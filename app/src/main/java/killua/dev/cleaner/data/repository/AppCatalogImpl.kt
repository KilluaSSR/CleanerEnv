package killua.dev.cleaner.data.repository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.di.ApplicationScope
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.AppCatalog
import killua.dev.cleaner.domain.RuleRepository
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.StorageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCatalogImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    @CurrentUser private val userId: Int,
    private val rules: RuleRepository,
) : AppCatalog {

    private val packageManager: PackageManager = context.packageManager
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val installed: SharedFlow<List<AppEntry>> =
        merge(refreshRequests, packageChanges())
            .onStart { emit(Unit) }
            .mapLatest { loadInstalled() }
            .flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), replay = 1)

    override fun observeApps(): Flow<List<AppEntry>> =
        combine(installed, enabledPackages()) { apps, enabled ->
            apps.map { app -> if (app.packageName in enabled) app.copy(isolationEnabled = true) else app }
        }

    override suspend fun refresh() {
        refreshRequests.emit(Unit)
    }

    override suspend fun appOf(packageName: String): AppEntry? {
        val enabled = enabledPackages().first()
        return installed.first().firstOrNull { it.packageName == packageName }
            ?.let { app -> if (app.packageName in enabled) app.copy(isolationEnabled = true) else app }
    }

    private fun enabledPackages(): Flow<Set<String>> =
        rules.observeAll(userId)
            .map { list -> list.filter { it.enabled }.mapTo(HashSet()) { it.packageName } }
            .distinctUntilChanged()

    private fun loadInstalled(): List<AppEntry> =
        queryPackageInfos()
            .filter { info ->
                val app = info.applicationInfo ?: return@filter false
                info.packageName != context.packageName && app.uid % PER_USER_RANGE >= FIRST_APPLICATION_UID
            }
            .map { info -> info.toEntry() }

    @Suppress("DEPRECATION")
    private fun queryPackageInfos(): List<PackageInfo> {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getInstalledPackages(flags)
        }
    }

    private fun PackageInfo.toEntry(): AppEntry {
        val app = requireNotNull(applicationInfo)
        return AppEntry(
            packageName = packageName,
            userId = userId,
            label = app.loadLabel(packageManager).toString(),
            versionName = versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(this),
            targetSdk = app.targetSdkVersion,
            isSystemApp = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
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

    private fun packageChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme(PACKAGE_SCHEME)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private companion object {
        const val PER_USER_RANGE = 100_000
        const val FIRST_APPLICATION_UID = 10_000
        const val KEEP_ALIVE_MS = 30_000L
        const val PACKAGE_SCHEME = "package"
        val MEDIA_PERMISSIONS = listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        )
    }
}
