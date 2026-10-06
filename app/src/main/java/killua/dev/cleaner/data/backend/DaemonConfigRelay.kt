package killua.dev.cleaner.data.backend

import killua.dev.arsenal.core.di.ApplicationScope
import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.cleaner.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DaemonConfigRelay @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val daemonLink: ServerConnection,
    private val settings: SettingsStore,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(daemonLink.server, settings.flow(AppSettings.fileMonitor)) { server, enabled -> server to enabled }
                .collect { (server, enabled) -> if (server != null) daemonLink.call { it.setMonitorEnabled(enabled) } }
        }
        scope.launch {
            combine(daemonLink.server, settings.flow(AppSettings.autoStartServer)) { server, enabled -> server to enabled }
                .collect { (server, enabled) -> if (server != null) daemonLink.call { it.setAutoStart(enabled) } }
        }
    }
}
