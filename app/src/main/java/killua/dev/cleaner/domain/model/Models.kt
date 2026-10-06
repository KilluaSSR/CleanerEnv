package killua.dev.cleaner.domain.model

import killua.dev.cleaner.ipc.IpcConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class StorageLocation {
    DataFolder,

    CacheFolder;

    fun toWire(): Int = when (this) {
        DataFolder -> IpcConstants.LOCATION_DATA
        CacheFolder -> IpcConstants.LOCATION_CACHE
    }

    companion object {
        fun fromWire(value: Int): StorageLocation =
            if (value == IpcConstants.LOCATION_CACHE) CacheFolder else DataFolder
    }
}

data class AccessibleFolder(val subPath: String)

data class ExportRule(val viewPath: String, val realPath: String)

data class SharedFolder(val packageName: String, val subPath: String)

data class AppRule(
    val packageName: String,
    val userId: Int,
    val enabled: Boolean = false,
    val location: StorageLocation? = null,
    val accessibleFolders: List<AccessibleFolder> = emptyList(),
    val allowGallery: Boolean = false,
    val handleProviders: Boolean = false,
    val exports: List<ExportRule> = emptyList(),
    val sharedFolders: List<SharedFolder> = emptyList(),
) {
    val exportRuleCount: Int get() = exports.size

    companion object {
        fun empty(packageName: String, userId: Int) = AppRule(packageName = packageName, userId = userId)
    }
}

enum class StorageAccess { None, Media, ReadOnly, ReadWrite, AllFiles }

data class AppEntry(
    val packageName: String,
    val userId: Int,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val storageAccess: StorageAccess,
    val isolationEnabled: Boolean,
) {
    val requestsStorage: Boolean get() = storageAccess != StorageAccess.None
}

enum class FileEventType {
    Create, Modify, Delete, MovedFrom, MovedTo, CreateDir, DeleteDir, Unknown;

    companion object {
        fun fromWire(value: Int): FileEventType = when (value) {
            IpcConstants.EVENT_CREATE -> Create
            IpcConstants.EVENT_MODIFY -> Modify
            IpcConstants.EVENT_DELETE -> Delete
            IpcConstants.EVENT_MOVED_FROM -> MovedFrom
            IpcConstants.EVENT_MOVED_TO -> MovedTo
            IpcConstants.EVENT_CREATE_DIR -> CreateDir
            IpcConstants.EVENT_DELETE_DIR -> DeleteDir
            else -> Unknown
        }
    }
}

data class FileEvent(
    val occurredAtMs: Long,
    val packageName: String,
    val userId: Int,
    val subPath: String,
    val type: FileEventType,
)

data class MountRecord(
    val occurredAtMs: Long,
    val pid: Int,
    val uid: Int,
    val packageName: String,
    val success: Boolean,
    val message: String,
    val alive: Boolean,
)

data class FileEntry(
    val name: String,
    val subPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int,
)

enum class StorageBackend {
    Fuse, Sdcardfs, FuseBpf, Unknown;

    companion object {
        fun fromWire(value: Int): StorageBackend = when (value) {
            IpcConstants.BACKEND_FUSE -> Fuse
            IpcConstants.BACKEND_SDCARDFS -> Sdcardfs
            IpcConstants.BACKEND_FUSE_BPF -> FuseBpf
            else -> Unknown
        }
    }
}

data class ModuleState(
    val installed: Boolean,
    val updatePending: Boolean,
    val enabled: Boolean,
    val active: Boolean,
    val versionName: String?,
    val versionCode: Long,
) {
    companion object {
        val Unknown = ModuleState(
            installed = false,
            updatePending = false,
            enabled = false,
            active = false,
            versionName = null,
            versionCode = 0,
        )
    }
}

data class BundledModule(val versionName: String, val versionCode: Long)

enum class ModuleInstallPlan {
    Install,

    Enable,

    AwaitingReboot,

    UpToDate,

    Unavailable,
}

enum class RootAccess { Unknown, Granted, Denied }

enum class AppScope { All, UserOnly }

data class AppFilter(
    val scope: AppScope = AppScope.UserOnly,
    val showDisabled: Boolean = false,
    val onlyStorageApps: Boolean = false,
    val query: String = "",
)

sealed interface RuntimeState {
    val module: ModuleState

    data class Disconnected(val root: RootAccess, override val module: ModuleState) : RuntimeState

    data class Connected(
        val daemonRevision: Int,
        val serverPid: Int,
        val sdkInt: Int,
        val backend: StorageBackend,
        val startedAtMillis: Long,
        val monitorEnabled: Boolean,
        val enabledRuleCount: Int,
        override val module: ModuleState,
    ) : RuntimeState
}

enum class WatchKind {
    Folder,

    File,

    Everything,
}

data class WatchRule(
    val id: String,
    val userId: Int,
    val directory: String,
    val kind: WatchKind,
    val name: String,
    val recursive: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
)

data class WatchHit(
    val ruleId: String,
    val occurredAtMs: Long,
    val subPath: String,
    val type: FileEventType,
)

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("prerelease")
    val isPrerelease: Boolean = false,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
data class GithubAssetDto(
    val name: String,
    val size: Long = 0L,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    @SerialName("content_type")
    val contentType: String? = null,
)