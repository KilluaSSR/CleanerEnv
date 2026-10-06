package killua.dev.cleaner.di

import android.content.Context
import android.os.Process
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import killua.dev.cleaner.data.backend.IsolationControllerImpl
import killua.dev.cleaner.data.local.RuleDao
import killua.dev.cleaner.data.local.RuleDatabase
import killua.dev.cleaner.data.local.WatchRuleDao
import killua.dev.cleaner.data.repository.AppCatalogImpl
import killua.dev.cleaner.data.repository.FileHistoryRepositoryImpl
import killua.dev.cleaner.data.repository.IsolatedStorageRepositoryImpl
import killua.dev.cleaner.data.repository.RuleRepositoryImpl
import killua.dev.cleaner.data.repository.WatchRepositoryImpl
import killua.dev.cleaner.domain.AppCatalog
import killua.dev.cleaner.domain.FileHistoryRepository
import killua.dev.cleaner.domain.IsolatedStorageRepository
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.RuleRepository
import killua.dev.cleaner.domain.WatchRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

    @Provides
    @CurrentUser
    fun currentUser(): Int = Process.myUid() / PER_USER_RANGE

    @Provides
    @Singleton
    fun ruleDatabase(@ApplicationContext context: Context): RuleDatabase =
        Room.databaseBuilder(context, RuleDatabase::class.java, RuleDatabase.NAME).build()

    @Provides
    fun ruleDao(database: RuleDatabase): RuleDao = database.rules()

    @Provides
    fun watchRuleDao(database: RuleDatabase): WatchRuleDao = database.watches()

    private const val PER_USER_RANGE = 100_000
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    abstract fun appCatalog(impl: AppCatalogImpl): AppCatalog

    @Binds
    abstract fun ruleRepository(impl: RuleRepositoryImpl): RuleRepository

    @Binds
    abstract fun isolationController(impl: IsolationControllerImpl): IsolationController

    @Binds
    abstract fun isolatedStorage(impl: IsolatedStorageRepositoryImpl): IsolatedStorageRepository

    @Binds
    abstract fun fileHistory(impl: FileHistoryRepositoryImpl): FileHistoryRepository

    @Binds
    abstract fun watchRepository(impl: WatchRepositoryImpl): WatchRepository
}
