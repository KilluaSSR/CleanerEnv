package killua.dev.cleaner

import killua.dev.cleaner.data.toParcel
import killua.dev.cleaner.domain.model.AccessibleFolder
import killua.dev.cleaner.domain.model.AppEntry
import killua.dev.cleaner.domain.model.AppFilter
import killua.dev.cleaner.domain.model.AppRule
import killua.dev.cleaner.domain.model.AppScope
import killua.dev.cleaner.domain.model.BundledModule
import killua.dev.cleaner.domain.model.ExportRule
import killua.dev.cleaner.domain.model.ModuleState
import killua.dev.cleaner.domain.model.RootAccess
import killua.dev.cleaner.domain.model.RuntimeState
import killua.dev.cleaner.domain.model.SharedFolder
import killua.dev.cleaner.domain.model.StorageAccess
import killua.dev.cleaner.domain.model.StorageBackend
import killua.dev.cleaner.domain.model.StorageLocation
import killua.dev.cleaner.domain.usecase.FilterApps
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ui.home.ModuleStatus
import killua.dev.cleaner.ui.home.toModuleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainLogicTest {

    private val activeModule = ModuleState(
        installed = true,
        updatePending = false,
        enabled = true,
        active = true,
        versionName = "1.0",
        versionCode = 1,
    )

    @Test
    fun `module status reports the first blocker`() {
        assertEquals(ModuleStatus.NotConnected, RuntimeState.Disconnected(RootAccess.Unknown, ModuleState.Unknown).toModuleStatus())
        assertEquals(ModuleStatus.RootDenied, RuntimeState.Disconnected(RootAccess.Denied, activeModule).toModuleStatus())
        assertEquals(ModuleStatus.ModuleMissing, RuntimeState.Disconnected(RootAccess.Granted, ModuleState.Unknown).toModuleStatus())
        assertEquals(
            ModuleStatus.ModulePending,
            RuntimeState.Disconnected(RootAccess.Granted, activeModule.copy(updatePending = true)).toModuleStatus(),
        )
        assertEquals(
            ModuleStatus.ModuleDisabled,
            RuntimeState.Disconnected(RootAccess.Granted, activeModule.copy(enabled = false)).toModuleStatus(),
        )
        assertEquals(
            ModuleStatus.ModuleInactive,
            RuntimeState.Disconnected(RootAccess.Granted, activeModule.copy(active = false)).toModuleStatus(),
        )
        assertEquals(
            ModuleStatus.Active(activeModule, null),
            RuntimeState.Disconnected(RootAccess.Granted, activeModule).toModuleStatus(),
        )
    }

    @Test
    fun `module status asks for an update only when the bundled build is newer`() {
        val granted = RuntimeState.Disconnected(RootAccess.Granted, activeModule)
        val newer = BundledModule(versionName = "v1.1.0", versionCode = 2)
        assertEquals(ModuleStatus.ModuleOutdated(activeModule, newer), granted.toModuleStatus(newer))
        assertEquals(ModuleStatus.Active(activeModule, null), granted.toModuleStatus(BundledModule("v1.0", 1)))
        assertEquals(
            ModuleStatus.ModulePending,
            RuntimeState.Disconnected(RootAccess.Granted, activeModule.copy(updatePending = true)).toModuleStatus(newer),
        )
    }

    @Test
    fun `daemonOnline status carries the isolated app count`() {
        val daemonOnline = RuntimeState.Connected(
            daemonRevision = 1,
            serverPid = 42,
            sdkInt = 35,
            backend = StorageBackend.Fuse,
            startedAtMillis = 0,
            monitorEnabled = false,
            enabledRuleCount = 3,
            module = activeModule,
        )
        assertEquals(ModuleStatus.Active(activeModule, 3), daemonOnline.toModuleStatus())
    }

    private fun app(
        packageName: String,
        label: String,
        system: Boolean = false,
        enabled: Boolean = true,
        access: StorageAccess = StorageAccess.ReadWrite,
        isolated: Boolean = false,
    ) = AppEntry(
        packageName = packageName,
        userId = 0,
        label = label,
        versionName = "1",
        versionCode = 1,
        targetSdk = 34,
        isSystemApp = system,
        isEnabled = enabled,
        storageAccess = access,
        isolationEnabled = isolated,
    )

    @Test
    fun `filter applies chips, menu toggles, search and sorting`() {
        val apps = listOf(
            app("com.b", "Beta"),
            app("com.a", "Alpha", isolated = true),
            app("com.sys", "System", system = true),
            app("com.off", "Disabled", enabled = false),
            app("com.none", "NoStorage", access = StorageAccess.None),
        )
        val filter = FilterApps()

        assertEquals(
            listOf("com.a", "com.b", "com.none"),
            filter(apps, AppFilter(scope = AppScope.UserOnly)).map { it.packageName },
        )
        assertEquals(
            listOf("com.a", "com.b", "com.off", "com.sys"),
            filter(apps, AppFilter(scope = AppScope.All, showDisabled = true, onlyStorageApps = true)).map { it.packageName },
        )
        assertEquals(listOf("com.b"), filter(apps, AppFilter(query = "bet")).map { it.packageName })
    }

    @Test
    fun `wire projection resolves default location and drops invalid paths`() {
        val rule = AppRule(
            packageName = "com.example",
            userId = 0,
            enabled = true,
            location = null,
            accessibleFolders = listOf(AccessibleFolder("Download/"), AccessibleFolder("../etc")),
            exports = listOf(ExportRule("Pictures/Out", "Pictures/App"), ExportRule("a/../b", "c")),
            sharedFolders = listOf(SharedFolder("com.other", "Shared"), SharedFolder("bad name", "x")),
        )

        val parcel = rule.toParcel(StorageLocation.CacheFolder)

        assertEquals(IpcConstants.LOCATION_CACHE, parcel.location)
        assertEquals(listOf("Download"), parcel.accessibleFolders)
        assertEquals(listOf("Pictures/Out"), parcel.exportSources)
        assertEquals(listOf("Pictures/App"), parcel.exportTargets)
        assertEquals(listOf("com.other"), parcel.sharedPackages)
        assertEquals(listOf("Shared"), parcel.sharedPaths)
        assertEquals(IpcConstants.LOCATION_DATA, rule.copy(location = StorageLocation.DataFolder).toParcel(StorageLocation.CacheFolder).location)
    }
}
