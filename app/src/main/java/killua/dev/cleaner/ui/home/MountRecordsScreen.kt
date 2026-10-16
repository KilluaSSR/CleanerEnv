package killua.dev.cleaner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition
import killua.dev.arsenal.navigation.Navigator
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.arsenal.navigation.generated.GeneratedRoutes
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.FileHistoryRepository
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.model.MountRecord
import killua.dev.cleaner.ui.components.AppIcon
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.components.DetailScaffold
import killua.dev.arsenal.material.templates.basics.EmptyView
import killua.dev.cleaner.ui.components.GroupHeader
import killua.dev.arsenal.material.templates.basics.LoadingView
import killua.dev.cleaner.ui.components.Pill
import killua.dev.cleaner.ui.components.ServerRequiredView
import killua.dev.cleaner.ui.components.rememberAppLabel
import killua.dev.cleaner.ui.components.rememberFormattedTime
import killua.dev.cleaner.ui.components.rememberRelativeTime
import javax.inject.Inject

data class MountRecordsState(
    val daemonOnline: Boolean = false,
    val records: DataState<List<MountRecord>> = DataState.Loading(),
) : UiState

sealed interface MountRecordsIntent : UiIntent {
    data object Refresh : MountRecordsIntent
    data class OpenApp(val packageName: String) : MountRecordsIntent
    data object Back : MountRecordsIntent
}

sealed interface MountRecordsEffect : UiEffect

@HiltViewModel
class MountRecordsViewModel @Inject constructor(
    private val history: FileHistoryRepository,
    private val navigator: Navigator,
    controller: IsolationController,
) : BaseViewModel<MountRecordsState, MountRecordsIntent, MountRecordsEffect>(MountRecordsState()) {

    init {
        controller.observeConnected().launchInScope { online ->
            setState { copy(daemonOnline = online) }
            load()
        }
    }

    override suspend fun handleIntent(intent: MountRecordsIntent) {
        when (intent) {
            MountRecordsIntent.Refresh -> load()
            is MountRecordsIntent.OpenApp -> navigator.navigate(GeneratedRoutes.appConfigScreen(intent.packageName))
            MountRecordsIntent.Back -> navigator.navigateUp()
        }
    }

    private suspend fun load() {
        if (!currentState.daemonOnline) return
        val records = history.mountRecords(LIMIT).sortedByDescending { it.occurredAtMs }
        setState { copy(records = if (records.isEmpty()) DataState.Empty() else DataState.Success(records)) }
    }

    private companion object {
        const val LIMIT = 500
    }
}

@NavDestination(route = "mount_records")
@Composable
fun MountRecordsScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: MountRecordsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(MountRecordsIntent.Refresh) }

    DetailScaffold(
        title = stringResource(R.string.home_section_mounts),
        onBack = { viewModel.dispatch(MountRecordsIntent.Back) },
    ) { padding ->
        val records = state.records
        if (state.daemonOnline && records is DataState.Success) {
            MountList(records.data, padding, onOpen = { viewModel.dispatch(MountRecordsIntent.OpenApp(it)) })
            return@DetailScaffold
        }
        Box(Modifier.padding(padding)) {
            when {
                !state.daemonOnline -> ServerRequiredView()
                records is DataState.Loading -> LoadingView()
                else -> EmptyView(icon = Icons.Rounded.Layers, title = stringResource(R.string.mounts_empty))
            }
        }
    }
}

@Composable
private fun MountList(records: List<MountRecord>, padding: PaddingValues, onOpen: (String) -> Unit) {
    val failures = records.count { !it.success }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(key = "summary") {
            GroupHeader(title = stringResource(R.string.mounts_count, records.size)) {
                MountPills(succeeded = records.size - failures, failed = failures)
            }
        }
        itemsIndexed(records, key = { index, record -> "${record.occurredAtMs}-${record.pid}-$index" }) { index, record ->
            MountRow(record, groupedItemShape(itemPosition(index, records.size))) { onOpen(record.packageName) }
        }
    }
}

@Composable
internal fun MountPills(succeeded: Int, failed: Int) {
    Pill(
        text = stringResource(R.string.mounts_succeeded, succeeded),
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    if (failed > 0) {
        Pill(
            text = stringResource(R.string.mounts_failed, failed),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun MountRow(record: MountRecord, shape: Shape, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = shape, color = CleanerSurfaces.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            MountAppIcon(record)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                Text(rememberAppLabel(record.packageName), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = if (record.success) {
                        stringResource(R.string.mount_success, record.pid)
                    } else {
                        stringResource(R.string.mount_failed, record.message)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
                Text(
                    text = rememberFormattedTime(record.occurredAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = rememberRelativeTime(record.occurredAtMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MountAppIcon(record: MountRecord) {
    val scheme = MaterialTheme.colorScheme
    Box {
        AppIcon(record.packageName, size = 40.dp)
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = Spacing.xs, y = Spacing.xs).size(18.dp),
            shape = CircleShape,
            color = if (record.success) scheme.primary else scheme.error,
            contentColor = if (record.success) scheme.onPrimary else scheme.onError,
        ) {
            Icon(
                imageVector = if (record.success) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.padding(2.dp),
            )
        }
    }
}
