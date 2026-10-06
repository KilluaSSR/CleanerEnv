package killua.dev.cleaner.data.backend

import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.WatchCodec
import killua.dev.cleaner.ipc.WatchSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchFileWriter @Inject constructor(private val shell: RootShell) {

    suspend fun write(userId: Int, specs: List<WatchSpec>): Boolean =
        shell.run(WatchFileScript.write(userId, specs)).isSuccess
}

internal object WatchFileScript {
    private const val END_MARKER = "__CLEANER_WATCH_END__"

    fun write(userId: Int, specs: List<WatchSpec>): String = buildString {
        val target = IpcConstants.watchFile(userId)
        appendLine("set -e")
        appendLine("umask 077")
        appendLine("mkdir -p '${IpcConstants.WATCHES_DIR}'")
        appendLine("chmod 700 '${IpcConstants.DATA_DIR}' '${IpcConstants.WATCHES_DIR}'")
        appendLine("cat > '$target.tmp' <<'$END_MARKER'")
        append(WatchCodec.encode(specs))
        appendLine(END_MARKER)
        appendLine("chmod 600 '$target.tmp'")
        appendLine("mv -f '$target.tmp' '$target'")
    }
}
