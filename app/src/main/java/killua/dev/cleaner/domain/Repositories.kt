package killua.dev.cleaner.domain

import android.graphics.Bitmap
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppRule
import killua.dev.cleaner.domain.model.BundledModule
import killua.dev.cleaner.domain.model.FileEntry
import killua.dev.cleaner.domain.model.FileEvent
import killua.dev.cleaner.domain.model.ModuleInstallPlan
import killua.dev.cleaner.domain.model.MountRecord
import killua.dev.cleaner.domain.model.RuntimeState
import killua.dev.cleaner.domain.model.WatchHit
import killua.dev.cleaner.domain.model.WatchRule
import kotlinx.coroutines.flow.Flow

interface AppCatalog {
    fun observeApps(): Flow<List<AppEntry>>

    suspend fun refresh()

    suspend fun appOf(packageName: String): AppEntry?
}

interface RuleRepository {
    fun observeAll(userId: Int): Flow<List<AppRule>>

    fun observeRule(packageName: String, userId: Int): Flow<AppRule>

    suspend fun get(packageName: String, userId: Int): AppRule


    suspend fun save(rule: AppRule): Boolean
}

interface IsolationController {
    fun observeRuntime(): Flow<RuntimeState>

    fun observeConnected(): Flow<Boolean>

    suspend fun refresh()

    suspend fun ensureServer(): Boolean

    suspend fun stopServer()

    suspend fun restartApp(packageName: String, userId: Int): Boolean

    suspend fun bundledModule(): BundledModule?

    suspend fun moduleInstallPlan(): ModuleInstallPlan

    suspend fun installModule(): Boolean

    suspend fun enableModule(): Boolean

    suspend fun reboot()
}

interface IsolatedStorageRepository {
    suspend fun list(packageName: String, userId: Int, subPath: String): List<FileEntry>

    suspend fun analyze(packageName: String, userId: Int): List<FileEntry>

    suspend fun media(packageName: String, userId: Int, limit: Int): List<FileEntry>

    suspend fun thumbnail(packageName: String, userId: Int, subPath: String, maxSizePx: Int): Bitmap?

    suspend fun realFolders(userId: Int, subPath: String): List<String>?

    suspend fun isolatedFolders(packageName: String, userId: Int, subPath: String): List<String>?
}

interface WatchRepository {
    fun observeRules(userId: Int): Flow<List<WatchRule>>

    suspend fun get(id: String): WatchRule?

    suspend fun save(rule: WatchRule): Boolean

    suspend fun saveAll(rules: List<WatchRule>): Boolean

    suspend fun delete(rule: WatchRule)

    suspend fun deleteAll(rules: List<WatchRule>)

    fun observeEvents(ruleId: String, userId: Int, limit: Int): Flow<List<WatchHit>>

    suspend fun latestEvents(userId: Int): Map<String, WatchHit>

    suspend fun pending(userId: Int): Set<String>

    suspend fun clearEvents(ruleId: String, userId: Int)
}

interface FileHistoryRepository {
    fun observe(packageName: String?, userId: Int, limit: Int): Flow<List<FileEvent>>

    fun observeLatestPerApp(userId: Int): Flow<List<FileEvent>>

    suspend fun clear(packageName: String?, userId: Int)

    suspend fun mountRecords(limit: Int): List<MountRecord>
}
