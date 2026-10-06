package killua.dev.cleaner.server

import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.ModuleProbe
import java.io.File

internal class ServerConfig {
    private val file = File(IpcConstants.CONFIG_FILE)
    private val values: MutableMap<String, String> =
        runCatching { ModuleProbe.parseProperties(file.readText()).toMutableMap() }.getOrDefault(mutableMapOf())

    var monitorEnabled: Boolean
        get() = values[KEY_MONITOR] == "1"
        set(value) {
            values[KEY_MONITOR] = if (value) "1" else "0"
            persist()
        }

    fun setAutoStart(enabled: Boolean) {
        val marker = File(IpcConstants.AUTOSTART_FILE)
        if (enabled) {
            marker.parentFile?.mkdirs()
            marker.writeText("")
        } else {
            marker.delete()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(values.entries.joinToString(separator = "\n", postfix = "\n") { "${it.key}=${it.value}" })
    }

    private companion object {
        const val KEY_MONITOR = "monitor"
    }
}
