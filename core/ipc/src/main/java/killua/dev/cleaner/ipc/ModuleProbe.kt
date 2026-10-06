package killua.dev.cleaner.ipc

data class ModuleSnapshot(
    val installed: Boolean,
    val updatePending: Boolean,
    val enabled: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val zygiskActive: Boolean,
    val zygiskVersionCode: Long,
) {
    companion object {
        val Absent = ModuleSnapshot(
            installed = false,
            updatePending = false,
            enabled = false,
            versionName = null,
            versionCode = 0,
            zygiskActive = false,
            zygiskVersionCode = 0,
        )
    }
}

object ModuleProbe {
    const val MODULE_DIR = "${IpcConstants.MODULES_DIR}/${IpcConstants.MODULE_ID}"
    const val MODULE_UPDATE_DIR = "/data/adb/modules_update/${IpcConstants.MODULE_ID}"
    const val BOOT_ID_FILE = "/proc/sys/kernel/random/boot_id"

    private const val SECTION_PROP = "@@prop"
    private const val SECTION_FLAGS = "@@flags"
    private const val SECTION_STATE = "@@state"
    private const val SECTION_BOOT = "@@boot"

    val SHELL_SCRIPT: String = buildString {
        append("echo $SECTION_PROP; cat $MODULE_DIR/module.prop 2>/dev/null; ")
        append("[ -f $MODULE_UPDATE_DIR/module.prop ] && cat $MODULE_UPDATE_DIR/module.prop; ")
        append("echo $SECTION_FLAGS; ")
        append("[ -f $MODULE_DIR/disable ] && echo disable; ")
        append("[ -f $MODULE_DIR/remove ] && echo remove; ")
        append("[ -d $MODULE_UPDATE_DIR ] && echo update; ")
        append("echo $SECTION_STATE; cat ${IpcConstants.ZYGISK_STATE_FILE} 2>/dev/null; ")
        append("echo $SECTION_BOOT; cat $BOOT_ID_FILE")
    }

    fun parseShellOutput(output: String): ModuleSnapshot {
        val sections = mutableMapOf<String, StringBuilder>()
        var current: StringBuilder? = null
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("@@")) {
                current = StringBuilder().also { sections[trimmed] = it }
            } else {
                current?.appendLine(line)
            }
        }
        val flags = sections[SECTION_FLAGS]?.lines()?.map { it.trim() }.orEmpty()
        return fromReadings(
            moduleProp = sections[SECTION_PROP]?.toString()?.takeIf { it.isNotBlank() },
            disabled = "disable" in flags,
            removing = "remove" in flags,
            updatePending = "update" in flags,
            zygiskState = sections[SECTION_STATE]?.toString(),
            bootId = sections[SECTION_BOOT]?.toString().orEmpty().trim(),
        )
    }

    fun fromReadings(
        moduleProp: String?,
        disabled: Boolean,
        removing: Boolean,
        updatePending: Boolean,
        zygiskState: String?,
        bootId: String,
    ): ModuleSnapshot {
        val props = moduleProp?.let(::parseProperties).orEmpty()
        val state = zygiskState?.let(::parseProperties).orEmpty()
        val installed = moduleProp != null
        val stateBoot = state["boot_id"].orEmpty()
        return ModuleSnapshot(
            installed = installed,
            updatePending = updatePending,
            enabled = installed && !disabled && !removing,
            versionName = props["version"],
            versionCode = props["versionCode"]?.toLongOrNull() ?: 0,
            zygiskActive = bootId.isNotEmpty() && stateBoot == bootId,
            zygiskVersionCode = state["version"]?.toLongOrNull() ?: 0,
        )
    }

    fun parseProperties(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()
}
