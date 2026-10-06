package killua.dev.cleaner.data.repository

import killua.dev.cleaner.data.backend.ServerConnection
import killua.dev.cleaner.data.backend.WatchFileWriter
import killua.dev.cleaner.data.local.WatchRuleDao
import killua.dev.cleaner.data.toDomain
import killua.dev.cleaner.data.toEntity
import killua.dev.cleaner.data.toSpec
import killua.dev.cleaner.domain.WatchRepository
import killua.dev.cleaner.domain.model.WatchHit
import killua.dev.cleaner.domain.model.WatchRule
import killua.dev.cleaner.ipc.FileEventParcel
import killua.dev.cleaner.ipc.IFileEventListener
import killua.dev.cleaner.ipc.WatchEventParcel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchRepositoryImpl @Inject constructor(
    private val dao: WatchRuleDao,
    private val files: WatchFileWriter,
    private val daemonLink: ServerConnection,
) : WatchRepository {

    private val invalidations = MutableStateFlow(0)

    override fun observeRules(userId: Int): Flow<List<WatchRule>> =
        dao.observeAll(userId).map { rules -> rules.map { it.toDomain() } }

    override suspend fun get(id: String): WatchRule? = dao.get(id)?.toDomain()

    override suspend fun save(rule: WatchRule): Boolean = saveAll(listOf(rule))

    override suspend fun saveAll(rules: List<WatchRule>): Boolean {
        if (rules.isEmpty()) return true
        dao.upsert(rules.map { it.toEntity() })
        return rules.map { it.userId }.distinct().all { publish(it) }
    }

    override suspend fun delete(rule: WatchRule) = deleteAll(listOf(rule))

    override suspend fun deleteAll(rules: List<WatchRule>) {
        if (rules.isEmpty()) return
        dao.delete(rules.map { it.toEntity() })
        rules.map { it.userId }.distinct().forEach { publish(it) }
        daemonLink.call { server -> rules.forEach { server.clearWatchEvents(it.id, it.userId) } }
        invalidations.update { it + 1 }
    }

    suspend fun publish(userId: Int): Boolean {
        val written = files.write(userId, dao.all(userId).map { it.toDomain().toSpec() })
        if (written) daemonLink.call { it.reloadWatches(userId) }
        return written
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeEvents(ruleId: String, userId: Int, limit: Int): Flow<List<WatchHit>> = channelFlow {
        combine(daemonLink.server, invalidations) { server, _ -> server }.collectLatest { server ->
            if (server == null) {
                send(emptyList())
                return@collectLatest
            }
            val events = ArrayDeque(
                daemonLink.call { it.queryWatchEvents(ruleId, userId, limit) }.orEmpty().map { it.toDomain() },
            )
            send(events.toList())

            val incoming = Channel<WatchEventParcel>(Channel.UNLIMITED)
            val listener = object : IFileEventListener.Stub() {
                override fun onFileEvent(event: FileEventParcel) = Unit

                override fun onWatchEvent(event: WatchEventParcel) {
                    if (event.ruleId == ruleId && event.userId == userId) incoming.trySend(event)
                }
            }
            daemonLink.call { it.registerFileEventListener(listener) }
            try {
                for (parcel in incoming) {
                    events.addFirst(parcel.toDomain())
                    while (events.size > limit) events.removeLast()
                    send(events.toList())
                }
            } finally {
                daemonLink.call { it.unregisterFileEventListener(listener) }
            }
        }
    }

    override suspend fun latestEvents(userId: Int): Map<String, WatchHit> {
        val ids = dao.all(userId).map { it.id }
        return daemonLink.call { server ->
            ids.mapNotNull { id -> server.queryWatchEvents(id, userId, 1).firstOrNull()?.let { id to it.toDomain() } }.toMap()
        }.orEmpty()
    }

    override suspend fun pending(userId: Int): Set<String> =
        daemonLink.call { it.pendingWatches(userId) }.orEmpty().toSet()

    override suspend fun clearEvents(ruleId: String, userId: Int) {
        daemonLink.call { it.clearWatchEvents(ruleId, userId) }
        invalidations.update { it + 1 }
    }
}
