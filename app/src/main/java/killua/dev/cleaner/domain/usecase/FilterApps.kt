package killua.dev.cleaner.domain.usecase

import killua.dev.arsenal.material.components.application.AppQuery
import killua.dev.arsenal.material.components.application.appLabelComparator
import killua.dev.arsenal.material.components.application.appTextMatches
import killua.dev.arsenal.material.components.application.applyAppQuery
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppFilter
import killua.dev.cleaner.domain.model.AppScope
import javax.inject.Inject

/**
 * Builds this app's [AppQuery] from its own filter dimensions (scope, show-disabled,
 * storage-only, query + an isolation-first sort) and runs it through the framework
 * pipeline. Each dimension is just another predicate — adding one never requires an
 * ordering value.
 */
class FilterApps @Inject constructor() {

    operator fun invoke(apps: List<AppEntry>, filter: AppFilter): List<AppEntry> {
        val query = AppQuery<AppEntry>(
            text = filter.query,
            predicates = listOf(
                { app -> filter.scope == AppScope.All || !app.isSystemApp },
                { app -> filter.showDisabled || app.isEnabled },
                { app -> !filter.onlyStorageApps || app.requestsStorage },
            ),
            comparator = compareByDescending<AppEntry> { it.isolationEnabled }
                .then(appLabelComparator { it.label }),
        )
        return apps.applyAppQuery(query) { app, text ->
            appTextMatches(text, app.label, app.packageName)
        }
    }
}
