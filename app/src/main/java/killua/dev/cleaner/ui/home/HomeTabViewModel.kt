package killua.dev.cleaner.ui.home

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.overlay.OverlayController
import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.arsenal.download.DownloadTask
import killua.dev.arsenal.download.UNKNOWN_PROGRESS
import killua.dev.arsenal.download.isTerminal
import killua.dev.arsenal.navigation.Navigator
import killua.dev.arsenal.navigation.generated.GeneratedRoutes
import killua.dev.arsenal.network.connectivity.NetworkMonitor
import killua.dev.arsenal.update.AppUpdater
import killua.dev.arsenal.update.UpdateChecker
import killua.dev.arsenal.update.UpdateDownloadState
import killua.dev.arsenal.update.UpdateState
import killua.dev.arsenal.update.currentAppVersion
import killua.dev.cleaner.R
import killua.dev.cleaner.data.settings.AppSettings
import killua.dev.cleaner.data.updater.GithubReleaseVersionSource
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.FileHistoryRepository
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.WatchRepository
import killua.dev.cleaner.domain.model.BundledModule
import killua.dev.cleaner.domain.model.ModuleState
import killua.dev.cleaner.domain.model.MountRecord
import killua.dev.cleaner.domain.model.RootAccess
import killua.dev.cleaner.domain.model.RuntimeState
import killua.dev.cleaner.domain.model.WatchHit
import killua.dev.cleaner.domain.model.WatchRule
import killua.dev.cleaner.ui.watch.NEW_WATCH
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ModuleStatus {
    data object Checking : ModuleStatus
    data object NotConnected : ModuleStatus
    data object RootDenied : ModuleStatus
    data object ModuleMissing : ModuleStatus
    data object ModulePending : ModuleStatus
    data class ModuleOutdated(val installed: ModuleState, val bundled: BundledModule) : ModuleStatus
    data object ModuleDisabled : ModuleStatus
    data object ModuleInactive : ModuleStatus
    data class Active(val module: ModuleState, val isolatedApps: Int?) : ModuleStatus
}

data class HomeState(
    val runtime: RuntimeState? = null,
    val status: ModuleStatus = ModuleStatus.Checking,
    val mounts: List<MountRecord> = emptyList(),
    val monitorEnabled: Boolean = false,
    val watches: List<WatchRule> = emptyList(),
    val watchLatest: Map<String, WatchHit> = emptyMap(),
    val watchPending: Set<String> = emptySet(),
    val busy: Boolean = false,
    val appVersion: String = "",
    val update: UpdateState = UpdateState.Idle,
    val download: UpdateDownloadState = UpdateDownloadState.Idle,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Connect : HomeIntent
    data object InstallModule : HomeIntent
    data object Reboot : HomeIntent
    data object StopServer : HomeIntent
    data object OpenMonitor : HomeIntent
    data object OpenMounts : HomeIntent
    data object AddWatch : HomeIntent
    data object ManageWatches : HomeIntent
    data class OpenWatch(val id: String) : HomeIntent
    data object Refresh : HomeIntent
    data object UpdateApp : HomeIntent
}

sealed interface HomeEffect : UiEffect

@HiltViewModel
class HomeTabViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @CurrentUser private val userId: Int,
    private val controller: IsolationController,
    private val history: FileHistoryRepository,
    private val watches: WatchRepository,
    private val navigator: Navigator,
    private val overlay: OverlayController,
    private val installer: ModuleInstaller,
    private val networkMonitor: NetworkMonitor,
    private val updateChecker: UpdateChecker,
    private val appUpdater: AppUpdater,
    private val versionSource: GithubReleaseVersionSource,
    settings: SettingsStore,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var updateCheck: Job? = null
    private var updateDownload: DownloadTask? = null

    init {
        setState { copy(appVersion = context.currentAppVersion().versionName) }
        updateChecker.state.launchInScope { update -> setState { copy(update = update) } }
        appUpdater.downloadState.launchInScope { download ->
            setState { copy(download = download) }
            if (download is UpdateDownloadState.Failed) {
                appUpdater.reset()
                overlay.snackbar(context.getString(R.string.update_download_failed), duration = SnackbarDuration.Short)
            }
        }

        if (updateChecker.state.value is UpdateState.Idle) {
            updateCheck = viewModelScope.launch {
                networkMonitor.status.first { it.isConnected }
                updateChecker.check(versionSource)
            }
        }
        controller.observeRuntime().launchInScope { runtime ->
            val mounts = if (runtime is RuntimeState.Connected) history.mountRecords(MOUNT_SUMMARY_LIMIT) else emptyList()
            val status = runtime.toModuleStatus(controller.bundledModule())
            setState { copy(runtime = runtime, status = status, mounts = mounts) }
            refreshWatchStatus()
        }
        settings.flow(AppSettings.fileMonitor).launchInScope { enabled -> setState { copy(monitorEnabled = enabled) } }
        watches.observeRules(userId).launchInScope { rules ->
            setState { copy(watches = rules) }
            refreshWatchStatus()
        }
        dispatch(HomeIntent.Connect)
    }

    private suspend fun refreshWatchStatus() {
        val latest = watches.latestEvents(userId)
        val pending = watches.pending(userId)
        setState { copy(watchLatest = latest, watchPending = pending) }
    }

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Connect -> busy {
                if (!controller.ensureServer()) {
                    val runtime = controller.observeRuntime().first()
                    if (runtime is RuntimeState.Disconnected && runtime.root == RootAccess.Granted) {
                        overlay.snackbar(context.getString(R.string.service_start_failed))
                    }
                }
            }
            HomeIntent.InstallModule -> busy { installer.installIfNeeded() }
            HomeIntent.Reboot -> installer.confirmReboot()
            HomeIntent.StopServer -> busy { controller.stopServer() }
            HomeIntent.OpenMonitor -> navigator.navigate(GeneratedRoutes.FILE_MONITOR_SCREEN)
            HomeIntent.OpenMounts -> navigator.navigate(GeneratedRoutes.MOUNT_RECORDS_SCREEN)
            HomeIntent.AddWatch -> navigator.navigate(GeneratedRoutes.watchEditorScreen(NEW_WATCH))
            HomeIntent.ManageWatches -> navigator.navigate(GeneratedRoutes.WATCH_RULES_SCREEN)
            is HomeIntent.OpenWatch -> navigator.navigate(GeneratedRoutes.watchLogScreen(intent.id))
            HomeIntent.Refresh -> {
                controller.refresh()
                refreshWatchStatus()
            }
            HomeIntent.UpdateApp -> advanceUpdate()
        }
    }

    private fun advanceUpdate() {
        val download = appUpdater.downloadState.value
        val update = updateChecker.state.value
        when {
            download is UpdateDownloadState.Downloading || updateDownload?.currentState?.isTerminal == false -> Unit
            download is UpdateDownloadState.Downloaded -> appUpdater.install(download.apk)
            update is UpdateState.Available -> {
                updateDownload = appUpdater.downloadAndInstall(update.info)
                setState { copy(download = UpdateDownloadState.Downloading(UNKNOWN_PROGRESS, 0L, -1L)) }
            }
            update is UpdateState.Checking -> Unit
            else -> {
                updateCheck?.cancel()
                updateCheck = viewModelScope.launch {
                    val message = when (updateChecker.check(versionSource)) {
                        is UpdateState.UpToDate -> R.string.update_up_to_date
                        is UpdateState.Error -> R.string.update_check_failed
                        else -> return@launch
                    }
                    overlay.snackbar(context.getString(message), withDismissAction = true, duration = SnackbarDuration.Short)
                }
            }
        }
    }

    private suspend fun busy(block: suspend () -> Unit) {
        setState { copy(busy = true) }
        try {
            block()
        } finally {
            setState { copy(busy = false) }
        }
    }

    private companion object {
        const val MOUNT_SUMMARY_LIMIT = 100
    }
}

internal fun RuntimeState.toModuleStatus(bundled: BundledModule? = null): ModuleStatus {
    val isolated = (this as? RuntimeState.Connected)?.enabledRuleCount
    if (this is RuntimeState.Disconnected) {
        when (root) {
            RootAccess.Unknown -> return ModuleStatus.NotConnected
            RootAccess.Denied -> return ModuleStatus.RootDenied
            RootAccess.Granted -> Unit
        }
    }
    return when {
        !module.installed -> ModuleStatus.ModuleMissing
        module.updatePending -> ModuleStatus.ModulePending
        bundled != null && module.versionCode < bundled.versionCode -> ModuleStatus.ModuleOutdated(module, bundled)
        !module.enabled -> ModuleStatus.ModuleDisabled
        !module.active -> ModuleStatus.ModuleInactive
        else -> ModuleStatus.Active(module, isolated)
    }
}
