package killua.dev.cleaner.data.repository

import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.cleaner.data.backend.RuleFileWriter
import killua.dev.cleaner.data.local.RuleDao
import killua.dev.cleaner.data.settings.AppSettings
import killua.dev.cleaner.data.toDomain
import killua.dev.cleaner.data.toEntity
import killua.dev.cleaner.data.toParcel
import killua.dev.cleaner.domain.RuleRepository
import killua.dev.cleaner.domain.model.AppRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepositoryImpl @Inject constructor(
    private val dao: RuleDao,
    private val files: RuleFileWriter,
    private val settings: SettingsStore,
) : RuleRepository {

    override fun observeAll(userId: Int): Flow<List<AppRule>> =
        dao.observeAll(userId).map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeRule(packageName: String, userId: Int): Flow<AppRule> =
        dao.observe(packageName, userId)
            .map { it?.toDomain() ?: AppRule.empty(packageName, userId) }
            .distinctUntilChanged()

    override suspend fun get(packageName: String, userId: Int): AppRule =
        dao.get(packageName, userId)?.toDomain() ?: AppRule.empty(packageName, userId)

    override suspend fun save(rule: AppRule): Boolean {
        dao.upsert(rule.toEntity(System.currentTimeMillis()))
        settings.awaitLoaded()
        return files.write(listOf(rule.toParcel(settings.get(AppSettings.defaultLocation))))
    }
}
