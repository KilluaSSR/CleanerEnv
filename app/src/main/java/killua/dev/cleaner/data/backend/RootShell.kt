package killua.dev.cleaner.data.backend

import killua.dev.cleaner.domain.model.RootAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val exitCode: Int, val stdout: String) {
    val isSuccess: Boolean get() = exitCode == 0

    companion object {
        const val UNAVAILABLE = -1
        const val TIMED_OUT = -2
    }
}

@Singleton
class RootShell @Inject constructor() {

    private val _access = MutableStateFlow(RootAccess.Unknown)
    val access: StateFlow<RootAccess> = _access.asStateFlow()

    suspend fun run(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(SU).start()
        } catch (_: IOException) {
            _access.value = RootAccess.Denied
            return@withContext ShellResult(ShellResult.UNAVAILABLE, "")
        }
        coroutineScope {
            val stdout = async(Dispatchers.IO) { process.inputStream.bufferedReader().readText() }
            val stderr = async(Dispatchers.IO) { process.errorStream.bufferedReader().readText() }
            runCatching {
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(script)
                    writer.newLine()
                    writer.write("exit")
                    writer.newLine()
                }
            }
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            val result = ShellResult(if (finished) process.exitValue() else ShellResult.TIMED_OUT, stdout.await())
            stderr.await()
            result
        }
    }

    suspend fun requestAccess(): RootAccess {
        val result = run("id -u", ACCESS_TIMEOUT_MS)
        val access = if (result.isSuccess && result.stdout.trim() == "0") RootAccess.Granted else RootAccess.Denied
        _access.value = access
        return access
    }

    private companion object {
        const val SU = "su"
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val ACCESS_TIMEOUT_MS = 60_000L
    }
}
