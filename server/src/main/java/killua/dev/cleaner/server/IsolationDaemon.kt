package killua.dev.cleaner.server

import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import killua.dev.cleaner.ipc.FileEntryParcel
import killua.dev.cleaner.ipc.FileEventParcel
import killua.dev.cleaner.ipc.IIsolationDaemon
import killua.dev.cleaner.ipc.IFileEventListener
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.MountRecordParcel
import killua.dev.cleaner.ipc.ServerStatusParcel
import killua.dev.cleaner.ipc.WatchEventParcel

internal class IsolationDaemon : IIsolationDaemon.Stub() {

    private val startedAt = System.currentTimeMillis()
    private val handler = Handler(Looper.getMainLooper())
    private val rules = RuleStore()
    private val storage = IsolatedStorage(rules)
    private val statusReader = StatusReader()
    private val history = HistoryStore()
    private val config = ServerConfig()
    private val callers = CallerCheck()
    private val listeners = RemoteCallbackList<IFileEventListener>()
    private val watchHub = DirectoryWatchHub()
    private val monitor = FileMonitor(history, watchHub) { event -> broadcast { it.onFileEvent(event) } }
    private val watchLog = WatchLogStore()
    private val watches = WatchEngine(watchHub, watchLog) { event -> broadcast { it.onWatchEvent(event) } }

    private val resync = object : Runnable {
        override fun run() {
            rules.load()
            monitor.sync(rules.all())
            watches.rearmPending()
            handler.postDelayed(this, RESYNC_INTERVAL_MS)
        }
    }

    fun start() {
        rules.load()
        monitor.setEnabled(config.monitorEnabled, rules.all())
        val users = callers.installedUsers()
        users.forEach(watches::load)
        BinderRequestListener(::deliverBinder).start()
        users.forEach(::deliverBinder)
        handler.postDelayed(resync, RESYNC_INTERVAL_MS)
        ServerLog.i("server ready (rules=${rules.all().size}, monitor=${monitor.enabled})")
    }

    private fun deliverBinder(userId: Int) {
        if (!SystemApi.sendBinderToApp(IpcConstants.APPLICATION_ID, userId, this)) {
            ServerLog.w("could not deliver binder to user $userId")
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != INTERFACE_TRANSACTION && !callers.isAllowed(Binder.getCallingUid())) {
            throw SecurityException("uid ${Binder.getCallingUid()} may not use the Cleaner server")
        }
        return super.onTransact(code, data, reply, flags)
    }

    override fun protocolRevision(): Int = IpcConstants.SERVER_VERSION

    override fun getStatus(): ServerStatusParcel {
        rules.load()
        return statusReader.status(startedAt, rules.all().count { it.enabled }, monitor.enabled)
    }

    override fun getMountRecords(limit: Int): List<MountRecordParcel> = statusReader.mountRecords(limit)

    override fun listIsolated(packageName: String, userId: Int, subPath: String?): List<FileEntryParcel> =
        storage.list(packageName, userId, subPath.orEmpty())

    override fun analyzeIsolated(packageName: String, userId: Int): List<FileEntryParcel> =
        storage.analyze(packageName, userId)

    override fun listIsolatedMedia(packageName: String, userId: Int, limit: Int): List<FileEntryParcel> =
        storage.media(packageName, userId, limit)

    override fun openIsolated(packageName: String, userId: Int, subPath: String): ParcelFileDescriptor? =
        storage.open(packageName, userId, subPath)

    override fun listRealFolders(userId: Int, subPath: String?): List<String> = storage.realFolders(userId, subPath.orEmpty())

    override fun setMonitorEnabled(enabled: Boolean) {
        config.monitorEnabled = enabled
        monitor.setEnabled(enabled, rules.all())
    }

    override fun queryFileEvents(packageName: String?, userId: Int, limit: Int): List<FileEventParcel> =
        history.query(packageName, userId, limit)

    override fun latestFileEvents(userId: Int): List<FileEventParcel> = history.latest(userId)

    override fun clearFileEvents(packageName: String?, userId: Int) = history.clear(packageName, userId)

    override fun registerFileEventListener(listener: IFileEventListener) {
        listeners.register(listener)
    }

    override fun unregisterFileEventListener(listener: IFileEventListener) {
        listeners.unregister(listener)
    }

    override fun setAutoStart(enabled: Boolean) = config.setAutoStart(enabled)

    override fun reloadWatches(userId: Int) = watches.load(userId)

    override fun queryWatchEvents(ruleId: String, userId: Int, limit: Int): List<WatchEventParcel> =
        watchLog.query(ruleId, userId, limit)

    override fun clearWatchEvents(ruleId: String?, userId: Int) = watchLog.clear(ruleId, userId)

    override fun pendingWatches(userId: Int): List<String> = watches.pending(userId)

    override fun exit() {
        handler.postDelayed({ System.exit(0) }, EXIT_DELAY_MS)
    }

    private fun broadcast(deliver: (IFileEventListener) -> Unit) = synchronized(listeners) {
        val count = listeners.beginBroadcast()
        try {
            for (index in 0 until count) {
                runCatching { deliver(listeners.getBroadcastItem(index)) }
            }
        } finally {
            listeners.finishBroadcast()
        }
    }

    private companion object {
        const val RESYNC_INTERVAL_MS = 30_000L
        const val EXIT_DELAY_MS = 200L
    }
}
