package killua.dev.cleaner.server

import android.net.LocalServerSocket
import killua.dev.cleaner.ipc.IpcConstants
import kotlin.concurrent.thread

internal class BinderRequestListener(private val deliver: (userId: Int) -> Unit) {

    fun start() {
        thread(name = "cleaner-binder-requests", isDaemon = true) {
            val server = runCatching { LocalServerSocket(IpcConstants.SERVER_SOCKET) }.getOrElse {
                ServerLog.w("binder request socket unavailable", it)
                return@thread
            }
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                runCatching {
                    client.use { socket ->
                        val request = socket.inputStream.read()
                        if (request == IpcConstants.SOCKET_REQUEST_BINDER.toInt()) {
                            deliver(socket.peerCredentials.uid / 100_000)
                            socket.outputStream.write(1)
                        }
                    }
                }
            }
        }
    }
}
