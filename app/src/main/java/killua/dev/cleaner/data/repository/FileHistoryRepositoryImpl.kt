package killua.dev.cleaner.data.repository

import killua.dev.cleaner.data.backend.ServerConnection
import killua.dev.cleaner.data.toDomain
import killua.dev.cleaner.domain.FileHistoryRepository
import killua.dev.cleaner.domain.model.FileEvent
import killua.dev.cleaner.domain.model.MountRecord
import killua.dev.cleaner.ipc.FileEventParcel
import killua.dev.cleaner.ipc.IFileEventListener
import killua.dev.cleaner.ipc.IIsolationDaemon
import killua.dev.cleaner.ipc.WatchEventParcel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileHistoryRepositoryImpl @Inject constructor(
    private val daemonLink: ServerConnection,
) : FileHistoryRepository {

    private val invalidations = MutableStateFlow(0)

    override fun observe(packageName: String?, userId: Int, limit: Int): Flow<List<FileEvent>> = live(
        initial = { it.queryFileEvents(packageName, userId, limit) },
        accepts = { it.userId == userId && (packageName == null || it.packageName == packageName) },
        merge = { history, event -> (listOf(event) + history).take(limit) },
    )

    override fun observeLatestPerApp(userId: Int): Flow<List<FileEvent>> = live(
        initial = { it.latestFileEvents(userId) },
        accepts = { it.userId == userId },
        merge = { latest, event -> listOf(event) + latest.filterNot { it.packageName == event.packageName } },
    )


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun live(
        initial: (IIsolationDaemon) -> List<FileEventParcel>,
        accepts: (FileEventParcel) -> Boolean,
        merge: (List<FileEvent>, FileEvent) -> List<FileEvent>,
    ): Flow<List<FileEvent>> = channelFlow {
        combine(daemonLink.server, invalidations) { server, _ -> server }.collectLatest { server ->
            if (server == null) {
                send(emptyList())
                return@collectLatest
            }
            var events = daemonLink.call(initial).orEmpty().map { it.toDomain() }
            send(events)

            val incoming = Channel<FileEventParcel>(Channel.UNLIMITED)
            val listener = object : IFileEventListener.Stub() {
                override fun onFileEvent(event: FileEventParcel) {
                    if (accepts(event)) incoming.trySend(event)
                }

                override fun onWatchEvent(event: WatchEventParcel) = Unit
            }
            daemonLink.call { it.registerFileEventListener(listener) }
            try {
                for (parcel in incoming) {
                    events = merge(events, parcel.toDomain())
                    send(events)
                }
            } finally {
                daemonLink.call { it.unregisterFileEventListener(listener) }
            }
        }
    }

    override suspend fun clear(packageName: String?, userId: Int) {
        daemonLink.call { it.clearFileEvents(packageName, userId) }
        invalidations.update { it + 1 }
    }

    override suspend fun mountRecords(limit: Int): List<MountRecord> =
        daemonLink.call { it.getMountRecords(limit) }.orEmpty().map { it.toDomain() }
}
