package killua.dev.cleaner.ui.watch

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.overlay.OverlayController
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.navigation.Navigator
import killua.dev.arsenal.navigation.generated.GeneratedRoutes
import killua.dev.cleaner.R
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.WatchRepository
import killua.dev.cleaner.domain.model.WatchHit
import killua.dev.cleaner.domain.model.WatchKind
import killua.dev.cleaner.domain.model.WatchRule
import killua.dev.cleaner.ipc.WatchCodec
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

const val NEW_WATCH = "new"

internal const val ARG_RULE_ID = "ruleId"

data class WatchEditorState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val directory: String? = null,
    val kind: WatchKind = WatchKind.Folder,
    val name: String = "",
    val recursive: Boolean = false,
    val enabled: Boolean = true,
    val directoryError: Boolean = false,
    val nameError: Boolean = false,
    val saving: Boolean = false,
) : UiState

sealed interface WatchEditorIntent : UiIntent {
    data class SetDirectory(val directory: String) : WatchEditorIntent
    data class SetKind(val kind: WatchKind) : WatchEditorIntent
    data class SetRecursive(val recursive: Boolean) : WatchEditorIntent
    data class SetEnabled(val enabled: Boolean) : WatchEditorIntent

    data class Save(val name: String) : WatchEditorIntent
    data object Back : WatchEditorIntent
}

sealed interface WatchEditorEffect : UiEffect

@HiltViewModel
class WatchEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    @CurrentUser private val userId: Int,
    private val watches: WatchRepository,
    private val navigator: Navigator,
    private val overlay: OverlayController,
) : BaseViewModel<WatchEditorState, WatchEditorIntent, WatchEditorEffect>(
    WatchEditorState(isNew = savedStateHandle.get<String>(ARG_RULE_ID) == NEW_WATCH),
) {
    private val ruleId: String = savedStateHandle.get<String>(ARG_RULE_ID)
        ?.takeIf { it != NEW_WATCH }
        ?: UUID.randomUUID().toString()
    private var existing: WatchRule? = null

    init {
        viewModelScope.launch {
            existing = if (currentState.isNew) null else watches.get(ruleId)
            setState {
                existing?.let { rule ->
                    copy(
                        loaded = true,
                        directory = rule.directory,
                        kind = rule.kind,
                        name = rule.name,
                        recursive = rule.recursive,
                        enabled = rule.enabled,
                    )
                } ?: copy(loaded = true)
            }
        }
    }

    override suspend fun handleIntent(intent: WatchEditorIntent) {
        when (intent) {
            is WatchEditorIntent.SetDirectory -> setState { copy(directory = intent.directory, directoryError = false) }
            is WatchEditorIntent.SetKind -> setState { copy(kind = intent.kind, nameError = false) }
            is WatchEditorIntent.SetRecursive -> setState { copy(recursive = intent.recursive) }
            is WatchEditorIntent.SetEnabled -> setState { copy(enabled = intent.enabled) }
            is WatchEditorIntent.Save -> save(intent.name.trim())
            WatchEditorIntent.Back -> navigator.navigateUp()
        }
    }

    private suspend fun save(name: String) {
        val directory = currentState.directory?.let(WatchCodec::normalizeDirectory)
        val kind = currentState.kind
        val nameError = kind != WatchKind.Everything && !WatchCodec.isValidName(name)
        setState { copy(directoryError = directory == null, nameError = nameError) }
        if (directory == null || nameError) return

        setState { copy(saving = true) }
        val published = watches.save(
            WatchRule(
                id = ruleId,
                userId = userId,
                directory = directory,
                kind = kind,
                name = if (kind == WatchKind.Everything) "" else name,
                recursive = currentState.recursive,
                enabled = currentState.enabled,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        setState { copy(saving = false) }
        if (!published) overlay.snackbar(context.getString(R.string.watch_saved_not_applied))
        navigator.navigateUp()
    }
}

data class WatchLogState(
    val ruleId: String,
    val rule: WatchRule? = null,
    val daemonOnline: Boolean = false,
    val pending: Boolean = false,
    val events: DataState<List<WatchHit>> = DataState.Loading(),
) : UiState

sealed interface WatchLogIntent : UiIntent {
    data class SetEnabled(val enabled: Boolean) : WatchLogIntent
    data object Edit : WatchLogIntent
    data object Clear : WatchLogIntent
    data object ConfirmClear : WatchLogIntent
    data object Delete : WatchLogIntent
    data object ConfirmDelete : WatchLogIntent
    data object Back : WatchLogIntent
}

sealed interface WatchLogEffect : UiEffect

@HiltViewModel
class WatchLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    @CurrentUser private val userId: Int,
    private val watches: WatchRepository,
    private val navigator: Navigator,
    private val overlay: OverlayController,
    controller: IsolationController,
) : BaseViewModel<WatchLogState, WatchLogIntent, WatchLogEffect>(
    WatchLogState(ruleId = savedStateHandle.get<String>(ARG_RULE_ID).orEmpty()),
) {
    init {
        val id = currentState.ruleId
        watches.observeRules(userId).map { rules -> rules.firstOrNull { it.id == id } }.launchInScope { rule ->
            setState { copy(rule = rule) }
            refreshPending()
        }
        controller.observeConnected().launchInScope { online ->
            setState { copy(daemonOnline = online) }
            refreshPending()
        }
        watches.observeEvents(id, userId, LIMIT).launchInScope { events ->
            setState { copy(events = if (events.isEmpty()) DataState.Empty() else DataState.Success(events)) }
            refreshPending()
        }
    }

    override suspend fun handleIntent(intent: WatchLogIntent) {
        when (intent) {
            is WatchLogIntent.SetEnabled -> currentState.rule?.let { rule ->
                if (!watches.save(rule.copy(enabled = intent.enabled))) {
                    overlay.snackbar(context.getString(R.string.watch_saved_not_applied))
                }
            }
            WatchLogIntent.Edit -> navigator.navigate(GeneratedRoutes.watchEditorScreen(currentState.ruleId))
            WatchLogIntent.Clear -> overlay.dialog(
                title = context.getString(R.string.watch_clear_title),
                message = context.getString(R.string.watch_clear_message),
                confirmLabel = context.getString(R.string.action_confirm),
                dismissLabel = context.getString(R.string.action_cancel),
                onConfirm = { dispatch(WatchLogIntent.ConfirmClear) },
            )
            WatchLogIntent.ConfirmClear -> watches.clearEvents(currentState.ruleId, userId)
            WatchLogIntent.Delete -> overlay.dialog(
                title = context.getString(R.string.watch_delete_title),
                message = context.getString(R.string.watch_delete_message),
                confirmLabel = context.getString(R.string.action_confirm),
                dismissLabel = context.getString(R.string.action_cancel),
                onConfirm = { dispatch(WatchLogIntent.ConfirmDelete) },
            )
            WatchLogIntent.ConfirmDelete -> currentState.rule?.let { rule ->
                watches.delete(rule)
                navigator.navigateUp()
            }
            WatchLogIntent.Back -> navigator.navigateUp()
        }
    }

    private suspend fun refreshPending() {
        val pending = currentState.daemonOnline && currentState.ruleId in watches.pending(userId)
        setState { copy(pending = pending) }
    }

    private companion object {
        const val LIMIT = 500
    }
}

data class WatchRulesState(
    val rules: List<WatchRule> = emptyList(),
    val latest: Map<String, WatchHit> = emptyMap(),
    val pending: Set<String> = emptySet(),
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
) : UiState

sealed interface WatchRulesIntent : UiIntent {
    data class Open(val id: String) : WatchRulesIntent
    data object StartSelection : WatchRulesIntent
    data class ToggleSelected(val id: String) : WatchRulesIntent
    data object SelectAll : WatchRulesIntent
    data object ClearSelection : WatchRulesIntent
    data class SetSelectedEnabled(val enabled: Boolean) : WatchRulesIntent
    data object DeleteSelected : WatchRulesIntent
    data object ConfirmDeleteSelected : WatchRulesIntent
    data object Add : WatchRulesIntent
    data object Refresh : WatchRulesIntent
    data object Back : WatchRulesIntent
}

sealed interface WatchRulesEffect : UiEffect

@HiltViewModel
class WatchRulesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @CurrentUser private val userId: Int,
    private val watches: WatchRepository,
    private val navigator: Navigator,
    private val overlay: OverlayController,
) : BaseViewModel<WatchRulesState, WatchRulesIntent, WatchRulesEffect>(WatchRulesState()) {

    init {
        watches.observeRules(userId).launchInScope { rules ->
            val ids = rules.mapTo(HashSet()) { it.id }
            setState { copy(rules = rules, selected = selected intersect ids) }
            refreshStatus()
        }
    }

    override suspend fun handleIntent(intent: WatchRulesIntent) {
        when (intent) {
            is WatchRulesIntent.Open -> navigator.navigate(GeneratedRoutes.watchLogScreen(intent.id))
            WatchRulesIntent.StartSelection -> setState { copy(selecting = true) }
            is WatchRulesIntent.ToggleSelected -> setState {
                copy(selecting = true, selected = if (intent.id in selected) selected - intent.id else selected + intent.id)
            }
            WatchRulesIntent.SelectAll -> setState {
                val all = rules.mapTo(HashSet()) { it.id }
                copy(selected = if (selected == all) emptySet() else all)
            }
            WatchRulesIntent.ClearSelection -> endSelection()
            is WatchRulesIntent.SetSelectedEnabled -> {
                val changed = selectedRules().filter { it.enabled != intent.enabled }.map { it.copy(enabled = intent.enabled) }
                val published = watches.saveAll(changed)
                endSelection()
                overlay.snackbar(
                    message = if (published) {
                        context.getString(R.string.watch_batch_updated, changed.size)
                    } else {
                        context.getString(R.string.watch_saved_not_applied)
                    },
                    duration = SnackbarDuration.Short,
                )
            }
            WatchRulesIntent.DeleteSelected -> overlay.dialog(
                title = context.getString(R.string.watch_batch_delete_title, currentState.selected.size),
                message = context.getString(R.string.watch_delete_message),
                confirmLabel = context.getString(R.string.action_confirm),
                dismissLabel = context.getString(R.string.action_cancel),
                onConfirm = { dispatch(WatchRulesIntent.ConfirmDeleteSelected) },
            )
            WatchRulesIntent.ConfirmDeleteSelected -> {
                val doomed = selectedRules()
                watches.deleteAll(doomed)
                endSelection()
                overlay.snackbar(context.getString(R.string.watch_batch_deleted, doomed.size), duration = SnackbarDuration.Short)
            }
            WatchRulesIntent.Add -> navigator.navigate(GeneratedRoutes.watchEditorScreen(NEW_WATCH))
            WatchRulesIntent.Refresh -> refreshStatus()
            WatchRulesIntent.Back -> navigator.navigateUp()
        }
    }

    private fun endSelection() = setState { copy(selecting = false, selected = emptySet()) }

    private fun selectedRules(): List<WatchRule> = currentState.rules.filter { it.id in currentState.selected }

    private suspend fun refreshStatus() {
        val latest = watches.latestEvents(userId)
        val pending = watches.pending(userId)
        setState { copy(latest = latest, pending = pending) }
    }
}
