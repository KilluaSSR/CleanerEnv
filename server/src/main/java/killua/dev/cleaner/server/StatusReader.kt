package killua.dev.cleaner.server

import android.os.Build
import android.os.Process
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.ModuleProbe
import killua.dev.cleaner.ipc.ModuleSnapshot
import killua.dev.cleaner.ipc.MountRecordParcel
import killua.dev.cleaner.ipc.ServerStatusParcel
import java.io.File

internal class StatusReader {

    fun moduleSnapshot(): ModuleSnapshot {
        val moduleProp = read("${ModuleProbe.MODULE_DIR}/module.prop")
        val updateProp = read("${ModuleProbe.MODULE_UPDATE_DIR}/module.prop")
        return ModuleProbe.fromReadings(
            moduleProp = updateProp ?: moduleProp,
            disabled = File("${ModuleProbe.MODULE_DIR}/disable").exists(),
            removing = File("${ModuleProbe.MODULE_DIR}/remove").exists(),
            updatePending = File(ModuleProbe.MODULE_UPDATE_DIR).isDirectory,
            zygiskState = read(IpcConstants.ZYGISK_STATE_FILE),
            bootId = read(ModuleProbe.BOOT_ID_FILE).orEmpty().trim(),
        )
    }

    fun status(startedAt: Long, activeRuleTotal: Int, monitorEnabled: Boolean): ServerStatusParcel {
        val module = moduleSnapshot()
        return ServerStatusParcel(
            version = IpcConstants.SERVER_VERSION,
            pid = Process.myPid(),
            uid = Process.myUid(),
            sdkInt = Build.VERSION.SDK_INT,
            storageBackend = storageBackend(),
            startedAtMillis = startedAt,
            monitorEnabled = monitorEnabled,
            activeRuleTotal = activeRuleTotal,
            moduleInstalled = module.installed,
            moduleUpdatePending = module.updatePending,
            moduleEnabled = module.enabled,
            moduleVersionName = module.versionName,
            moduleVersionCode = module.versionCode,
            zygiskActive = module.zygiskActive,
            zygiskVersionCode = module.zygiskVersionCode,
        )
    }

    fun mountRecords(limit: Int): List<MountRecordParcel> {
        val lines = runCatching { File(IpcConstants.MOUNT_LOG_FILE).readLines() }.getOrDefault(emptyList())
        return lines.asReversed().asSequence()
            .mapNotNull(::parseMountLine)
            .take(limit.coerceIn(1, 1000))
            .toList()
    }

    private fun parseMountLine(line: String): MountRecordParcel? {
        val parts = line.split('\t')
        if (parts.size < 5) return null
        val pid = parts[1].toIntOrNull() ?: return null
        return MountRecordParcel(
            occurredAtMs = parts[0].toLongOrNull() ?: 0,
            pid = pid,
            uid = parts[2].toIntOrNull() ?: 0,
            packageName = parts[3],
            success = parts[4] == "1",
            message = parts.getOrElse(5) { "" },
            alive = File("/proc/$pid").exists(),
        )
    }

    private fun storageBackend(): Int = when (SystemApi.property("persist.sys.fuse")) {
        "true" -> IpcConstants.BACKEND_FUSE
        "false" -> IpcConstants.BACKEND_SDCARDFS
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) IpcConstants.BACKEND_FUSE else IpcConstants.BACKEND_SDCARDFS
    }

    private fun read(path: String): String? = runCatching { File(path).readText() }.getOrNull()
}
