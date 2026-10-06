package killua.dev.cleaner.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.components.loadingindicator.ArsenalLoadingIndicator
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.SettingsAccent
import killua.dev.arsenal.material.templates.settings.SettingsItem
import killua.dev.arsenal.update.UpdateDownloadState
import killua.dev.arsenal.update.UpdateState
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.model.ModuleState
import killua.dev.cleaner.domain.model.MountRecord
import killua.dev.cleaner.domain.model.RuntimeState
import killua.dev.cleaner.ui.components.GroupHeader
import killua.dev.cleaner.ui.components.Pill
import killua.dev.cleaner.ui.components.RowGroup
import killua.dev.cleaner.ui.components.RowSlot
import killua.dev.cleaner.ui.components.TabScaffold
import killua.dev.cleaner.ui.components.labelRes
import killua.dev.cleaner.ui.components.rememberAppLabel
import killua.dev.cleaner.ui.components.rememberFormattedTime
import killua.dev.cleaner.ui.components.rememberRelativeTime
import kotlin.math.roundToInt

@Composable
fun HomeTab() {
    val viewModel: HomeTabViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(HomeIntent.Refresh) }

    TabScaffold(
        title = stringResource(R.string.app_name),
        contentAfterTitle = {
            UpdatePill(
                version = state.appVersion,
                update = state.update,
                download = state.download,
                onClick = { viewModel.dispatch(HomeIntent.UpdateApp) },
            )
        },
    ) { padding ->
        HomeContent(state = state, onIntent = viewModel::dispatch, contentPadding = padding)
    }
}

@Composable
private fun UpdatePill(
    version: String,
    update: UpdateState,
    download: UpdateDownloadState,
    onClick: () -> Unit,
) {
    if (version.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val offering = update is UpdateState.Available || download !is UpdateDownloadState.Idle
    val container by animateColorAsState(
        if (offering) scheme.errorContainer else scheme.secondaryContainer,
        label = "updateContainer",
    )
    val content by animateColorAsState(
        if (offering) scheme.onErrorContainer else scheme.onSecondaryContainer,
        label = "updateContent",
    )
    val label = when {
        download is UpdateDownloadState.Downloading -> if (download.progress in 0f..1f) {
            stringResource(R.string.update_downloading, (download.progress * 100).roundToInt())
        } else {
            stringResource(R.string.update_downloading_unknown)
        }
        download is UpdateDownloadState.Downloaded -> stringResource(R.string.update_install)
        update is UpdateState.Available -> stringResource(R.string.update_available)
        else -> stringResource(R.string.app_version, version)
    }
    val working = update is UpdateState.Checking || download is UpdateDownloadState.Downloading
    Pill(
        text = label,
        modifier = Modifier.animateContentSize(),
        container = container,
        content = content,
        leading = {
            AnimatedVisibility(
                visible = working,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                ArsenalLoadingIndicator(
                    modifier = Modifier.padding(end = Spacing.xs).size(16.dp),
                    color = content,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun HomeContent(state: HomeState, onIntent: (HomeIntent) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.sm,
            bottom = Spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "status") { StatusCard(state.status, state.busy, onIntent) }
        item(key = "service") { ServiceGroup(state.runtime, state.busy, onIntent) }
        item(key = "monitor") { MonitorGroup(state.monitorEnabled, onIntent) }
        item(key = "watch") { WatchGroup(onIntent) }
        item(key = "mounts") { MountsGroup(state.mounts, onIntent) }
    }
}

private data class StatusVisual(
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
private fun ModuleStatus.visual(): StatusVisual {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        ModuleStatus.Checking -> StatusVisual(Icons.Rounded.HourglassTop, scheme.surfaceContainerHighest, scheme.onSurface)
        ModuleStatus.NotConnected -> StatusVisual(Icons.Rounded.Link, scheme.secondaryContainer, scheme.onSecondaryContainer)
        ModuleStatus.RootDenied -> StatusVisual(Icons.Rounded.GppBad, scheme.errorContainer, scheme.onErrorContainer)
        ModuleStatus.ModuleMissing -> StatusVisual(Icons.Rounded.Extension, scheme.errorContainer, scheme.onErrorContainer)
        ModuleStatus.ModulePending -> StatusVisual(Icons.Rounded.RestartAlt, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        is ModuleStatus.ModuleOutdated -> StatusVisual(Icons.Rounded.Upgrade, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ModuleStatus.ModuleDisabled -> StatusVisual(Icons.Rounded.Block, scheme.errorContainer, scheme.onErrorContainer)
        ModuleStatus.ModuleInactive -> StatusVisual(Icons.Rounded.ErrorOutline, scheme.errorContainer, scheme.onErrorContainer)
        is ModuleStatus.Active -> StatusVisual(Icons.Rounded.VerifiedUser, scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}

@Composable
private fun StatusCard(status: ModuleStatus, busy: Boolean, onIntent: (HomeIntent) -> Unit) {
    val visual = status.visual()
    val container by animateColorAsState(visual.container, label = "statusContainer")
    val content by animateColorAsState(visual.content, label = "statusContent")
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Crossfade(targetState = status == ModuleStatus.Checking || busy, label = "statusBusy") { loading ->
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (loading) {
                            ArsenalLoadingIndicator(modifier = Modifier.size(40.dp), color = content)
                        } else {
                            Crossfade(targetState = visual.icon, label = "statusIcon") { icon ->
                                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
                ChangingText(statusTitle(status), MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ), Modifier.weight(1f))
                if (status is ModuleStatus.Active && status.isolatedApps != null) {
                    IsolatedCount(status.isolatedApps)
                }
            }
            statusMessage(status)?.let { ChangingText(it, MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ) ) }
            StatusActions(status, busy, onIntent)
        }
    }
}

@Composable
private fun IsolatedCount(count: Int) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "isolatedCount",
        ) { value ->
            Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium)
        }
        Text(text = stringResource(R.string.status_isolated_apps_label), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ChangingText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "statusText",
    ) { value ->
        Text(text = value, style = style)
    }
}

@Composable
private fun statusTitle(status: ModuleStatus): String = stringResource(
    when (status) {
        ModuleStatus.Checking -> R.string.status_connecting_title
        ModuleStatus.NotConnected -> R.string.status_not_connected_title
        ModuleStatus.RootDenied -> R.string.status_root_denied_title
        ModuleStatus.ModuleMissing -> R.string.status_module_missing_title
        ModuleStatus.ModulePending -> R.string.status_module_pending_title
        is ModuleStatus.ModuleOutdated -> R.string.status_module_outdated_title
        ModuleStatus.ModuleDisabled -> R.string.status_module_disabled_title
        ModuleStatus.ModuleInactive -> R.string.status_module_inactive_title
        is ModuleStatus.Active -> R.string.status_active_title
    },
)

@Composable
private fun statusMessage(status: ModuleStatus): String? = when (status) {
    ModuleStatus.Checking -> null
    ModuleStatus.NotConnected -> stringResource(R.string.status_not_connected_message)
    ModuleStatus.RootDenied -> stringResource(R.string.status_root_denied_message)
    ModuleStatus.ModuleMissing -> stringResource(R.string.status_module_missing_message)
    ModuleStatus.ModulePending -> stringResource(R.string.status_module_pending_message)
    is ModuleStatus.ModuleOutdated -> stringResource(
        R.string.status_module_outdated_message,
        moduleVersion(status.installed),
        stringResource(R.string.module_version, status.bundled.versionName),
    )
    ModuleStatus.ModuleDisabled -> stringResource(R.string.status_module_disabled_message)
    ModuleStatus.ModuleInactive -> stringResource(R.string.status_module_inactive_message)
    is ModuleStatus.Active -> {
        val version = moduleVersion(status.module)
        if (status.isolatedApps != null) {
            stringResource(R.string.status_active_message, version)
        } else {
            stringResource(R.string.status_active_message_offline, version)
        }
    }
}

@Composable
private fun moduleVersion(module: ModuleState): String =
    module.versionName?.let { stringResource(R.string.module_version, it) }
        ?: stringResource(R.string.module_version_unknown)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusActions(status: ModuleStatus, busy: Boolean, onIntent: (HomeIntent) -> Unit) {
    val actions: List<Pair<Int, HomeIntent>> = when (status) {
        ModuleStatus.Checking -> emptyList()
        ModuleStatus.NotConnected -> listOf(R.string.action_connect to HomeIntent.Connect)
        ModuleStatus.RootDenied -> listOf(R.string.action_grant_root to HomeIntent.Connect)
        ModuleStatus.ModuleMissing -> listOf(R.string.action_install_module to HomeIntent.InstallModule)
        ModuleStatus.ModulePending -> listOf(R.string.action_reboot to HomeIntent.Reboot)
        is ModuleStatus.ModuleOutdated -> listOf(R.string.action_update_module to HomeIntent.InstallModule)
        ModuleStatus.ModuleDisabled -> listOf(R.string.action_enable_module to HomeIntent.InstallModule)
        ModuleStatus.ModuleInactive -> listOf(R.string.action_reboot to HomeIntent.Reboot)
        is ModuleStatus.Active -> emptyList()
    }
    AnimatedVisibility(visible = actions.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            actions.forEachIndexed { index, (label, intent) ->
                if (index == 0) {
                    Button(onClick = { onIntent(intent) }, enabled = !busy) { Text(stringResource(label)) }
                } else {
                    OutlinedButton(onClick = { onIntent(intent) }, enabled = !busy) { Text(stringResource(label)) }
                }
            }
        }
    }
}

@Composable
private fun ServiceGroup(runtime: RuntimeState?, busy: Boolean, onIntent: (HomeIntent) -> Unit) {
    val live = runtime as? RuntimeState.Connected
    val rows = buildList<@Composable (RowSlot) -> Unit> {
        add { slot ->
            SettingsItem(
                title = stringResource(if (live != null) R.string.service_running else R.string.service_stopped),
                description = live?.let {
                    stringResource(R.string.service_details, it.serverPid, it.sdkInt, rememberFormattedTime(it.startedAtMillis))
                },
                leadingIcon = Icons.Rounded.Dns,
                leadingAccent = if (live != null) SettingsAccent.Primary else SettingsAccent.Neutral,
                shape = slot.shape,
                containerColor = slot.color,
                trailing = {
                    if (live != null) {
                        OutlinedButton(onClick = { onIntent(HomeIntent.StopServer) }, enabled = !busy) {
                            Text(stringResource(R.string.action_stop_service))
                        }
                    } else {
                        FilledTonalButton(onClick = { onIntent(HomeIntent.Connect) }, enabled = !busy) {
                            Text(stringResource(R.string.action_start_service))
                        }
                    }
                },
            )
        }
        if (live != null) {
            add { slot ->
                SettingsItem(
                    title = stringResource(R.string.service_backend),
                    value = stringResource(live.backend.labelRes()),
                    leadingIcon = Icons.Rounded.Storage,
                    leadingAccent = SettingsAccent.Tertiary,
                    shape = slot.shape,
                    containerColor = slot.color,
                )
            }
        }
    }
    RowGroup(rows = rows, title = stringResource(R.string.home_section_service))
}

@Composable
private fun MonitorGroup(monitorEnabled: Boolean, onIntent: (HomeIntent) -> Unit) {
    RowGroup(
        title = stringResource(R.string.home_section_monitor),
        rows = listOf { slot ->
            SettingsItem(
                title = stringResource(R.string.home_monitor_title),
                value = stringResource(if (monitorEnabled) R.string.state_on else R.string.state_off),
                description = stringResource(R.string.home_monitor_description),
                leadingIcon = Icons.AutoMirrored.Rounded.ManageSearch,
                leadingAccent = SettingsAccent.Secondary,
                shape = slot.shape,
                containerColor = slot.color,
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                onClick = { onIntent(HomeIntent.OpenMonitor) },
            )
        },
    )
}

@Composable
private fun WatchGroup(
    onIntent: (HomeIntent) -> Unit,
) {
    Column {
        GroupHeader(title = stringResource(R.string.home_section_watch))
        val rows: List<@Composable (RowSlot) -> Unit> = listOf { slot ->
            SettingsItem(
                title = stringResource(R.string.watch_manage),
                description = stringResource(R.string.watch_manage_desc),
                leadingIcon = Icons.Rounded.Visibility,
                leadingAccent = SettingsAccent.Secondary,
                shape = slot.shape,
                containerColor = slot.color,
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                onClick = { onIntent(HomeIntent.ManageWatches) },
            )
        }
        RowGroup(rows = rows)
    }
}

@Composable
private fun MountsGroup(mounts: List<MountRecord>, onIntent: (HomeIntent) -> Unit) {
    Column {
        val failures = mounts.count { !it.success }
        GroupHeader(title = stringResource(R.string.home_section_mounts))
        val latest = mounts.maxByOrNull { it.occurredAtMs }
        RowGroup(
            rows = listOf { slot ->
                SettingsItem(
                    title = stringResource(R.string.mounts_title),
                    description = latest?.let {
                        stringResource(R.string.mounts_latest, rememberAppLabel(it.packageName), rememberRelativeTime(it.occurredAtMs))
                    } ?: stringResource(R.string.mounts_empty),
                    leadingIcon = Icons.Rounded.Layers,
                    leadingAccent = if (failures > 0) SettingsAccent.Error else SettingsAccent.Primary,
                    shape = slot.shape,
                    containerColor = slot.color,
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    onClick = { onIntent(HomeIntent.OpenMounts) },
                )
            },
        )
    }
}
