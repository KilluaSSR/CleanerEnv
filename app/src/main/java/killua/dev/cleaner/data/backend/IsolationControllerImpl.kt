package killua.dev.cleaner.data.backend

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.logging.ArsenalLog
import killua.dev.cleaner.data.toDomain
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.model.BundledModule
import killua.dev.cleaner.domain.model.ModuleInstallPlan
import killua.dev.cleaner.domain.model.ModuleState
import killua.dev.cleaner.domain.model.RootAccess
import killua.dev.cleaner.domain.model.RuntimeState
import killua.dev.cleaner.ipc.ModuleProbe
import killua.dev.cleaner.ipc.RuleFileCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsolationControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val daemonLink: ServerConnection,
    private val shell: RootShell,
) : IsolationController {

    private val refreshTick = MutableStateFlow(0)

    @Volatile
    private var bundled: BundledModule? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRuntime(): Flow<RuntimeState> =
        combine(daemonLink.server, shell.access, refreshTick) { server, access, _ -> server to access }
            .mapLatest { (server, access) ->
                val status = server?.let { daemonLink.call { it.status } }
                status?.toDomain() ?: RuntimeState.Disconnected(access, probeModule(access))
            }

    override fun observeConnected(): Flow<Boolean> = daemonLink.server.map { it != null }.distinctUntilChanged()

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    override suspend fun ensureServer(): Boolean {
        val daemonOnline = daemonLink.ensure() != null
        refresh()
        return daemonOnline
    }

    override suspend fun stopServer() {
        daemonLink.stop()
        refresh()
    }

    override suspend fun restartApp(packageName: String, userId: Int): Boolean {
        if (!RuleFileCodec.isValidPackageName(packageName)) return false
        val launcher = context.packageManager.getLaunchIntentForPackage(packageName)?.component
        val script = buildString {
            appendLine("am force-stop --user $userId '$packageName' || exit 1")
            if (launcher != null) {
                append("am start --user $userId -W -n '${launcher.flattenToString()}' ")
                appendLine("-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f $RELAUNCH_FLAGS 2>&1")
            }
        }
        val result = shell.run(script)
        val started = launcher == null || LAUNCH_ERROR !in result.stdout
        if (!result.isSuccess || !started) ArsenalLog.w(TAG, "restart $packageName exited ${result.exitCode}: ${result.stdout}")
        return result.isSuccess && started
    }

    override suspend fun bundledModule(): BundledModule? = bundled ?: withContext(Dispatchers.IO) {
        runCatching {
            ZipInputStream(context.assets.open(MODULE_ZIP)).use { zip ->
                generateSequence { zip.nextEntry }.firstOrNull { it.name == MODULE_PROP }
                    ?.let { ModuleProbe.parseProperties(zip.readBytes().decodeToString()) }
            }
        }.onFailure { ArsenalLog.e(TAG, "bundled module unreadable", it) }.getOrNull()
            ?.let { props ->
                val code = props["versionCode"]?.toLongOrNull() ?: return@let null
                BundledModule(versionName = props["version"].orEmpty(), versionCode = code)
            }
            ?.also { bundled = it }
    }

    override suspend fun moduleInstallPlan(): ModuleInstallPlan {
        if (!ensureRoot()) return ModuleInstallPlan.Unavailable
        val bundled = bundledModule() ?: return ModuleInstallPlan.Unavailable
        val module = probeModule(RootAccess.Granted)
        return when {
            !module.installed || module.versionCode < bundled.versionCode -> ModuleInstallPlan.Install
            !module.enabled -> ModuleInstallPlan.Enable
            module.updatePending -> ModuleInstallPlan.AwaitingReboot
            else -> ModuleInstallPlan.UpToDate
        }
    }

    override suspend fun installModule(): Boolean {
        if (!ensureRoot()) return false
        val zip = withContext(Dispatchers.IO) { extractModuleZip() } ?: return false
        val result = shell.run(installScript(zip.path), INSTALL_TIMEOUT_MS)
        zip.delete()
        if (!result.isSuccess) ArsenalLog.w(TAG, "module install exited ${result.exitCode}: ${result.stdout}")
        refresh()
        return result.isSuccess
    }

    override suspend fun enableModule(): Boolean {
        if (!ensureRoot()) return false
        val result = shell.run("rm -f ${ModuleProbe.MODULE_DIR}/disable ${ModuleProbe.MODULE_DIR}/remove")
        refresh()
        return result.isSuccess
    }

    private suspend fun ensureRoot(): Boolean =
        shell.access.value == RootAccess.Granted || shell.requestAccess() == RootAccess.Granted

    override suspend fun reboot() {
        shell.run(REBOOT_SCRIPT)
    }

    private suspend fun probeModule(access: RootAccess): ModuleState {
        if (access != RootAccess.Granted) return ModuleState.Unknown
        val result = shell.run(ModuleProbe.SHELL_SCRIPT)
        return if (result.exitCode >= 0) ModuleProbe.parseShellOutput(result.stdout).toDomain() else ModuleState.Unknown
    }

    private fun extractModuleZip(): File? = runCatching {
        val target = File(context.cacheDir, MODULE_ZIP)
        context.assets.open(MODULE_ZIP).use { input -> target.outputStream().use { input.copyTo(it) } }
        target.setReadable(true, false)
        target
    }.onFailure { ArsenalLog.e(TAG, "bundled module zip missing", it) }.getOrNull()

    private fun installScript(zip: String): String = buildString {
        append("if [ -x /data/adb/ksud ]; then /data/adb/ksud module install '$zip'; ")
        append("elif [ -x /data/adb/apd ]; then /data/adb/apd module install '$zip'; ")
        append("elif command -v magisk >/dev/null 2>&1; then magisk --install-module '$zip'; ")
        append("else exit 127; fi")
    }

    private companion object {
        const val TAG = "IsolationController"
        const val MODULE_ZIP = "cleaner_isolation.zip"
        const val MODULE_PROP = "module.prop"
        const val INSTALL_TIMEOUT_MS = 120_000L
        const val REBOOT_SCRIPT = "svc power reboot || reboot"
        const val LAUNCH_ERROR = "Error:"

        /** FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, as a launcher sends. */
        const val RELAUNCH_FLAGS = "0x10200000"
    }
}
