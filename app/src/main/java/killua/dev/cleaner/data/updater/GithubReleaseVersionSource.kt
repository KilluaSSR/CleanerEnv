package killua.dev.cleaner.data.updater

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.result.Outcome
import killua.dev.arsenal.network.ApiClient
import killua.dev.arsenal.update.UpdateInfo
import killua.dev.arsenal.update.VersionSource
import killua.dev.arsenal.update.currentAppVersion
import killua.dev.cleaner.domain.model.GithubReleaseDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubReleaseVersionSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: ApiClient,
) : VersionSource {

    override suspend fun fetchLatest(): UpdateInfo? {
        val release = when (val outcome = apiClient.get<GithubReleaseDto>(LATEST_RELEASE)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> throw outcome.error
        }
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        val versionName = release.tagName.trim().removePrefix("v").removePrefix("V")
        val installed = context.currentAppVersion()
        val newer = compareVersions(versionName, installed.versionName) > 0
        return UpdateInfo(
            versionName = versionName,
            versionCode = if (newer) installed.versionCode + 1 else installed.versionCode,
            downloadUrl = apk.browserDownloadUrl,
            releaseNotes = release.body,
            fileName = apk.name,
        )
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = numbers(a)
        val right = numbers(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val order = left.getOrElse(i) { 0L }.compareTo(right.getOrElse(i) { 0L })
            if (order != 0) return order
        }
        return 0
    }

    private fun numbers(version: String): List<Long> =
        version.substringBefore('-').substringBefore('+').split('.').map { it.trim().toLongOrNull() ?: 0L }

    private companion object {
        const val LATEST_RELEASE = "/repos/KilluaSSR/CleanerEnv/releases/latest"
    }
}
