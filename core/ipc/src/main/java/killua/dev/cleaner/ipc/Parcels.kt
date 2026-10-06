package killua.dev.cleaner.ipc

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RuleParcel(
    val packageName: String,
    val userId: Int,
    val enabled: Boolean,
    val location: Int,
    val accessibleFolders: List<String>,
    val allowGallery: Boolean,
    val handleProviders: Boolean,
    val exportSources: List<String>,
    val exportTargets: List<String>,
    val sharedPackages: List<String>,
    val sharedPaths: List<String>,
) : Parcelable

@Parcelize
data class ServerStatusParcel(
    val version: Int,
    val pid: Int,
    val uid: Int,
    val sdkInt: Int,
    val storageBackend: Int,
    val startedAtMillis: Long,
    val monitorEnabled: Boolean,
    val activeRuleTotal: Int,
    val moduleInstalled: Boolean,
    val moduleUpdatePending: Boolean,
    val moduleEnabled: Boolean,
    val moduleVersionName: String?,
    val moduleVersionCode: Long,
    val zygiskActive: Boolean,
    val zygiskVersionCode: Long,
) : Parcelable

@Parcelize
data class MountRecordParcel(
    val occurredAtMs: Long,
    val pid: Int,
    val uid: Int,
    val packageName: String,
    val success: Boolean,
    val message: String,
    val alive: Boolean,
) : Parcelable

@Parcelize
data class FileEntryParcel(
    val name: String,
    val subPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int,
) : Parcelable

@Parcelize
data class FileEventParcel(
    val occurredAtMs: Long,
    val packageName: String,
    val userId: Int,
    val subPath: String,
    val type: Int,
) : Parcelable

@Parcelize
data class WatchEventParcel(
    val ruleId: String,
    val userId: Int,
    val occurredAtMs: Long,
    val subPath: String,
    val type: Int,
) : Parcelable
