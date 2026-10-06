package killua.dev.cleaner.server

import android.os.FileObserver
import killua.dev.cleaner.ipc.FileEventParcel
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.RuleParcel
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class FileMonitor(
    private val history: HistoryStore,
    private val hub: DirectoryWatchHub,
    private val onEvent: (FileEventParcel) -> Unit,
) {
    private val watchers = ConcurrentHashMap<String, RootObserver>()

    @Volatile
    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean, rules: List<RuleParcel>) {
        enabled = value
        if (value) sync(rules) else stopAll()
    }

    fun sync(rules: List<RuleParcel>) {
        if (!enabled) return
        val wanted = rules.filter { it.enabled }.associate { rule ->
            val relative = IpcConstants.isolatedRelativeRoot(rule.packageName, rule.location)
            key(rule.packageName, rule.userId) to Watch(rule.packageName, rule.userId, File("/data/media/${rule.userId}/$relative"))
        }
        (watchers.keys - wanted.keys).forEach { stop(it) }
        wanted.forEach { (id, watch) ->
            if (!watchers.containsKey(id) && watch.root.isDirectory) {
                val watcher = RootObserver(watch).also { it.startWatching() }
                watchers[id] = watcher
            }
        }
    }

    fun stopAll() {
        watchers.keys.toList().forEach(::stop)
    }

    private fun stop(id: String) {
        watchers.remove(id)?.stopWatching()
    }

    private fun key(packageName: String, userId: Int) = "$userId/$packageName"

    private companion object {
        const val MODIFY_WINDOW_MS = 2_000L
    }

    private data class Watch(val packageName: String, val userId: Int, val root: File)

    private inner class RootObserver(private val watch: Watch) {
        private val dirs = ConcurrentHashMap.newKeySet<File>()
        private val lastModify = ConcurrentHashMap<String, Long>()
        private val listener = DirectoryWatchHub.Listener(::handle)

        fun startWatching() {
            watch.root.walkTopDown().filter { it.isDirectory }.forEach(::watchDir)
        }

        fun stopWatching() {
            dirs.forEach { hub.unsubscribe(it, listener) }
            dirs.clear()
        }

        private fun watchDir(dir: File) {
            if (dir !in dirs && hub.subscribe(dir, listener)) dirs.add(dir)
        }

        private fun handle(dir: File, event: Int, name: String) {
            if (event and DirectoryWatchHub.SELF_GONE != 0) {
                dirs.remove(dir)
                return
            }
            val target = File(dir, name)
            val isDir = event and DirectoryWatchHub.IS_DIR != 0
            val type = eventTypeOf(event, isDir)
            if (type == 0) return
            if (type == IpcConstants.EVENT_MODIFY) {
                val now = System.currentTimeMillis()
                val last = lastModify[target.path]
                if (last != null && now - last < MODIFY_WINDOW_MS) return
                lastModify[target.path] = now
            }
            if (isDir && event and (FileObserver.CREATE or FileObserver.MOVED_TO) != 0) watchDir(target)
            val relative = target.relativeToOrSelf(watch.root).path
            val parcel = FileEventParcel(
                occurredAtMs = System.currentTimeMillis(),
                packageName = watch.packageName,
                userId = watch.userId,
                subPath = relative,
                type = type,
            )
            history.record(parcel)
            onEvent(parcel)
        }
    }
}

internal fun eventTypeOf(event: Int, isDir: Boolean): Int = when {
    event and FileObserver.CREATE != 0 -> if (isDir) IpcConstants.EVENT_CREATE_DIR else IpcConstants.EVENT_CREATE
    event and FileObserver.DELETE != 0 -> if (isDir) IpcConstants.EVENT_DELETE_DIR else IpcConstants.EVENT_DELETE
    event and FileObserver.MODIFY != 0 -> IpcConstants.EVENT_MODIFY
    event and FileObserver.MOVED_FROM != 0 -> IpcConstants.EVENT_MOVED_FROM
    event and FileObserver.MOVED_TO != 0 -> IpcConstants.EVENT_MOVED_TO
    else -> 0
}
