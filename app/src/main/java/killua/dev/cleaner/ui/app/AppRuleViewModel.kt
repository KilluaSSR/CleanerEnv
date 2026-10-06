package killua.dev.cleaner.ui.app

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.overlay.OverlayController
import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.arsenal.navigation.Navigator
import killua.dev.arsenal.navigation.generated.GeneratedRoutes
import killua.dev.cleaner.R
import killua.dev.cleaner.data.settings.AppSettings
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.AppCatalog
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.RuleRepository
import killua.dev.cleaner.domain.model.AccessibleFolder
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppRule
import killua.dev.cleaner.domain.model.ExportRule
import killua.dev.cleaner.domain.model.SharedFolder
import killua.dev.cleaner.domain.model.StorageLocation
import killua.dev.cleaner.ipc.RuleFileCodec
import javax.inject.Inject

data class AppRuleState(
    val packageName: String,
    val app: AppEntry? = null,
    val rule: AppRule,
    val defaultLocation: StorageLocation = StorageLocation.DataFolder,
    val daemonOnline: Boolean = false,
    val isolatedApps: List<AppEntry> = emptyList(),
) : UiState

enum class AppPage { AccessibleFolders, SharedFolders, Export, Gallery, IsolatedStorage, History, Analysis, Help }

sealed interface AppRuleIntent : UiIntent {
    data class SetEnabled(val enabled: Boolean) : AppRuleIntent
    data class SetHandleProviders(val enabled: Boolean) : AppRuleIntent
    data class SetAllowGallery(val enabled: Boolean) : AppRuleIntent
    data class SetLocation(val location: StorageLocation?) : AppRuleIntent
    data class AddFolder(val path: String) : AppRuleIntent
    data class RemoveFolder(val folder: AccessibleFolder) : AppRuleIntent
    data class AddExport(val viewPath: String, val realPath: String) : AppRuleIntent
    data class RemoveExport(val rule: ExportRule) : AppRuleIntent
    data class AddShared(val packageName: String, val path: String) : AppRuleIntent
    data class RemoveShared(val folder: SharedFolder) : AppRuleIntent
    data object Restart : AppRuleIntent
    data object Reset : AppRuleIntent
    data object ConfirmReset : AppRuleIntent
    data object OpenAppInfo : AppRuleIntent
    data class Open(val page: AppPage) : AppRuleIntent
    data object Back : AppRuleIntent
}

sealed interface AppRuleEffect : UiEffect {
    data class ShowAppInfo(val packageName: String) : AppRuleEffect
}

@HiltViewModel
class AppRuleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    @CurrentUser private val userId: Int,
    private val rules: RuleRepository,
    private val catalog: AppCatalog,
    private val controller: IsolationController,
    private val navigator: Navigator,
    private val overlay: OverlayController,
    settings: SettingsStore,
) : BaseViewModel<AppRuleState, AppRuleIntent, AppRuleEffect>(
    initialState(savedStateHandle.get<String>(ARG_PACKAGE).orEmpty(), userId),
) {
    private val packageName: String = currentState.packageName

    init {
        rules.observeRule(packageName, userId).launchInScope { rule -> setState { copy(rule = rule) } }
        catalog.observeApps().launchInScope { apps ->
            setState {
                copy(
                    app = apps.firstOrNull { it.packageName == packageName },
                    isolatedApps = apps.filter { it.isolationEnabled && it.packageName != packageName },
                )
            }
        }
        settings.flow(AppSettings.defaultLocation).launchInScope { setState { copy(defaultLocation = it) } }
        controller.observeConnected().launchInScope { setState { copy(daemonOnline = it) } }
    }

    override suspend fun handleIntent(intent: AppRuleIntent) {
        when (intent) {
            is AppRuleIntent.SetEnabled -> edit(alwaysRestart = true) { copy(enabled = intent.enabled) }
            is AppRuleIntent.SetHandleProviders -> edit { copy(handleProviders = intent.enabled) }
            is AppRuleIntent.SetAllowGallery -> edit { copy(allowGallery = intent.enabled) }
            is AppRuleIntent.SetLocation -> edit { copy(location = intent.location) }
            is AppRuleIntent.AddFolder -> {
                val path = validPath(intent.path) ?: return
                edit {
                    if (accessibleFolders.any { it.subPath == path }) this
                    else copy(accessibleFolders = accessibleFolders + AccessibleFolder(path))
                }
            }
            is AppRuleIntent.RemoveFolder -> edit { copy(accessibleFolders = accessibleFolders - intent.folder) }
            is AppRuleIntent.AddExport -> {
                val view = validPath(intent.viewPath) ?: return
                val real = validPath(intent.realPath) ?: return
                edit { copy(exports = exports.filterNot { it.viewPath == view } + ExportRule(view, real)) }
            }
            is AppRuleIntent.RemoveExport -> edit { copy(exports = exports - intent.rule) }
            is AppRuleIntent.AddShared -> {
                val path = validPath(intent.path) ?: return
                val folder = SharedFolder(intent.packageName, path)
                edit { if (folder in sharedFolders) this else copy(sharedFolders = sharedFolders + folder) }
            }
            is AppRuleIntent.RemoveShared -> edit { copy(sharedFolders = sharedFolders - intent.folder) }
            AppRuleIntent.Restart -> restart()
            AppRuleIntent.Reset -> overlay.dialog(
                title = context.getString(R.string.config_reset_title),
                message = context.getString(R.string.config_reset_message),
                confirmLabel = context.getString(R.string.action_confirm),
                dismissLabel = context.getString(R.string.action_cancel),
                onConfirm = { dispatch(AppRuleIntent.ConfirmReset) },
            )
            AppRuleIntent.ConfirmReset -> edit(alwaysRestart = true) { AppRule.empty(packageName, userId) }
            AppRuleIntent.OpenAppInfo -> sendEffect(AppRuleEffect.ShowAppInfo(packageName))
            is AppRuleIntent.Open -> navigator.navigate(intent.page.route(packageName))
            AppRuleIntent.Back -> navigator.navigateUp()
        }
    }

    private suspend fun edit(alwaysRestart: Boolean = false, transform: AppRule.() -> AppRule) {
        val current = rules.get(packageName, userId)
        val updated = current.transform()
        if (updated == current) return
        val applied = rules.save(updated)
        when {
            !applied -> overlay.snackbar(context.getString(R.string.config_saved_not_applied))
            alwaysRestart || updated.enabled -> suggestRestart()
        }
    }

    private fun validPath(raw: String): String? {
        val path = RuleFileCodec.normalizeRelativePath(raw.trim())
        if (path == null) overlay.snackbar(context.getString(R.string.path_invalid))
        return path
    }

    private fun suggestRestart() {
        val label = currentState.app?.label ?: packageName
        overlay.snackbar(
            message = context.getString(R.string.config_saved_restart, label),
            actionLabel = context.getString(R.string.config_menu_restart),
            duration = SnackbarDuration.Short,
            onAction = { dispatch(AppRuleIntent.Restart) },
        )
    }

    private suspend fun restart() {
        if (!controller.restartApp(packageName, userId)) {
            overlay.snackbar(context.getString(R.string.config_restart_failed))
        }
    }

    companion object {
        const val ARG_PACKAGE = "packageName"

        private fun initialState(packageName: String, userId: Int) =
            AppRuleState(packageName = packageName, rule = AppRule.empty(packageName, userId))
    }
}

internal fun AppPage.route(packageName: String): String = when (this) {
    AppPage.AccessibleFolders -> GeneratedRoutes.accessibleFoldersScreen(packageName)
    AppPage.SharedFolders -> GeneratedRoutes.sharedFoldersScreen(packageName)
    AppPage.Export -> GeneratedRoutes.exportRulesScreen(packageName)
    AppPage.Gallery -> GeneratedRoutes.galleryScreen(packageName)
    AppPage.IsolatedStorage -> GeneratedRoutes.isolatedBrowserScreen(packageName)
    AppPage.History -> GeneratedRoutes.fileHistoryScreen(packageName)
    AppPage.Analysis -> GeneratedRoutes.folderAnalysisScreen(packageName)
    AppPage.Help -> GeneratedRoutes.HELP_SCREEN
}
