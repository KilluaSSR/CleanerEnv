package killua.dev.cleaner.ui.history

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.overlay.OverlayController
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.SettingsAccent
import killua.dev.arsenal.material.templates.settings.SettingsSwitch
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition
import killua.dev.arsenal.navigation.Navigator
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.arsenal.navigation.generated.GeneratedRoutes
import killua.dev.cleaner.R
import killua.dev.cleaner.data.settings.AppSettings
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.FileHistoryRepository
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.model.FileEvent
import killua.dev.cleaner.domain.model.FileEventType
import killua.dev.cleaner.ui.app.AppRuleViewModel
import killua.dev.cleaner.ui.components.AppDetailScaffold
import killua.dev.cleaner.ui.components.AppHeader
import killua.dev.cleaner.ui.components.AppIcon
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.components.DetailScaffold
import killua.dev.cleaner.ui.components.EmptyView
import killua.dev.cleaner.ui.components.LoadingView
import killua.dev.cleaner.ui.components.Pill
import killua.dev.cleaner.ui.components.ServerRequiredView
import killua.dev.cleaner.ui.components.labelRes
import killua.dev.cleaner.ui.components.rememberAppLabel
import killua.dev.cleaner.ui.components.rememberFormattedTime
import killua.dev.cleaner.ui.components.rememberRelativeTime
import killua.dev.cleaner.ui.components.stateItem
import javax.inject.Inject

data class FileMonitorState(
    val daemonOnline: Boolean = false,
    val monitorEnabled: Boolean = false,
    val apps: DataState<List<FileEvent>> = DataState.Loading(),
) : UiState

sealed interface FileMonitorIntent : UiIntent {
    data class SetMonitor(val enabled: Boolean) : FileMonitorIntent
    data class OpenApp(val packageName: String) : FileMonitorIntent
    data object Clear : FileMonitorIntent
    data object ConfirmClear : FileMonitorIntent
    data object Back : FileMonitorIntent
}

sealed interface FileMonitorEffect : UiEffect

@HiltViewModel
class FileMonitorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @CurrentUser private val userId: Int,
    private val history: FileHistoryRepository,
    private val settings: SettingsStore,
    private val navigator: Navigator,
    private val overlay: OverlayController,
    controller: IsolationController,
) : BaseViewModel<FileMonitorState, FileMonitorIntent, FileMonitorEffect>(FileMonitorState()) {
    init {
        controller.observeConnected().launchInScope { setState { copy(daemonOnline = it) } }
        settings.flow(AppSettings.fileMonitor).launchInScope { setState { copy(monitorEnabled = it) } }
        history.observeLatestPerApp(userId).launchInScope { latest ->
            setState { copy(apps = if (latest.isEmpty()) DataState.Empty() else DataState.Success(latest)) }
        }
    }

    override suspend fun handleIntent(intent: FileMonitorIntent) {
        when (intent) {
            is FileMonitorIntent.SetMonitor -> settings.set(AppSettings.fileMonitor, intent.enabled)
            is FileMonitorIntent.OpenApp -> navigator.navigate(GeneratedRoutes.fileHistoryScreen(intent.packageName))
            FileMonitorIntent.Clear -> overlay.dialog(
                title = context.getString(R.string.history_clear_all_title),
                message = context.getString(R.string.history_clear_all_message),
                confirmLabel = context.getString(R.string.action_confirm),
                dismissLabel = context.getString(R.string.action_cancel),
                onConfirm = { dispatch(FileMonitorIntent.ConfirmClear) },
            )
            FileMonitorIntent.ConfirmClear -> history.clear(null, userId)
            FileMonitorIntent.Back -> navigator.navigateUp()
        }
    }
}

@NavDestination(route = "file_monitor")
@Composable
fun FileMonitorScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: FileMonitorViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val apps = state.apps

    DetailScaffold(
        title = stringResource(R.string.history_title_all),
        onBack = { viewModel.dispatch(FileMonitorIntent.Back) },
        actions = {
            if (state.daemonOnline && apps is DataState.Success) {
                IconButton(onClick = { viewModel.dispatch(FileMonitorIntent.Clear) }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "monitor") {
                SettingsSwitch(
                    title = stringResource(R.string.settings_file_monitor),
                    description = stringResource(R.string.settings_file_monitor_description),
                    leadingIcon = Icons.AutoMirrored.Rounded.ManageSearch,
                    leadingAccent = if (state.monitorEnabled) SettingsAccent.Secondary else SettingsAccent.Neutral,
                    checked = state.monitorEnabled,
                    onCheckedChange = { viewModel.dispatch(FileMonitorIntent.SetMonitor(it)) },
                    shape = MaterialTheme.shapes.large,
                    containerColor = CleanerSurfaces.row,
                )
            }
            item(key = "gap") { Box(Modifier.padding(top = Spacing.md)) }
            when {
                !state.daemonOnline -> stateItem { ServerRequiredView(it) }
                apps is DataState.Success -> itemsIndexed(apps.data, key = { _, event -> event.packageName }) { index, event ->
                    MonitoredAppRow(
                        latest = event,
                        shape = groupedItemShape(itemPosition(index, apps.data.size)),
                        modifier = Modifier.animateItem(),
                    ) { viewModel.dispatch(FileMonitorIntent.OpenApp(event.packageName)) }
                }
                apps is DataState.Loading -> stateItem { LoadingView(it) }
                !state.monitorEnabled -> stateItem {
                    EmptyView(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.history_monitor_off_title),
                        message = stringResource(R.string.history_monitor_off_message),
                        modifier = it,
                    )
                }
                else -> stateItem {
                    EmptyView(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.history_apps_empty),
                        message = stringResource(R.string.history_apps_empty_message),
                        modifier = it,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitoredAppRow(latest: FileEvent, shape: Shape, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = shape, color = CleanerSurfaces.row, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppIcon(latest.packageName, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                Text(rememberAppLabel(latest.packageName), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = stringResource(R.string.history_app_latest, stringResource(latest.type.labelRes()), latest.subPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = rememberRelativeTime(latest.occurredAtMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class HistoryState(
    val packageName: String,
    val daemonOnline: Boolean = false,
    val monitorEnabled: Boolean = false,
    val events: DataState<List<FileEvent>> = DataState.Loading(),
) : UiState

sealed interface HistoryIntent : UiIntent {
    data object Clear : HistoryIntent
    data object ConfirmClear : HistoryIntent
    data object EnableMonitor : HistoryIntent
    data object Back : HistoryIntent
}

sealed interface HistoryEffect : UiEffect

@HiltViewModel
class FileHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    @CurrentUser private val userId: Int,
    private val history: FileHistoryRepository,
    private val settings: SettingsStore,
    private val navigator: Navigator,
    private val overlay: OverlayController,
    controller: IsolationController,
) : BaseViewModel<HistoryState, HistoryIntent, HistoryEffect>(
    HistoryState(packageName = savedStateHandle.get<String>(AppRuleViewModel.ARG_PACKAGE).orEmpty()),
) {
    init {
        controller.observeConnected().launchInScope { setState { copy(daemonOnline = it) } }
        settings.flow(AppSettings.fileMonitor).launchInScope { setState { copy(monitorEnabled = it) } }
        history.observe(currentState.packageName, userId, LIMIT).launchInScope { events ->
            setState { copy(events = if (events.isEmpty()) DataState.Empty() else DataState.Success(events)) }
        }
    }

    override suspend fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            HistoryIntent.Clear -> overlay.dialog(
                title = context.getString(R.string.history_clear_title),
                message = context.getString(R.string.history_clear_message),
                confirmLabel = context.getString(R.string.action_confirm),
                dismissLabel = context.getString(R.string.action_cancel),
                onConfirm = { dispatch(HistoryIntent.ConfirmClear) },
            )
            HistoryIntent.ConfirmClear -> history.clear(currentState.packageName, userId)
            HistoryIntent.EnableMonitor -> settings.set(AppSettings.fileMonitor, true)
            HistoryIntent.Back -> navigator.navigateUp()
        }
    }

    private companion object {
        const val LIMIT = 500
    }
}

@NavDestination(route = "file_history/{packageName}")
@Composable
fun FileHistoryScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: FileHistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events = state.events

    AppDetailScaffold(
        onBack = { viewModel.dispatch(HistoryIntent.Back) },
        actions = {
            if (state.daemonOnline && events is DataState.Success) {
                IconButton(onClick = { viewModel.dispatch(HistoryIntent.Clear) }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "header") {
                AppHeader(
                    packageName = state.packageName,
                    label = rememberAppLabel(state.packageName),
                    modifier = Modifier.padding(bottom = Spacing.md),
                ) {
                    if (events is DataState.Success) {
                        Pill(
                            text = stringResource(R.string.history_record_count, events.data.size),
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
            when {
                !state.daemonOnline -> stateItem { ServerRequiredView(it) }
                events is DataState.Success -> itemsIndexed(events.data, key = { index, event -> "${event.occurredAtMs}-$index" }) { index, event ->
                    EventRow(event, groupedItemShape(itemPosition(index, events.data.size)))
                }
                events is DataState.Loading -> stateItem { LoadingView(it) }
                !state.monitorEnabled -> stateItem {
                    EmptyView(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.history_monitor_off_title),
                        message = stringResource(R.string.history_monitor_off_message),
                        actionLabel = stringResource(R.string.history_enable_monitor),
                        onAction = { viewModel.dispatch(HistoryIntent.EnableMonitor) },
                        modifier = it,
                    )
                }
                else -> stateItem { EmptyView(icon = Icons.Rounded.History, title = stringResource(R.string.history_empty), modifier = it) }
            }
        }
    }
}

@Composable
private fun EventRow(event: FileEvent, shape: Shape) {
    Surface(shape = shape, color = CleanerSurfaces.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(event.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(event.subPath, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    text = stringResource(R.string.history_item_summary, stringResource(event.type.labelRes()), rememberFormattedTime(event.occurredAtMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun FileEventType.icon(): ImageVector = when (this) {
    FileEventType.Create -> Icons.AutoMirrored.Rounded.NoteAdd
    FileEventType.Modify -> Icons.Rounded.Edit
    FileEventType.Delete -> Icons.Rounded.Delete
    FileEventType.MovedFrom, FileEventType.MovedTo -> Icons.AutoMirrored.Rounded.DriveFileMove
    FileEventType.CreateDir -> Icons.Rounded.CreateNewFolder
    FileEventType.DeleteDir -> Icons.Rounded.FolderDelete
    FileEventType.Unknown -> Icons.Rounded.QuestionMark
}
