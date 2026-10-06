package killua.dev.cleaner.ui.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import killua.dev.arsenal.core.mvvm.CollectEffect
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.SettingsAccent
import killua.dev.arsenal.material.templates.settings.SettingsItem
import killua.dev.arsenal.material.templates.settings.SettingsSwitch
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppRule
import killua.dev.cleaner.domain.model.StorageAccess
import killua.dev.cleaner.ui.components.AppDetailScaffold
import killua.dev.cleaner.ui.components.AppHeader
import killua.dev.cleaner.ui.components.Pill
import killua.dev.cleaner.ui.components.RowGroup
import killua.dev.cleaner.ui.components.RowSlot
import killua.dev.cleaner.ui.components.labelRes
import killua.dev.cleaner.ui.components.locationLabel
import killua.dev.cleaner.ui.components.recommendationRes

@OptIn(ExperimentalMaterial3Api::class)
@NavDestination(route = "app_config/{packageName}")
@Composable
fun AppConfigScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: AppRuleViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLocationSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is AppRuleEffect.ShowAppInfo -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", effect.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    AppDetailScaffold(
        onBack = { viewModel.dispatch(AppRuleIntent.Back) },
        actions = { OverflowMenu(onIntent = viewModel::dispatch) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, bottom = FAB_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "header") { ConfigHeader(state.app, state.packageName) }
            item(key = "enable") {
                EnableRow(state.rule.enabled) { viewModel.dispatch(AppRuleIntent.SetEnabled(it)) }
            }
            state.app?.let { app -> item(key = "recommend") { RecommendationCard(app.storageAccess) } }
            item(key = "rules") { RuleRows(state, viewModel::dispatch) { showLocationSheet = true } }
            item(key = "viewers") { ViewerRows(state.rule.enabled, viewModel::dispatch) }
        }
    }

    if (showLocationSheet) {
        LocationSheet(
            current = state.rule.location,
            default = state.defaultLocation,
            onSelect = {
                viewModel.dispatch(AppRuleIntent.SetLocation(it))
                showLocationSheet = false
            },
            onDismiss = { showLocationSheet = false },
        )
    }
}

private val FAB_CLEARANCE = 96.dp

@Composable
private fun OverflowMenu(onIntent: (AppRuleIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                R.string.config_menu_restart to AppRuleIntent.Restart,
                R.string.config_menu_app_info to AppRuleIntent.OpenAppInfo,
                R.string.config_menu_reset to AppRuleIntent.Reset,
            ).forEach { (label, intent) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        onIntent(intent)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigHeader(app: AppEntry?, packageName: String) {
    AppHeader(packageName = packageName, label = app?.label ?: packageName) {
        if (app != null) {
            Text(
                text = stringResource(R.string.config_version, app.versionName, app.versionCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Pill(
                text = stringResource(R.string.config_api_badge, app.targetSdk, stringResource(app.storageAccess.labelRes())),
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun EnableRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    RowGroup(
        rows = listOf { slot ->
            SettingsSwitch(
                title = stringResource(R.string.config_enable_isolation),
                description = stringResource(R.string.config_enable_isolation_description),
                checked = enabled,
                onCheckedChange = onChange,
                leadingIcon = Icons.Rounded.Shield,
                leadingAccent = if (enabled) SettingsAccent.Primary else SettingsAccent.Neutral,
                shape = slot.shape,
                containerColor = slot.color,
            )
        },
    )
}

private enum class Advice { NotNeeded, Optional, Recommended, Strong }

private fun StorageAccess.advice(): Advice = when (this) {
    StorageAccess.None -> Advice.NotNeeded
    StorageAccess.Media, StorageAccess.ReadOnly -> Advice.Optional
    StorageAccess.ReadWrite -> Advice.Recommended
    StorageAccess.AllFiles -> Advice.Strong
}

private data class AdviceVisual(val icon: ImageVector, val title: Int, val container: Color, val content: Color)

@Composable
private fun Advice.visual(): AdviceVisual {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        Advice.NotNeeded -> AdviceVisual(Icons.Rounded.CheckCircle, R.string.recommend_title_not_needed, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        Advice.Optional -> AdviceVisual(Icons.Rounded.TipsAndUpdates, R.string.recommend_title_optional, scheme.secondaryContainer, scheme.onSecondaryContainer)
        Advice.Recommended -> AdviceVisual(Icons.Rounded.Lightbulb, R.string.recommend_title_recommended, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Advice.Strong -> AdviceVisual(Icons.Rounded.GppMaybe, R.string.recommend_title_strong, scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
private fun RecommendationCard(access: StorageAccess) {
    val visual = access.advice().visual()
    val container by animateColorAsState(visual.container, label = "adviceContainer")
    val content by animateColorAsState(visual.content, label = "adviceContent")
    Surface(shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(visual.icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(visual.title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(access.recommendationRes()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
}

@Composable
private fun folderSummary(rule: AppRule): String {
    val count = rule.accessibleFolders.size
    val base = if (count == 0) stringResource(R.string.folder_count_none) else pluralStringResource(R.plurals.folder_count, count, count)
    return if (rule.allowGallery) base + stringResource(R.string.gallery_allowed_suffix) else base
}

@Composable
private fun RuleRows(state: AppRuleState, onIntent: (AppRuleIntent) -> Unit, onPickLocation: () -> Unit) {
    val rule = state.rule
    val enabled = rule.enabled
    RowGroup(
        rows = listOf(
            { slot ->
                NavigationRow(
                    slot = slot,
                    title = stringResource(R.string.config_accessible_folders),
                    value = folderSummary(rule),
                    icon = Icons.Rounded.Folder,
                    accent = SettingsAccent.Primary,
                    enabled = enabled,
                    onClick = { onIntent(AppRuleIntent.Open(AppPage.AccessibleFolders)) },
                )
            },
            { slot ->
                NavigationRow(
                    slot = slot,
                    title = stringResource(R.string.config_shared_folders),
                    value = if (rule.sharedFolders.isEmpty()) {
                        null
                    } else {
                        pluralStringResource(R.plurals.folder_count, rule.sharedFolders.size, rule.sharedFolders.size)
                    },
                    icon = Icons.Rounded.FolderShared,
                    accent = SettingsAccent.Tertiary,
                    enabled = enabled,
                    onClick = { onIntent(AppRuleIntent.Open(AppPage.SharedFolders)) },
                )
            },
            { slot ->
                NavigationRow(
                    slot = slot,
                    title = stringResource(R.string.config_export),
                    value = stringResource(R.string.config_export_count, rule.exportRuleCount),
                    icon = Icons.Rounded.IosShare,
                    accent = SettingsAccent.Secondary,
                    enabled = enabled,
                    onClick = { onIntent(AppRuleIntent.Open(AppPage.Export)) },
                )
            },
            { slot ->
                SettingsSwitch(
                    title = stringResource(R.string.config_handle_providers),
                    description = stringResource(R.string.config_handle_providers_description),
                    leadingIcon = Icons.Rounded.SwapHoriz,
                    leadingAccent = SettingsAccent.Primary,
                    checked = rule.handleProviders,
                    enabled = enabled,
                    shape = slot.shape,
                    containerColor = slot.color,
                    onCheckedChange = { onIntent(AppRuleIntent.SetHandleProviders(it)) },
                )
            },
            { slot ->
                SettingsItem(
                    title = stringResource(R.string.config_location),
                    value = locationLabel(rule.location, state.defaultLocation),
                    leadingIcon = Icons.Rounded.SdStorage,
                    leadingAccent = SettingsAccent.Tertiary,
                    enabled = enabled,
                    shape = slot.shape,
                    containerColor = slot.color,
                    onClick = onPickLocation,
                )
            },
        ),
    )
}

@Composable
private fun ViewerRows(enabled: Boolean, onIntent: (AppRuleIntent) -> Unit) {
    data class Viewer(val title: Int, val icon: ImageVector, val accent: SettingsAccent, val page: AppPage)
    val rows = listOf(
        Viewer(R.string.config_view_history, Icons.Rounded.History, SettingsAccent.Secondary, AppPage.History),
        Viewer(R.string.config_folder_analysis, Icons.Rounded.DataUsage, SettingsAccent.Tertiary, AppPage.Analysis),
    ).map { viewer ->
        @Composable { slot: RowSlot ->
            NavigationRow(
                slot = slot,
                title = stringResource(viewer.title),
                icon = viewer.icon,
                accent = viewer.accent,
                enabled = enabled,
                onClick = { onIntent(AppRuleIntent.Open(viewer.page)) },
            )
        }
    }
    RowGroup(rows = rows)
}

@Composable
private fun NavigationRow(
    slot: RowSlot,
    title: String,
    icon: ImageVector,
    accent: SettingsAccent,
    enabled: Boolean,
    onClick: () -> Unit,
    value: String? = null,
) {
    SettingsItem(
        title = title,
        value = value,
        leadingIcon = icon,
        leadingAccent = accent,
        enabled = enabled,
        shape = slot.shape,
        containerColor = slot.color,
        trailing = { Chevron() },
        onClick = onClick,
    )
}
