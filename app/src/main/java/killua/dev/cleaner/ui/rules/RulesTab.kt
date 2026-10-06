package killua.dev.cleaner.ui.rules

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppFilter
import killua.dev.cleaner.domain.model.AppScope
import killua.dev.cleaner.ui.components.AppIcon
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.components.EmptyView
import killua.dev.cleaner.ui.components.LoadingView
import killua.dev.cleaner.ui.components.Pill
import killua.dev.cleaner.ui.components.TabScaffold

@Composable
fun RulesTab() {
    val viewModel: RulesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TabScaffold(
        title = stringResource(R.string.rules_title),
        actions = { FilterMenu(state.filter, viewModel::dispatch) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SearchField(onQueryChange = { viewModel.dispatch(RulesIntent.Search(it)) })
            ScopeChips(state.filter.scope) { viewModel.dispatch(RulesIntent.SetScope(it)) }
            when (val apps = state.apps) {
                is DataState.Success -> AppList(apps.data) { viewModel.dispatch(RulesIntent.OpenApp(it)) }
                is DataState.Empty -> EmptyView(
                    icon = Icons.Rounded.Apps,
                    title = stringResource(R.string.rules_empty_title),
                    message = stringResource(R.string.rules_empty_message),
                )
                else -> LoadingView()
            }
        }
    }
}

@Composable
private fun FilterMenu(filter: AppFilter, onIntent: (RulesIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.action_filter))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_show_disabled)) },
                trailingIcon = { Checkbox(checked = filter.showDisabled, onCheckedChange = null) },
                onClick = { onIntent(RulesIntent.SetShowDisabled(!filter.showDisabled)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_only_storage)) },
                trailingIcon = { Checkbox(checked = filter.onlyStorageApps, onCheckedChange = null) },
                onClick = { onIntent(RulesIntent.SetOnlyStorage(!filter.onlyStorageApps)) },
            )
        }
    }
}

@Composable
private fun SearchField(onQueryChange: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val fill = CleanerSurfaces.row
    TextField(
        value = text,
        onValueChange = {
            text = it
            onQueryChange(it)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        placeholder = { Text(stringResource(R.string.rules_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onQueryChange("")
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ScopeChips(scope: AppScope, onSelect: (AppScope) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = scope == AppScope.All,
            onClick = { onSelect(AppScope.All) },
            label = { Text(stringResource(R.string.rules_scope_all)) },
        )
        FilterChip(
            selected = scope == AppScope.UserOnly,
            onClick = { onSelect(AppScope.UserOnly) },
            label = { Text(stringResource(R.string.rules_scope_user)) },
        )
    }
}

@Composable
private fun AppList(apps: List<AppEntry>, onOpen: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            Surface(
                shape = groupedItemShape(itemPosition(index, apps.size)),
                color = CleanerSurfaces.row,
                modifier = Modifier.animateItem(),
            ) {
                AppRow(app) { onOpen(app.packageName) }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppIcon(app.packageName)
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.rules_app_summary, app.versionName, app.targetSdk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        when {
            app.isolationEnabled -> Pill(
                text = stringResource(R.string.rules_isolated_badge),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            !app.isEnabled -> Pill(
                text = stringResource(R.string.rules_disabled_badge),
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
