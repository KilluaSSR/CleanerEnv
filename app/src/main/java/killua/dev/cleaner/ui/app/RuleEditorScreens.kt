package killua.dev.cleaner.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.settings.ItemPosition
import killua.dev.arsenal.material.templates.settings.SettingsAccent
import killua.dev.arsenal.material.templates.settings.SettingsSwitch
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.cleaner.R
import killua.dev.cleaner.ui.components.AppIcon
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.picker.FolderField
import killua.dev.cleaner.ui.picker.FolderPickerSheet
import killua.dev.cleaner.ui.picker.FolderSource

@Composable
private fun AppRuleState.title(): String = app?.label ?: packageName

@Composable
private fun FolderInput(path: String?, source: FolderSource, title: String, onPick: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FolderField(path = path, source = source, title = title) { picking = true }
    if (picking) {
        FolderPickerSheet(
            title = title,
            initialPath = path.orEmpty(),
            source = source,
            allowManualPath = false,
            onPick = {
                onPick(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@NavDestination(route = "accessible_folders/{packageName}")
@Composable
fun AccessibleFoldersScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: AppRuleViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    RuleListEditor(
        title = stringResource(R.string.config_accessible_folders),
        packageName = state.packageName,
        label = state.title(),
        description = stringResource(R.string.folders_description),
        items = state.rule.accessibleFolders.map { EditorItem(key = it.subPath, title = it.subPath) },
        emptyText = stringResource(R.string.folders_empty),
        itemIcon = Icons.Rounded.Folder,
        addLabel = stringResource(R.string.folders_add),
        onAdd = { adding = true },
        onRemove = { key ->
            state.rule.accessibleFolders.firstOrNull { it.subPath == key }
                ?.let { viewModel.dispatch(AppRuleIntent.RemoveFolder(it)) }
        },
        onBack = { viewModel.dispatch(AppRuleIntent.Back) },
        header = {
            item(key = "gallery") {
                SettingsSwitch(
                    title = stringResource(R.string.folders_allow_gallery),
                    description = stringResource(R.string.folders_allow_gallery_description),
                    leadingIcon = Icons.Rounded.PhotoLibrary,
                    leadingAccent = SettingsAccent.Tertiary,
                    checked = state.rule.allowGallery,
                    onCheckedChange = { viewModel.dispatch(AppRuleIntent.SetAllowGallery(it)) },
                    shape = groupedItemShape(ItemPosition.Single),
                    containerColor = CleanerSurfaces.row,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
            }
        },
    )

    if (adding) {
        var path by rememberSaveable { mutableStateOf<String?>(null) }
        AddEntrySheet(
            title = stringResource(R.string.folders_add),
            confirmEnabled = path != null,
            onConfirm = {
                path?.let { viewModel.dispatch(AppRuleIntent.AddFolder(it)) }
                adding = false
            },
            onDismiss = { adding = false },
        ) {
            FolderInput(path, FolderSource.SharedStorage, stringResource(R.string.folders_pick)) { path = it }
        }
    }
}

@NavDestination(route = "shared_folders/{packageName}")
@Composable
fun SharedFoldersScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: AppRuleViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val labels = state.isolatedApps.associate { it.packageName to it.label }

    RuleListEditor(
        title = stringResource(R.string.config_shared_folders),
        packageName = state.packageName,
        label = state.title(),
        description = stringResource(R.string.shared_description),
        items = state.rule.sharedFolders.map {
            EditorItem(
                key = "${it.packageName}/${it.subPath}",
                title = it.subPath,
                subtitle = labels[it.packageName] ?: it.packageName,
            )
        },
        emptyText = stringResource(R.string.shared_empty),
        itemIcon = Icons.Rounded.FolderShared,
        addLabel = stringResource(R.string.shared_add),
        onAdd = { adding = true },
        onRemove = { key ->
            state.rule.sharedFolders.firstOrNull { "${it.packageName}/${it.subPath}" == key }
                ?.let { viewModel.dispatch(AppRuleIntent.RemoveShared(it)) }
        },
        onBack = { viewModel.dispatch(AppRuleIntent.Back) },
    )

    if (adding) {
        var source by rememberSaveable { mutableStateOf<String?>(null) }
        var path by rememberSaveable { mutableStateOf<String?>(null) }
        AddEntrySheet(
            title = stringResource(R.string.shared_add),
            confirmEnabled = source != null && path != null,
            onConfirm = {
                val app = source
                val folder = path
                if (app != null && folder != null) viewModel.dispatch(AppRuleIntent.AddShared(app, folder))
                adding = false
            },
            onDismiss = { adding = false },
        ) {
            Text(
                text = stringResource(R.string.shared_pick_app),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isolatedApps.isEmpty()) {
                Text(stringResource(R.string.shared_no_apps), style = MaterialTheme.typography.bodyMedium)
            } else {
                SourceAppChips(state, selected = source) {
                    if (it != source) path = null
                    source = it
                }
            }
            source?.let { app ->
                FolderInput(path, FolderSource.Isolated(app), stringResource(R.string.shared_pick_folder)) { path = it }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceAppChips(state: AppRuleState, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.isolatedApps.forEach { app ->
            FilterChip(
                selected = selected == app.packageName,
                onClick = { onSelect(app.packageName) },
                label = { Text(app.label) },
                leadingIcon = { AppIcon(app.packageName, size = androidx.compose.material3.FilterChipDefaults.IconSize) },
            )
        }
    }
}

@NavDestination(route = "export_rules/{packageName}")
@Composable
fun ExportRulesScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: AppRuleViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    RuleListEditor(
        title = stringResource(R.string.config_export),
        packageName = state.packageName,
        label = state.title(),
        description = stringResource(R.string.export_description),
        items = state.rule.exports.map {
            EditorItem(key = it.viewPath, title = stringResource(R.string.export_item, it.viewPath, it.realPath))
        },
        emptyText = stringResource(R.string.export_empty),
        itemIcon = Icons.Rounded.IosShare,
        addLabel = stringResource(R.string.export_add),
        onAdd = { adding = true },
        onRemove = { key ->
            state.rule.exports.firstOrNull { it.viewPath == key }?.let { viewModel.dispatch(AppRuleIntent.RemoveExport(it)) }
        },
        onBack = { viewModel.dispatch(AppRuleIntent.Back) },
    )

    if (adding) {
        var view by rememberSaveable { mutableStateOf<String?>(null) }
        var real by rememberSaveable { mutableStateOf<String?>(null) }
        AddEntrySheet(
            title = stringResource(R.string.export_add),
            confirmEnabled = view != null && real != null,
            onConfirm = {
                val from = view
                val to = real
                if (from != null && to != null) viewModel.dispatch(AppRuleIntent.AddExport(from, to))
                adding = false
            },
            onDismiss = { adding = false },
        ) {
            FolderInput(
                path = view,
                source = FolderSource.Isolated(state.packageName, withSharedStorage = true),
                title = stringResource(R.string.export_view_label),
            ) { view = it }
            FolderInput(path = real, source = FolderSource.SharedStorage, title = stringResource(R.string.export_real_label)) {
                real = it
            }
        }
    }
}
