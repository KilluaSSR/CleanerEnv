package killua.dev.cleaner.ui.watch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.SettingsAccent
import killua.dev.arsenal.material.templates.settings.SettingsItem
import killua.dev.arsenal.material.templates.settings.SettingsSwitch
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.model.WatchHit
import killua.dev.cleaner.domain.model.WatchKind
import killua.dev.cleaner.domain.model.WatchRule
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.components.DetailScaffold
import killua.dev.cleaner.ui.components.EmptyView
import killua.dev.cleaner.ui.components.GroupHeader
import killua.dev.cleaner.ui.components.LoadingView
import killua.dev.cleaner.ui.components.RowGroup
import killua.dev.cleaner.ui.components.RowSlot
import killua.dev.cleaner.ui.components.ServerRequiredView
import killua.dev.cleaner.ui.components.labelRes
import killua.dev.cleaner.ui.components.rememberFormattedTime
import killua.dev.cleaner.ui.components.rememberRelativeTime
import killua.dev.cleaner.ui.history.icon
import killua.dev.cleaner.ui.picker.FolderField
import killua.dev.cleaner.ui.picker.FolderPickerSheet
import killua.dev.cleaner.ui.picker.FolderSource

@Composable
internal fun WatchRule.titleText(): String = when (kind) {
    WatchKind.Folder -> stringResource(R.string.watch_title_folder, name)
    WatchKind.File -> stringResource(R.string.watch_title_file, name)
    WatchKind.Everything -> stringResource(R.string.watch_title_everything)
}

@Composable
internal fun WatchRule.directoryText(): String =
    if (directory.isEmpty()) {
        stringResource(R.string.watch_directory_root)
    } else {
        stringResource(R.string.watch_directory_path, directory)
    }

@Composable
internal fun WatchRule.statusText(latest: WatchHit?, pending: Boolean): String = when {
    !enabled -> stringResource(R.string.watch_status_paused)
    pending -> stringResource(R.string.watch_status_pending)
    latest != null -> stringResource(
        R.string.watch_status_last,
        rememberRelativeTime(latest.occurredAtMs),
        stringResource(latest.type.labelRes()),
    )
    else -> stringResource(R.string.watch_status_idle)
}

internal fun WatchKind.icon(): ImageVector = when (this) {
    WatchKind.Folder -> Icons.Rounded.CreateNewFolder
    WatchKind.File -> Icons.AutoMirrored.Rounded.NoteAdd
    WatchKind.Everything -> Icons.Rounded.Visibility
}

internal fun WatchKind.accent(): SettingsAccent = when (this) {
    WatchKind.Folder -> SettingsAccent.Tertiary
    WatchKind.File -> SettingsAccent.Secondary
    WatchKind.Everything -> SettingsAccent.Primary
}

internal fun WatchRule.accent(): SettingsAccent = if (enabled) kind.accent() else SettingsAccent.Neutral

@NavDestination(route = "watch_editor/{ruleId}")
@Composable
fun WatchEditorScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: WatchEditorViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DetailScaffold(
        title = stringResource(if (state.isNew) R.string.watch_editor_title_new else R.string.watch_editor_title_edit),
        onBack = { viewModel.dispatch(WatchEditorIntent.Back) },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.padding(padding)) { LoadingView() }
        } else {
            WatchEditorContent(state, viewModel::dispatch, padding)
        }
    }
}

@Composable
private fun WatchEditorContent(state: WatchEditorState, onIntent: (WatchEditorIntent) -> Unit, padding: PaddingValues) {
    var name by rememberSaveable { mutableStateOf(state.name) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var namedKind by remember { mutableStateOf(state.kind.takeIf { it != WatchKind.Everything } ?: WatchKind.Folder) }
    if (state.kind != WatchKind.Everything) namedKind = state.kind

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "directory") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GroupHeader(title = stringResource(R.string.watch_section_directory))
                FolderField(
                    path = state.directory,
                    source = FolderSource.SharedStorage,
                    title = stringResource(R.string.watch_pick_directory),
                    supporting = stringResource(if (state.directoryError) R.string.watch_directory_required else R.string.watch_directory_supporting),
                    isError = state.directoryError,
                ) { picking = true }
            }
        }
        item(key = "kind") {
            Column {
                RowGroup(
                    title = stringResource(R.string.watch_section_kind),
                    rows = WatchKind.entries.map { kind ->
                        { slot -> KindRow(kind, selected = state.kind == kind, slot) { onIntent(WatchEditorIntent.SetKind(kind)) } }
                    },
                )
                AnimatedVisibility(
                    visible = state.kind != WatchKind.Everything,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    FormField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(if (namedKind == WatchKind.Folder) R.string.watch_name_folder else R.string.watch_name_file),
                        supporting = stringResource(if (state.nameError) R.string.watch_name_invalid else R.string.watch_name_supporting),
                        isError = state.nameError,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
        }
        item(key = "options") {
            RowGroup(
                title = stringResource(R.string.watch_section_options),
                rows = listOf(
                    { slot ->
                        SettingsSwitch(
                            title = stringResource(R.string.watch_recursive),
                            description = stringResource(R.string.watch_recursive_description),
                            leadingIcon = Icons.Rounded.AccountTree,
                            leadingAccent = SettingsAccent.Secondary,
                            checked = state.recursive,
                            onCheckedChange = { onIntent(WatchEditorIntent.SetRecursive(it)) },
                            shape = slot.shape,
                            containerColor = slot.color,
                        )
                    },
                    { slot ->
                        SettingsSwitch(
                            title = stringResource(R.string.watch_enabled),
                            description = stringResource(R.string.watch_enabled_description),
                            leadingIcon = Icons.Rounded.PowerSettingsNew,
                            leadingAccent = if (state.enabled) SettingsAccent.Primary else SettingsAccent.Neutral,
                            checked = state.enabled,
                            onCheckedChange = { onIntent(WatchEditorIntent.SetEnabled(it)) },
                            shape = slot.shape,
                            containerColor = slot.color,
                        )
                    },
                ),
            )
        }
        item(key = "note") {
            Text(
                text = stringResource(R.string.watch_attribution_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        item(key = "save") {
            Button(
                onClick = { onIntent(WatchEditorIntent.Save(name)) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.watch_save))
            }
        }
    }

    if (picking) {
        FolderPickerSheet(
            title = stringResource(R.string.watch_pick_directory),
            initialPath = state.directory.orEmpty(),
            onPick = {
                onIntent(WatchEditorIntent.SetDirectory(it))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun KindRow(kind: WatchKind, selected: Boolean, slot: RowSlot, onClick: () -> Unit) {
    val (title, description) = when (kind) {
        WatchKind.Folder -> R.string.watch_kind_folder to R.string.watch_kind_folder_description
        WatchKind.File -> R.string.watch_kind_file to R.string.watch_kind_file_description
        WatchKind.Everything -> R.string.watch_kind_everything to R.string.watch_kind_everything_description
    }
    SettingsItem(
        title = stringResource(title),
        description = stringResource(description),
        leadingIcon = kind.icon(),
        leadingAccent = if (selected) kind.accent() else SettingsAccent.Neutral,
        shape = slot.shape,
        containerColor = slot.color,
        trailing = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val fill = CleanerSurfaces.row
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(supporting) },
        isError = isError,
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            errorContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@NavDestination(route = "watch_log/{ruleId}")
@Composable
fun WatchLogScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: WatchLogViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rule = state.rule

    DetailScaffold(
        title = rule?.titleText().orEmpty(),
        subtitle = rule?.directoryText(),
        onBack = { viewModel.dispatch(WatchLogIntent.Back) },
        actions = {
            if (rule != null) {
                IconButton(onClick = { viewModel.dispatch(WatchLogIntent.Edit) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.watch_edit))
                }
                if (state.daemonOnline && state.events is DataState.Success) {
                    IconButton(onClick = { viewModel.dispatch(WatchLogIntent.Clear) }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.watch_clear))
                    }
                }
                IconButton(onClick = { viewModel.dispatch(WatchLogIntent.Delete) }) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = stringResource(R.string.watch_delete))
                }
            }
        },
    ) { padding ->
        if (rule == null) {
            Box(Modifier.padding(padding)) { LoadingView() }
            return@DetailScaffold
        }
        val events = (state.events as? DataState.Success)?.data.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "enabled") {
                SettingsSwitch(
                    title = stringResource(R.string.watch_enabled),
                    description = stringResource(R.string.watch_enabled_description),
                    leadingIcon = Icons.Rounded.PowerSettingsNew,
                    leadingAccent = rule.accent(),
                    checked = rule.enabled,
                    onCheckedChange = { viewModel.dispatch(WatchLogIntent.SetEnabled(it)) },
                    shape = groupedItemShape(itemPosition(0, if (state.pending) 2 else 1)),
                    containerColor = CleanerSurfaces.row,
                )
            }
            if (state.pending) {
                item(key = "pending") {
                    SettingsItem(
                        title = stringResource(R.string.watch_status_pending),
                        description = stringResource(R.string.watch_log_pending),
                        leadingIcon = Icons.Rounded.HourglassTop,
                        leadingAccent = SettingsAccent.Tertiary,
                        shape = groupedItemShape(itemPosition(1, 2)),
                        containerColor = CleanerSurfaces.row,
                    )
                }
            }
            item(key = "gap") { Box(Modifier.padding(top = Spacing.md)) }
            when {
                !state.daemonOnline -> item(key = "offline") { ServerRequiredView() }
                state.events is DataState.Loading -> item(key = "loading") { LoadingView() }
                events.isEmpty() -> item(key = "empty") {
                    EmptyView(
                        icon = rule.kind.icon(),
                        title = stringResource(R.string.watch_log_empty),
                        message = stringResource(R.string.watch_log_empty_message),
                    )
                }
                else -> itemsIndexed(events, key = { index, event -> "${event.occurredAtMs}-$index" }) { index, event ->
                    EventRow(event, groupedItemShape(itemPosition(index, events.size)))
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: WatchHit, shape: Shape) {
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
                    text = stringResource(
                        R.string.history_item_summary,
                        stringResource(event.type.labelRes()),
                        rememberFormattedTime(event.occurredAtMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@NavDestination(route = "watch_rules")
@Composable
fun WatchRulesScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: WatchRulesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(WatchRulesIntent.Refresh) }
    BackHandler(enabled = state.selecting) { viewModel.dispatch(WatchRulesIntent.ClearSelection) }

    DetailScaffold(
        title = if (state.selecting) {
            stringResource(R.string.watch_selected_count, state.selected.size)
        } else {
            stringResource(R.string.watch_rules_title)
        },
        onBack = { viewModel.dispatch(if (state.selecting) WatchRulesIntent.ClearSelection else WatchRulesIntent.Back) },
        actions = { WatchRulesActions(state, viewModel::dispatch) },
        floatingActionButton = {
            AnimatedVisibility(visible = !state.selecting, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.watch_add)) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = { viewModel.dispatch(WatchRulesIntent.Add) },
                )
            }
        },
    ) { padding ->
        if (state.rules.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyView(
                    icon = Icons.Rounded.Visibility,
                    title = stringResource(R.string.watch_rules_empty),
                    message = stringResource(R.string.watch_manage_desc),
                )
            }
            return@DetailScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = FAB_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            itemsIndexed(state.rules, key = { _, rule -> rule.id }) { index, rule ->
                ManagedRuleRow(
                    rule = rule,
                    status = rule.statusText(state.latest[rule.id], rule.id in state.pending),
                    selecting = state.selecting,
                    selected = rule.id in state.selected,
                    shape = groupedItemShape(itemPosition(index, state.rules.size)),
                    modifier = Modifier.animateItem(),
                    onClick = {
                        viewModel.dispatch(
                            if (state.selecting) WatchRulesIntent.ToggleSelected(rule.id) else WatchRulesIntent.Open(rule.id),
                        )
                    },
                    onLongClick = { viewModel.dispatch(WatchRulesIntent.ToggleSelected(rule.id)) },
                )
            }
        }
    }
}

private val FAB_CLEARANCE = 96.dp

@Composable
private fun WatchRulesActions(state: WatchRulesState, onIntent: (WatchRulesIntent) -> Unit) {
    if (!state.selecting) {
        if (state.rules.isNotEmpty()) {
            IconButton(onClick = { onIntent(WatchRulesIntent.StartSelection) }) {
                Icon(Icons.Rounded.Checklist, contentDescription = stringResource(R.string.watch_select))
            }
        }
        return
    }
    val any = state.selected.isNotEmpty()
    IconButton(onClick = { onIntent(WatchRulesIntent.SelectAll) }) {
        Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.watch_select_all))
    }
    IconButton(onClick = { onIntent(WatchRulesIntent.SetSelectedEnabled(true)) }, enabled = any) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.watch_enable_selected))
    }
    IconButton(onClick = { onIntent(WatchRulesIntent.SetSelectedEnabled(false)) }, enabled = any) {
        Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.watch_pause_selected))
    }
    IconButton(onClick = { onIntent(WatchRulesIntent.DeleteSelected) }, enabled = any) {
        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.watch_delete_selected))
    }
}

@Composable
private fun ManagedRuleRow(
    rule: WatchRule,
    status: String,
    selecting: Boolean,
    selected: Boolean,
    shape: Shape,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else CleanerSurfaces.row,
        label = "ruleContainer",
    )
    SettingsItem(
        title = rule.titleText(),
        description = stringResource(R.string.watch_row_summary, rule.directoryText(), status),
        leadingIcon = rule.kind.icon(),
        leadingAccent = rule.accent(),
        shape = shape,
        containerColor = container,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        trailing = {
            Crossfade(targetState = selecting, label = "ruleTrailing") { checking ->
                if (checking) {
                    Checkbox(checked = selected, onCheckedChange = null)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        },
    )
}
