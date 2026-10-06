package killua.dev.cleaner.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import killua.dev.arsenal.network.DefaultNetworkConfig
import killua.dev.arsenal.network.NetworkConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig = object : DefaultNetworkConfig(
        baseUrl = "https://api.github.com",
    ) {
        override val enableLogging: Boolean = false
        override val defaultHeaders: Map<String, String> = mapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to "Cleaner",
        )
    }
}
