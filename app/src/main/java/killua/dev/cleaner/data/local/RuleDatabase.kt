package killua.dev.cleaner.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ExportDto(val view: String, val real: String)

@Serializable
data class SharedDto(val packageName: String, val path: String)

@Entity(tableName = "rules", primaryKeys = ["packageName", "userId"])
data class RuleEntity(
    val packageName: String,
    val userId: Int,
    val enabled: Boolean,
    val location: String?,
    val accessibleFolders: String,
    val allowGallery: Boolean,
    val handleProviders: Boolean,
    val exports: String,
    val sharedFolders: String,
    val updatedAt: Long,
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE userId = :userId")
    fun observeAll(userId: Int): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE packageName = :packageName AND userId = :userId")
    fun observe(packageName: String, userId: Int): Flow<RuleEntity?>

    @Query("SELECT * FROM rules WHERE packageName = :packageName AND userId = :userId")
    suspend fun get(packageName: String, userId: Int): RuleEntity?

    @Upsert
    suspend fun upsert(entity: RuleEntity)
}

@Entity(tableName = "watch_rules")
data class WatchRuleEntity(
    @PrimaryKey val id: String,
    val userId: Int,
    val directory: String,
    val kind: String,
    val name: String,
    val recursive: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
)

@Dao
interface WatchRuleDao {
    @Query("SELECT * FROM watch_rules WHERE userId = :userId ORDER BY createdAt")
    fun observeAll(userId: Int): Flow<List<WatchRuleEntity>>

    @Query("SELECT * FROM watch_rules WHERE userId = :userId ORDER BY createdAt")
    suspend fun all(userId: Int): List<WatchRuleEntity>

    @Query("SELECT * FROM watch_rules WHERE id = :id")
    suspend fun get(id: String): WatchRuleEntity?

    @Upsert
    suspend fun upsert(entities: List<WatchRuleEntity>)

    @Delete
    suspend fun delete(entities: List<WatchRuleEntity>)
}

@Database(entities = [RuleEntity::class, WatchRuleEntity::class], version = 1, exportSchema = false)
abstract class RuleDatabase : RoomDatabase() {
    abstract fun rules(): RuleDao

    abstract fun watches(): WatchRuleDao

    companion object {
        const val NAME = "rules.db"
    }
}
