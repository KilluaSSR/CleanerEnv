package killua.dev.cleaner.data.backend

import killua.dev.arsenal.core.di.ApplicationScope
import killua.dev.arsenal.logging.ArsenalLog
import killua.dev.cleaner.data.repository.WatchRepositoryImpl
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.model.RootAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchFilePublisher @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @CurrentUser private val userId: Int,
    private val shell: RootShell,
    private val watches: WatchRepositoryImpl,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            shell.access
                .filter { it == RootAccess.Granted }
                .distinctUntilChanged()
                .collect { if (!watches.publish(userId)) ArsenalLog.w(TAG, "could not publish watch rules") }
        }
    }

    private companion object {
        const val TAG = "WatchFilePublisher"
    }
}
