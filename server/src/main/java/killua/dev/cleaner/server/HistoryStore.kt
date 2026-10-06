package killua.dev.cleaner.server

import killua.dev.cleaner.ipc.FileEventParcel
import killua.dev.cleaner.ipc.IpcConstants
import java.io.File

internal class HistoryStore {

    private val lock = Any()

    fun record(event: FileEventParcel) = synchronized(lock) {
        val file = file(event.packageName, event.userId)
        file.parentFile?.mkdirs()
        if (file.length() > MAX_BYTES) trim(file)
        file.appendText("${event.occurredAtMs}\t${event.type}\t${event.subPath.replace('\n', ' ')}\n")
    }

    fun query(packageName: String?, userId: Int, limit: Int): List<FileEventParcel> = synchronized(lock) {
        val cap = limit.coerceIn(1, 5000)
        val files = if (packageName != null) {
            listOf(file(packageName, userId))
        } else {
            File("${IpcConstants.HISTORY_DIR}/$userId").listFiles()?.toList().orEmpty()
        }
        files.flatMap { file ->
            val pkg = file.name.removeSuffix(SUFFIX)
            readLines(file).mapNotNull { parse(it, pkg, userId) }
        }.sortedByDescending { it.occurredAtMs }.take(cap)
    }

    fun latest(userId: Int): List<FileEventParcel> = synchronized(lock) {
        File("${IpcConstants.HISTORY_DIR}/$userId").listFiles()?.mapNotNull { file ->
            val pkg = file.name.removeSuffix(SUFFIX)
            readLines(file).asReversed().firstNotNullOfOrNull { parse(it, pkg, userId) }
        }.orEmpty().sortedByDescending { it.occurredAtMs }
    }

    fun clear(packageName: String?, userId: Int) {
        synchronized(lock) {
            if (packageName != null) {
                file(packageName, userId).delete()
            } else {
                File("${IpcConstants.HISTORY_DIR}/$userId").deleteRecursively()
            }
        }
    }

    private fun trim(file: File) {
        val lines = readLines(file)
        file.writeText(lines.takeLast(lines.size / 2).joinToString(separator = "\n", postfix = "\n"))
    }

    private fun readLines(file: File): List<String> =
        runCatching { file.readLines() }.getOrDefault(emptyList())

    private fun parse(line: String, packageName: String, userId: Int): FileEventParcel? {
        val first = line.indexOf('\t')
        val second = line.indexOf('\t', first + 1)
        if (first !in 1..<second) return null
        return FileEventParcel(
            occurredAtMs = line.substring(0, first).toLongOrNull() ?: return null,
            packageName = packageName,
            userId = userId,
            subPath = line.substring(second + 1),
            type = line.substring(first + 1, second).toIntOrNull() ?: return null,
        )
    }

    private fun file(packageName: String, userId: Int) =
        File("${IpcConstants.HISTORY_DIR}/$userId/$packageName$SUFFIX")

    private companion object {
        const val SUFFIX = ".log"
        const val MAX_BYTES = 1L shl 20
    }
}
