package killua.dev.cleaner.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.di.ApplicationScope
import killua.dev.arsenal.utils.`package`.PackageManagerAppCatalog
import killua.dev.arsenal.utils.`package`.isSystem
import killua.dev.arsenal.utils.`package`.isUserVisibleApp
import killua.dev.arsenal.utils.`package`.queryInstalledPackages
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.AppCatalog
import killua.dev.cleaner.domain.RuleRepository
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.StorageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates installed packages via the framework's [PackageManagerAppCatalog] (projecting
 * each [PackageInfo] to an [AppEntry]) and overlays rule-isolation state. All
 * PackageManager/broadcast plumbing now lives in the framework; only the permission →
 * [StorageAccess] mapping and the isolation overlay are app-specific.
 */
@Singleton
class AppCatalogImpl @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
    @CurrentUser private val userId: Int,
    private val rules: RuleRepository,
) : AppCatalog {

    private val selfPackage = context.packageName

    private val catalog = PackageManagerAppCatalog(
        context = context,
        scope = scope,
        query = { queryInstalledPackages(it, PackageManager.GET_PERMISSIONS) },
        include = { info -> info.isUserVisibleApp(selfPackage) },
        transform = { info, pm -> info.toEntry(pm) },
    )

    override fun observeApps(): Flow<List<AppEntry>> =
        combine(catalog.apps, enabledPackages()) { apps, enabled ->
            apps.map { app -> if (app.packageName in enabled) app.copy(isolationEnabled = true) else app }
        }

    override suspend fun refresh() = catalog.refresh()

    override suspend fun appOf(packageName: String): AppEntry? {
        val enabled = enabledPackages().first()
        return catalog.apps.first().firstOrNull { it.packageName == packageName }
            ?.let { app -> if (app.packageName in enabled) app.copy(isolationEnabled = true) else app }
    }

    private fun enabledPackages(): Flow<Set<String>> =
        rules.observeAll(userId)
            .map { list -> list.filter { it.enabled }.mapTo(HashSet()) { it.packageName } }
            .distinctUntilChanged()

    private fun PackageInfo.toEntry(pm: PackageManager): AppEntry {
        val app: ApplicationInfo = requireNotNull(applicationInfo)
        return AppEntry(
            packageName = packageName,
            userId = userId,
            label = app.loadLabel(pm).toString(),
            versionName = versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(this),
            targetSdk = app.targetSdkVersion,
            isSystemApp = app.isSystem(),
            isEnabled = app.enabled,
            storageAccess = storageAccessOf(requestedPermissions.orEmpty().toSet()),
            isolationEnabled = false,
        )
    }

    private fun storageAccessOf(permissions: Set<String>): StorageAccess = when {
        Manifest.permission.MANAGE_EXTERNAL_STORAGE in permissions -> StorageAccess.AllFiles
        Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions -> StorageAccess.ReadWrite
        Manifest.permission.READ_EXTERNAL_STORAGE in permissions -> StorageAccess.ReadOnly
        MEDIA_PERMISSIONS.any { it in permissions } -> StorageAccess.Media
        else -> StorageAccess.None
    }

    private companion object {
        val MEDIA_PERMISSIONS = listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        )
    }
}
