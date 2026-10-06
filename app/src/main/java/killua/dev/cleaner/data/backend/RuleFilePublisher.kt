package killua.dev.cleaner.data.backend

import killua.dev.arsenal.core.di.ApplicationScope
import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.arsenal.logging.ArsenalLog
import killua.dev.cleaner.data.settings.AppSettings
import killua.dev.cleaner.data.toParcel
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.RuleRepository
import killua.dev.cleaner.domain.model.RootAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleFilePublisher @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @CurrentUser private val userId: Int,
    private val shell: RootShell,
    private val rules: RuleRepository,
    private val settings: SettingsStore,
    private val files: RuleFileWriter,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(shell.access, settings.flow(AppSettings.defaultLocation)) { access, location -> access to location }
                .filter { (access, _) -> access == RootAccess.Granted }
                .distinctUntilChanged()
                .collect { (_, location) ->
                    val all = rules.observeAll(userId).first().map { it.toParcel(location) }
                    if (!files.replaceAll(userId, all)) ArsenalLog.w(TAG, "could not publish ${all.size} rules")
                }
        }
    }

    private companion object {
        const val TAG = "RuleFilePublisher"
    }
}
