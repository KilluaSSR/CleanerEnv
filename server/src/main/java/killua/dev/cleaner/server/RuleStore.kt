package killua.dev.cleaner.server

import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.RuleFileCodec
import killua.dev.cleaner.ipc.RuleParcel
import java.io.File

internal class RuleStore {
    @Volatile
    private var rules: Map<String, RuleParcel> = emptyMap()

    fun load() {
        val loaded = HashMap<String, RuleParcel>()
        File(IpcConstants.RULES_DIR).listFiles()?.forEach { userDir ->
            val userId = userDir.name.toIntOrNull() ?: return@forEach
            userDir.listFiles { file -> file.name.endsWith(IpcConstants.RULE_FILE_SUFFIX) }?.forEach { file ->
                val packageName = file.name.removeSuffix(IpcConstants.RULE_FILE_SUFFIX)
                if (RuleFileCodec.isValidPackageName(packageName)) {
                    val rule = RuleFileCodec.decode(packageName, userId, file.readText())
                    loaded[key(packageName, userId)] = rule
                }
            }
        }
        rules = loaded
    }

    fun all(): List<RuleParcel> = rules.values.toList()

    fun enabled(): List<RuleParcel> = rules.values.filter { it.enabled }

    fun get(packageName: String, userId: Int): RuleParcel? = rules[key(packageName, userId)]

    private fun key(packageName: String, userId: Int) = "$userId/$packageName"
}
