package killua.dev.cleaner.data.backend

import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.RuleFileCodec
import killua.dev.cleaner.ipc.RuleParcel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleFileWriter @Inject constructor(private val shell: RootShell) {

    suspend fun write(rules: List<RuleParcel>): Boolean =
        rules.isEmpty() || shell.run(RuleFileScript.write(rules)).isSuccess

    suspend fun replaceAll(userId: Int, rules: List<RuleParcel>): Boolean =
        shell.run(RuleFileScript.replaceAll(userId, rules)).isSuccess
}

internal object RuleFileScript {
    private const val END_MARKER = "__CLEANER_RULE_END__"

    fun write(rules: List<RuleParcel>): String = buildString {
        appendLine("set -e")
        appendLine("umask 077")
        rules.groupBy { it.userId }.forEach { (userId, group) ->
            appendDirectory(userId)
            group.forEach { appendRule(it) }
        }
    }

    fun replaceAll(userId: Int, rules: List<RuleParcel>): String = buildString {
        append(write(rules.filter { it.userId == userId }))
        appendDirectory(userId)
        val keep = rules.filter { it.userId == userId }.map { "${it.packageName}${IpcConstants.RULE_FILE_SUFFIX}" }
        appendLine("for f in '${IpcConstants.RULES_DIR}/$userId/'*${IpcConstants.RULE_FILE_SUFFIX}; do")
        appendLine("  [ -e \"\$f\" ] || continue")
        if (keep.isEmpty()) {
            appendLine("  rm -f \"\$f\"")
        } else {
            appendLine("  case \"\${f##*/}\" in")
            appendLine("    ${keep.joinToString("|") { "'$it'" }}) ;;")
            appendLine("    *) rm -f \"\$f\" ;;")
            appendLine("  esac")
        }
        appendLine("done")
    }

    private fun StringBuilder.appendDirectory(userId: Int) {
        val dir = "${IpcConstants.RULES_DIR}/$userId"
        appendLine("mkdir -p '$dir'")
        appendLine("chmod 700 '${IpcConstants.DATA_DIR}' '${IpcConstants.RULES_DIR}' '$dir'")
    }

    private fun StringBuilder.appendRule(rule: RuleParcel) {
        require(RuleFileCodec.isValidPackageName(rule.packageName)) { "invalid package ${rule.packageName}" }
        val target = IpcConstants.ruleFile(rule.userId, rule.packageName)
        appendLine("cat > '$target.tmp' <<'$END_MARKER'")
        append(RuleFileCodec.encode(rule).trimEnd('\n'))
        appendLine()
        appendLine(END_MARKER)
        appendLine("chmod 600 '$target.tmp'")
        appendLine("mv -f '$target.tmp' '$target'")
    }
}
