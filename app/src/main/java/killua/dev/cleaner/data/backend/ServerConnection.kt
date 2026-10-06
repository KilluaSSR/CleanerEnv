package killua.dev.cleaner.data.backend

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.IBinder
import android.os.RemoteException
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.logging.ArsenalLog
import killua.dev.cleaner.domain.model.RootAccess
import killua.dev.cleaner.ipc.IIsolationDaemon
import killua.dev.cleaner.ipc.IpcConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds


@Singleton
class ServerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: RootShell,
) {
    private val _server = MutableStateFlow<IIsolationDaemon?>(null)
    val server: StateFlow<IIsolationDaemon?> = _server.asStateFlow()

    private val connectLock = Mutex()

    fun acceptDaemonBinder(binder: IBinder) {
        if (!binder.pingBinder()) return
        val service = IIsolationDaemon.Stub.asInterface(binder)
        val version = runCatching { service.protocolRevision() }.getOrNull()
        if (version != IpcConstants.SERVER_VERSION) {
            ArsenalLog.w(TAG, "server version $version != ${IpcConstants.SERVER_VERSION}")
            runCatching { service.exit() }
            return
        }
        try {
            binder.linkToDeath({ _server.compareAndSet(service, null) }, 0)
        } catch (_: RemoteException) {
            return
        }
        _server.value = service
    }

    suspend fun ensure(): IIsolationDaemon? {
        alive()?.let { return it }
        return connectLock.withLock {
            alive()?.let { return@withLock it }
            if (requestBinder()) await(REQUEST_TIMEOUT_MS)?.let { return@withLock it }
            if (shell.access.value != RootAccess.Granted && shell.requestAccess() != RootAccess.Granted) {
                return@withLock null
            }
            if (!start()) return@withLock null
            await(START_TIMEOUT_MS)
        }
    }

    suspend fun stop() {
        call { it.exit() }
        _server.value = null
    }

    suspend fun <T> call(block: (IIsolationDaemon) -> T): T? = withContext(Dispatchers.IO) {
        val service = _server.value ?: return@withContext null
        try {
            block(service)
        } catch (error: RemoteException) {
            ArsenalLog.w(TAG, "server call failed", error)
            if (!service.asBinder().pingBinder()) _server.compareAndSet(service, null)
            null
        }
    }

    private fun alive(): IIsolationDaemon? = _server.value?.takeIf { it.asBinder().pingBinder() }

    private suspend fun await(timeoutMs: Long): IIsolationDaemon? =
        withTimeoutOrNull(timeoutMs.milliseconds) { _server.filterNotNull().first() }

    private suspend fun requestBinder(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            LocalSocket().use { socket ->
                socket.connect(LocalSocketAddress(IpcConstants.SERVER_SOCKET))
                socket.outputStream.write(IpcConstants.SOCKET_REQUEST_BINDER.toInt())
                socket.inputStream.read() == 1
            }
        }.getOrDefault(false)
    }

    private suspend fun start(): Boolean {
        val info = context.applicationInfo
        val starter = File(info.nativeLibraryDir, IpcConstants.STARTER_LIBRARY)
        if (!starter.exists()) {
            ArsenalLog.e(TAG, "starter missing at ${starter.path}")
            return false
        }
        val result = shell.run("'${starter.path}' --apk='${info.sourceDir}'")
        if (!result.isSuccess) ArsenalLog.w(TAG, "starter exited with ${result.exitCode}")
        return result.isSuccess
    }

    private companion object {
        const val TAG = "ServerConnection"
        const val REQUEST_TIMEOUT_MS = 2_000L
        const val START_TIMEOUT_MS = 15_000L
    }
}
