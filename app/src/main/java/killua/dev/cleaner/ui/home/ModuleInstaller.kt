package killua.dev.cleaner.ui.home

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.di.ApplicationScope
import killua.dev.arsenal.core.overlay.OverlayController
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.model.ModuleInstallPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ModuleInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val controller: IsolationController,
    private val overlay: OverlayController,
) {
    suspend fun installIfNeeded() {
        when (controller.moduleInstallPlan()) {
            ModuleInstallPlan.Install -> {
                overlay.snackbar(context.getString(R.string.module_installing), duration = SnackbarDuration.Short)
                report(controller.installModule(), R.string.module_install_success, R.string.module_install_failed)
            }
            ModuleInstallPlan.Enable ->
                report(controller.enableModule(), R.string.module_enabled_reboot, R.string.module_enable_failed)
            ModuleInstallPlan.AwaitingReboot -> offerReboot(R.string.module_awaiting_reboot)
            ModuleInstallPlan.UpToDate -> overlay.snackbar(
                message = context.getString(R.string.module_up_to_date),
                duration = SnackbarDuration.Short,
            )
            ModuleInstallPlan.Unavailable -> overlay.snackbar(
                message = context.getString(R.string.module_install_unavailable),
                duration = SnackbarDuration.Short,
            )
        }
    }

    fun confirmReboot() = overlay.dialog(
        title = context.getString(R.string.reboot_confirm_title),
        message = context.getString(R.string.reboot_confirm_message),
        confirmLabel = context.getString(R.string.action_reboot),
        dismissLabel = context.getString(R.string.action_cancel),
        onConfirm = { scope.launch { controller.reboot() } },
    )

    private fun report(success: Boolean, successMessage: Int, failureMessage: Int) {
        if (success) {
            offerReboot(successMessage)
        } else {
            overlay.snackbar(context.getString(failureMessage), duration = SnackbarDuration.Short)
        }
    }

    private fun offerReboot(message: Int) = overlay.snackbar(
        message = context.getString(message),
        actionLabel = context.getString(R.string.action_reboot),
        duration = SnackbarDuration.Short,
        onAction = ::confirmReboot,
    )
}
