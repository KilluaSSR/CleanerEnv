package killua.dev.cleaner.server

import android.os.FileObserver
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

internal class DirectoryWatchHub {

    fun interface Listener {
        fun onEvent(dir: File, event: Int, name: String)
    }

    private class Entry(val observer: FileObserver, val listeners: CopyOnWriteArraySet<Listener>)

    private val entries = HashMap<String, Entry>()

    fun subscribe(dir: File, listener: Listener): Boolean = synchronized(entries) {
        if (!dir.isDirectory) return false
        val entry = entries.getOrPut(dir.path) { open(dir) }
        entry.listeners.add(listener)
        true
    }

    fun unsubscribe(dir: File, listener: Listener) = synchronized(entries) {
        val entry = entries[dir.path] ?: return
        entry.listeners.remove(listener)
        if (entry.listeners.isEmpty()) {
            entry.observer.stopWatching()
            entries.remove(dir.path)
        }
    }

    private fun open(dir: File): Entry {
        val listeners = CopyOnWriteArraySet<Listener>()
        val observer = object : FileObserver(dir, MASK) {
            override fun onEvent(event: Int, path: String?) {
                if (event and SELF_GONE != 0) {
                    synchronized(entries) {
                        if (entries[dir.path]?.observer === this) entries.remove(dir.path)
                    }
                    stopWatching()
                    listeners.forEach { it.onEvent(dir, event, "") }
                    return
                }
                path ?: return
                listeners.forEach { it.onEvent(dir, event, path) }
            }
        }
        observer.startWatching()
        return Entry(observer, listeners)
    }

    companion object {
        const val SELF_GONE = FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

        const val IS_DIR = 0x40000000

        private const val MASK = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or SELF_GONE
    }
}
