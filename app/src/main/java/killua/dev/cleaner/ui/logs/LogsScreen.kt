package killua.dev.cleaner.ui.logs

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import killua.dev.arsenal.material.templates.logger.ArsenalLogViewer
import killua.dev.arsenal.material.templates.logger.LogViewerStrings
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.cleaner.R

@NavDestination(route = "logs")
@Composable
fun LogsScreen(navController: NavHostController, backStackEntry: NavBackStackEntry) {
    ArsenalLogViewer(
        strings = rememberLogViewerStrings(),
        onBack = { navController.navigateUp() },
    )
}

@Composable
private fun rememberLogViewerStrings(): LogViewerStrings {
    val context = LocalContext.current
    return remember(context) { context.logViewerStrings() }
}

private fun Context.logViewerStrings() = LogViewerStrings(
    title = getString(R.string.logs_title),
    searchPlaceholder = getString(R.string.logs_search_hint),
    searchContentDescription = getString(R.string.logs_cd_search),
    searchClearContentDescription = getString(R.string.logs_cd_search_clear),
    sourceLabels = mapOf("arsenal" to getString(R.string.logs_source_arsenal), "logcat" to getString(R.string.logs_source_logcat)),
    countLabel = { filtered, total -> getString(R.string.logs_count, filtered, total) },
    tagFilterLabel = { tag -> getString(R.string.logs_tag_filter, tag) },
    pausedLabel = getString(R.string.logs_paused),
    pauseContentDescription = getString(R.string.logs_cd_pause),
    resumeContentDescription = getString(R.string.logs_cd_resume),
    jumpToBottomContentDescription = getString(R.string.logs_cd_jump_bottom),
    clearContentDescription = getString(R.string.logs_cd_clear),
    exportContentDescription = getString(R.string.logs_cd_export),
    copyContentDescription = getString(R.string.logs_cd_copy),
    filterByTagContentDescription = getString(R.string.logs_cd_filter_tag),
    copiedMessage = getString(R.string.logs_copied),
    exportedMessage = getString(R.string.logs_exported),
    exportFailedMessage = getString(R.string.logs_export_failed),
    clearedMessage = getString(R.string.logs_cleared),
    emptyTitle = getString(R.string.logs_empty_title),
    emptyMessage = getString(R.string.logs_empty_message),
    permissionTitle = getString(R.string.logs_permission_title),
    permissionHint = { pkg -> getString(R.string.logs_permission_hint, pkg) },
    copyCommandContentDescription = getString(R.string.logs_cd_copy_command),
)
