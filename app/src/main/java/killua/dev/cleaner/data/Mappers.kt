package killua.dev.cleaner.data

import killua.dev.cleaner.data.local.ExportDto
import killua.dev.cleaner.data.local.RuleEntity
import killua.dev.cleaner.data.local.SharedDto
import killua.dev.cleaner.data.local.WatchRuleEntity
import killua.dev.cleaner.domain.model.AccessibleFolder
import killua.dev.cleaner.domain.model.AppRule
import killua.dev.cleaner.domain.model.ExportRule
import killua.dev.cleaner.domain.model.FileEntry
import killua.dev.cleaner.domain.model.FileEvent
import killua.dev.cleaner.domain.model.FileEventType
import killua.dev.cleaner.domain.model.ModuleState
import killua.dev.cleaner.domain.model.MountRecord
import killua.dev.cleaner.domain.model.RuntimeState
import killua.dev.cleaner.domain.model.SharedFolder
import killua.dev.cleaner.domain.model.StorageBackend
import killua.dev.cleaner.domain.model.StorageLocation
import killua.dev.cleaner.domain.model.WatchHit
import killua.dev.cleaner.domain.model.WatchKind
import killua.dev.cleaner.domain.model.WatchRule
import killua.dev.cleaner.ipc.FileEntryParcel
import killua.dev.cleaner.ipc.FileEventParcel
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.ModuleSnapshot
import killua.dev.cleaner.ipc.MountRecordParcel
import killua.dev.cleaner.ipc.RuleFileCodec
import killua.dev.cleaner.ipc.RuleParcel
import killua.dev.cleaner.ipc.ServerStatusParcel
import killua.dev.cleaner.ipc.WatchEventParcel
import killua.dev.cleaner.ipc.WatchSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

internal fun RuleEntity.toDomain(): AppRule = AppRule(
    packageName = packageName,
    userId = userId,
    enabled = enabled,
    location = location?.let { name -> StorageLocation.entries.firstOrNull { it.name == name } },
    accessibleFolders = json.decodeFromString<List<String>>(accessibleFolders).map(::AccessibleFolder),
    allowGallery = allowGallery,
    handleProviders = handleProviders,
    exports = json.decodeFromString<List<ExportDto>>(exports).map { ExportRule(it.view, it.real) },
    sharedFolders = json.decodeFromString<List<SharedDto>>(sharedFolders).map { SharedFolder(it.packageName, it.path) },
)

internal fun AppRule.toEntity(now: Long): RuleEntity = RuleEntity(
    packageName = packageName,
    userId = userId,
    enabled = enabled,
    location = location?.name,
    accessibleFolders = json.encodeToString(accessibleFolders.map { it.subPath }),
    allowGallery = allowGallery,
    handleProviders = handleProviders,
    exports = json.encodeToString(exports.map { ExportDto(it.viewPath, it.realPath) }),
    sharedFolders = json.encodeToString(sharedFolders.map { SharedDto(it.packageName, it.subPath) }),
    updatedAt = now,
)

internal fun AppRule.toParcel(defaultLocation: StorageLocation): RuleParcel {
    val validExports = exports.mapNotNull { rule ->
        val view = RuleFileCodec.normalizeRelativePath(rule.viewPath)
        val real = RuleFileCodec.normalizeRelativePath(rule.realPath)
        if (view != null && real != null) view to real else null
    }
    val validShared = sharedFolders.mapNotNull { folder ->
        val path = RuleFileCodec.normalizeRelativePath(folder.subPath)
        if (path != null && RuleFileCodec.isValidPackageName(folder.packageName)) folder.packageName to path else null
    }
    return RuleParcel(
        packageName = packageName,
        userId = userId,
        enabled = enabled,
        location = (location ?: defaultLocation).toWire(),
        accessibleFolders = accessibleFolders.mapNotNull { RuleFileCodec.normalizeRelativePath(it.subPath) },
        allowGallery = allowGallery,
        handleProviders = handleProviders,
        exportSources = validExports.map { it.first },
        exportTargets = validExports.map { it.second },
        sharedPackages = validShared.map { it.first },
        sharedPaths = validShared.map { it.second },
    )
}

internal fun ModuleSnapshot.toDomain(): ModuleState = ModuleState(
    installed = installed,
    updatePending = updatePending,
    enabled = enabled,
    active = zygiskActive,
    versionName = versionName,
    versionCode = versionCode,
)

internal fun ServerStatusParcel.toDomain(): RuntimeState.Connected = RuntimeState.Connected(
    daemonRevision = version,
    serverPid = pid,
    sdkInt = sdkInt,
    backend = StorageBackend.fromWire(storageBackend),
    startedAtMillis = startedAtMillis,
    monitorEnabled = monitorEnabled,
    enabledRuleCount = activeRuleTotal,
    module = ModuleState(
        installed = moduleInstalled,
        updatePending = moduleUpdatePending,
        enabled = moduleEnabled,
        active = zygiskActive,
        versionName = moduleVersionName,
        versionCode = moduleVersionCode,
    ),
)

internal fun FileEntryParcel.toDomain() = FileEntry(
    name = name,
    subPath = subPath,
    isDirectory = isDirectory,
    size = size,
    lastModified = lastModified,
    childCount = childCount,
)

internal fun FileEventParcel.toDomain() = FileEvent(
    occurredAtMs = occurredAtMs,
    packageName = packageName,
    userId = userId,
    subPath = subPath,
    type = FileEventType.fromWire(type),
)

internal fun MountRecordParcel.toDomain() = MountRecord(
    occurredAtMs = occurredAtMs,
    pid = pid,
    uid = uid,
    packageName = packageName,
    success = success,
    message = message,
    alive = alive,
)

internal fun WatchRuleEntity.toDomain(): WatchRule = WatchRule(
    id = id,
    userId = userId,
    directory = directory,
    kind = WatchKind.entries.firstOrNull { it.name == kind } ?: WatchKind.Everything,
    name = name,
    recursive = recursive,
    enabled = enabled,
    createdAt = createdAt,
)

internal fun WatchRule.toEntity(): WatchRuleEntity = WatchRuleEntity(
    id = id,
    userId = userId,
    directory = directory,
    kind = kind.name,
    name = name,
    recursive = recursive,
    enabled = enabled,
    createdAt = createdAt,
)

internal fun WatchRule.toSpec(): WatchSpec = WatchSpec(
    id = id,
    directory = directory,
    kind = when (kind) {
        WatchKind.Folder -> IpcConstants.WATCH_KIND_FOLDER
        WatchKind.File -> IpcConstants.WATCH_KIND_FILE
        WatchKind.Everything -> IpcConstants.WATCH_KIND_ALL
    },
    name = name,
    recursive = recursive,
    enabled = enabled,
)

internal fun WatchEventParcel.toDomain() = WatchHit(
    ruleId = ruleId,
    occurredAtMs = occurredAtMs,
    subPath = subPath,
    type = FileEventType.fromWire(type),
)
