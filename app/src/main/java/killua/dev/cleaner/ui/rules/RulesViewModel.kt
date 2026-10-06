package killua.dev.cleaner.ui.rules

import dagger.hilt.android.lifecycle.HiltViewModel
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.arsenal.navigation.Navigator
import killua.dev.arsenal.navigation.generated.GeneratedRoutes
import killua.dev.cleaner.data.settings.AppSettings
import killua.dev.cleaner.domain.AppCatalog
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppFilter
import killua.dev.cleaner.domain.model.AppScope
import killua.dev.cleaner.domain.usecase.FilterApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class RulesState(
    val apps: DataState<List<AppEntry>> = DataState.Loading(),
    val filter: AppFilter = AppFilter(),
) : UiState

sealed interface RulesIntent : UiIntent {
    data class SetScope(val scope: AppScope) : RulesIntent
    data class SetShowDisabled(val enabled: Boolean) : RulesIntent
    data class SetOnlyStorage(val enabled: Boolean) : RulesIntent
    data class Search(val query: String) : RulesIntent
    data class OpenApp(val packageName: String) : RulesIntent
    data object Refresh : RulesIntent
}

sealed interface RulesEffect : UiEffect

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val catalog: AppCatalog,
    private val settings: SettingsStore,
    private val navigator: Navigator,
    filterApps: FilterApps,
) : BaseViewModel<RulesState, RulesIntent, RulesEffect>(RulesState()) {

    private val query = MutableStateFlow("")

    init {
        val filters = combine(
            settings.flow(AppSettings.rulesScope),
            settings.flow(AppSettings.rulesShowDisabled),
            settings.flow(AppSettings.rulesOnlyStorage),
            query,
        ) { scope, showDisabled, onlyStorage, text -> AppFilter(scope, showDisabled, onlyStorage, text) }

        combine(catalog.observeApps(), filters) { apps, filter -> filter to filterApps(apps, filter) }
            .launchInScope { (filter, apps) ->
                setState {
                    copy(filter = filter, apps = if (apps.isEmpty()) DataState.Empty() else DataState.Success(apps))
                }
            }
    }

    override suspend fun handleIntent(intent: RulesIntent) {
        when (intent) {
            is RulesIntent.SetScope -> settings.set(AppSettings.rulesScope, intent.scope)
            is RulesIntent.SetShowDisabled -> settings.set(AppSettings.rulesShowDisabled, intent.enabled)
            is RulesIntent.SetOnlyStorage -> settings.set(AppSettings.rulesOnlyStorage, intent.enabled)
            is RulesIntent.Search -> {
                query.value = intent.query
                setState { copy(filter = filter.copy(query = intent.query)) }
            }
            is RulesIntent.OpenApp -> navigator.navigate(GeneratedRoutes.appConfigScreen(intent.packageName))
            RulesIntent.Refresh -> catalog.refresh()
        }
    }
}
