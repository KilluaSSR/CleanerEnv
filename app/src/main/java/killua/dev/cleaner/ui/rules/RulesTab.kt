package killua.dev.cleaner.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import killua.dev.arsenal.core.overlay.SimpleMenuItem
import killua.dev.arsenal.core.overlay.SimpleMenuMode
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.material.ArsenalTokens
import killua.dev.arsenal.material.components.application.AppFilterChip
import killua.dev.arsenal.material.components.application.AppFilterChipsRow
import killua.dev.arsenal.material.components.application.ArsenalSearchBar
import killua.dev.arsenal.material.components.application.SearchExpansionMode
import killua.dev.arsenal.material.components.application.appListItems
import killua.dev.arsenal.material.components.menu.rememberSimpleMenuLauncher
import killua.dev.arsenal.material.templates.basics.EmptyView
import killua.dev.arsenal.material.templates.basics.LoadingView
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppFilter
import killua.dev.cleaner.domain.model.AppScope
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
            ArsenalSearchBar(
                query = state.filter.query,
                onQueryChange = { viewModel.dispatch(RulesIntent.Search(it)) },
                expansionMode = SearchExpansionMode.Inline,
                placeholder = stringResource(R.string.rules_search_hint),
                clearContentDescription = stringResource(R.string.action_clear_search),
            )
            AppFilterChipsRow(chips = scopeChips(state.filter.scope) { viewModel.dispatch(RulesIntent.SetScope(it)) })
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
private fun scopeChips(scope: AppScope, onSelect: (AppScope) -> Unit): List<AppFilterChip> = listOf(
    AppFilterChip(
        label = stringResource(R.string.rules_scope_all),
        selected = scope == AppScope.All,
        onClick = { onSelect(AppScope.All) },
    ),
    AppFilterChip(
        label = stringResource(R.string.rules_scope_user),
        selected = scope == AppScope.UserOnly,
        onClick = { onSelect(AppScope.UserOnly) },
    ),
)

private const val FILTER_SHOW_DISABLED_ID = "show_disabled"
private const val FILTER_ONLY_STORAGE_ID = "only_storage"

@Composable
private fun FilterMenu(filter: AppFilter, onIntent: (RulesIntent) -> Unit) {
    val launcher = rememberSimpleMenuLauncher()
    var anchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val showDisabledLabel = stringResource(R.string.rules_show_disabled)
    val onlyStorageLabel = stringResource(R.string.rules_only_storage)
    IconButton(
        onClick = {
            anchor?.let { coordinates ->
                launcher.show(
                    items = listOf(
                        SimpleMenuItem(
                            id = FILTER_SHOW_DISABLED_ID,
                            label = showDisabledLabel,
                            selected = filter.showDisabled,
                        ),
                        SimpleMenuItem(
                            id = FILTER_ONLY_STORAGE_ID,
                            label = onlyStorageLabel,
                            selected = filter.onlyStorageApps,
                        ),
                    ),
                    anchor = coordinates,
                    mode = SimpleMenuMode.Multiple,
                    onSelectionChange = { ids ->
                        onIntent(RulesIntent.SetShowDisabled(FILTER_SHOW_DISABLED_ID in ids))
                        onIntent(RulesIntent.SetOnlyStorage(FILTER_ONLY_STORAGE_ID in ids))
                    },
                )
            }
        },
        modifier = Modifier.onGloballyPositioned { anchor = it },
    ) {
        Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.action_filter))
    }
}

@Composable
private fun AppList(apps: List<AppEntry>, onOpen: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ArsenalTokens.spacing.md, end = ArsenalTokens.spacing.md, bottom = ArsenalTokens.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ArsenalTokens.spacing.xs),
    ) {
        appListItems(
            items = apps,
            key = { it.packageName },
            packageName = { it.packageName },
            label = { it.label },
            onClick = { onOpen(it.packageName) },
            supporting = { app ->
                Text(
                    text = stringResource(R.string.rules_app_summary, app.versionName, app.targetSdk),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
            trailing = { app ->
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
            },
        )
    }
}
