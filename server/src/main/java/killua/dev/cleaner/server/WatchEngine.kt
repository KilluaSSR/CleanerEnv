package killua.dev.cleaner.server

import android.os.FileObserver
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.WatchCodec
import killua.dev.cleaner.ipc.WatchEventParcel
import killua.dev.cleaner.ipc.WatchSpec
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class WatchEngine(
    private val hub: DirectoryWatchHub,
    private val log: WatchLogStore,
    private val onEvent: (WatchEventParcel) -> Unit,
) {
    private val armed = ConcurrentHashMap<String, ArmedWatch>()

    fun load(userId: Int) {
        val specs = runCatching { WatchCodec.decode(File(IpcConstants.watchFile(userId)).readText()) }
            .getOrDefault(emptyList())
            .filter { it.enabled }
            .associateBy { key(userId, it.id) }
        armed.entries
            .filter { (key, watch) -> watch.userId == userId && specs[key] != watch.spec }
            .forEach { (key, watch) -> if (armed.remove(key, watch)) watch.disarm() }
        specs.forEach { (key, spec) -> armed.computeIfAbsent(key) { ArmedWatch(userId, spec).also { it.arm() } } }
    }

    fun rearmPending() = armed.values.filter { it.pending }.forEach { it.arm() }

    fun pending(userId: Int): List<String> =
        armed.values.filter { it.userId == userId && it.pending }.map { it.spec.id }

    private fun key(userId: Int, id: String) = "$userId/$id"

    private inner class ArmedWatch(val userId: Int, val spec: WatchSpec) {
        private val storageRoot = File("/data/media/$userId")
        private val root = if (spec.directory.isEmpty()) storageRoot else File(storageRoot, spec.directory)
        private val dirs = ConcurrentHashMap.newKeySet<File>()
        private val lastModify = ConcurrentHashMap<String, Long>()
        private val listener = DirectoryWatchHub.Listener(::handle)
        private val anchorListener = DirectoryWatchHub.Listener(::handleAnchor)
        private var anchor: File? = null

        @Volatile
        var pending = false
            private set

        @Synchronized
        fun arm() {
            if (dirs.isNotEmpty()) return
            dropAnchor()
            if (!root.isDirectory) {
                pending = true
                val ancestor = generateSequence(root.parentFile) { it.parentFile }
                    .takeWhile { it.path.startsWith(storageRoot.path) }
                    .firstOrNull { it.isDirectory }
                if (ancestor != null && hub.subscribe(ancestor, anchorListener)) anchor = ancestor
                return
            }
            pending = false
            watchTree(root)
        }

        @Synchronized
        fun disarm() {
            dropAnchor()
            dirs.forEach { hub.unsubscribe(it, listener) }
            dirs.clear()
        }

        private fun dropAnchor() {
            anchor?.let { hub.unsubscribe(it, anchorListener) }
            anchor = null
        }

        private fun watchTree(dir: File) {
            if (dirs.size >= MAX_DIRS_PER_RULE || dir in dirs || !hub.subscribe(dir, listener)) return
            dirs.add(dir)
            if (spec.recursive) dir.listFiles()?.filter { it.isDirectory }?.forEach(::watchTree)
        }

        private fun handleAnchor(dir: File, event: Int, name: String) {
            val created = event and (FileObserver.CREATE or FileObserver.MOVED_TO) != 0
            if (!created || event and DirectoryWatchHub.IS_DIR == 0) return
            val appeared = File(dir, name).path
            if (root.path == appeared || root.path.startsWith("$appeared/")) arm()
        }

        private fun handle(dir: File, event: Int, name: String) {
            if (event and DirectoryWatchHub.SELF_GONE != 0) {
                dirs.remove(dir)
                if (dir == root) {
                    disarm()
                    arm()
                }
                return
            }
            val isDir = event and DirectoryWatchHub.IS_DIR != 0
            val type = eventTypeOf(event, isDir)
            if (type == 0) return
            val target = File(dir, name)
            if (spec.recursive && isDir && event and (FileObserver.CREATE or FileObserver.MOVED_TO) != 0) {
                watchTree(target)
            }
            if (!matches(type, isDir, name) || throttled(type, target)) return
            val parcel = WatchEventParcel(
                ruleId = spec.id,
                userId = userId,
                occurredAtMs = System.currentTimeMillis(),
                subPath = target.relativeToOrSelf(root).path,
                type = type,
            )
            log.record(parcel)
            onEvent(parcel)
        }

        private fun matches(type: Int, isDir: Boolean, name: String): Boolean = when (spec.kind) {
            IpcConstants.WATCH_KIND_FOLDER -> isDir && type != IpcConstants.EVENT_MODIFY && spec.matchesName(name)
            IpcConstants.WATCH_KIND_FILE -> !isDir && type != IpcConstants.EVENT_MODIFY && spec.matchesName(name)
            else -> true
        }

        private fun throttled(type: Int, target: File): Boolean {
            if (type != IpcConstants.EVENT_MODIFY) return false
            val now = System.currentTimeMillis()
            val last = lastModify.put(target.path, now)
            return last != null && now - last < MODIFY_WINDOW_MS
        }
    }

    private companion object {
        const val MODIFY_WINDOW_MS = 2_000L

        const val MAX_DIRS_PER_RULE = 2_048
    }
}

internal class WatchLogStore {
    private val lock = Any()

    fun record(event: WatchEventParcel) = synchronized(lock) {
        val file = file(event.ruleId, event.userId)
        file.parentFile?.mkdirs()
        if (file.length() > MAX_BYTES) {
            val lines = readLines(file)
            file.writeText(lines.takeLast(lines.size / 2).joinToString(separator = "\n", postfix = "\n"))
        }
        file.appendText("${event.occurredAtMs}\t${event.type}\t${event.subPath.replace('\n', ' ')}\n")
    }

    fun query(ruleId: String, userId: Int, limit: Int): List<WatchEventParcel> = synchronized(lock) {
        if (!WatchCodec.isValidId(ruleId)) return emptyList()
        readLines(file(ruleId, userId))
            .mapNotNull { parse(it, ruleId, userId) }
            .sortedByDescending { it.occurredAtMs }
            .take(limit.coerceIn(1, MAX_QUERY))
    }

    fun clear(ruleId: String?, userId: Int) {
        synchronized(lock) {
            when {
                ruleId == null -> File("${IpcConstants.WATCH_LOG_DIR}/$userId").deleteRecursively()
                WatchCodec.isValidId(ruleId) -> file(ruleId, userId).delete()
            }
        }
    }

    private fun readLines(file: File): List<String> = runCatching { file.readLines() }.getOrDefault(emptyList())

    private fun parse(line: String, ruleId: String, userId: Int): WatchEventParcel? {
        val first = line.indexOf('\t')
        val second = line.indexOf('\t', first + 1)
        if (first !in 1..<second) return null
        return WatchEventParcel(
            ruleId = ruleId,
            userId = userId,
            occurredAtMs = line.substring(0, first).toLongOrNull() ?: return null,
            subPath = line.substring(second + 1),
            type = line.substring(first + 1, second).toIntOrNull() ?: return null,
        )
    }

    private fun file(ruleId: String, userId: Int) = File("${IpcConstants.WATCH_LOG_DIR}/$userId/$ruleId.log")

    private companion object {
        const val MAX_BYTES = 512L * 1024
        const val MAX_QUERY = 2_000
    }
}
