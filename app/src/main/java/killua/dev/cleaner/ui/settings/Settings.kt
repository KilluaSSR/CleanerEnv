package killua.dev.cleaner.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.core.di.ApplicationScope
import killua.dev.arsenal.core.gate.Gate
import killua.dev.arsenal.data.settings.SettingsStore
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.ArsenalSettingsScreen
import killua.dev.arsenal.material.templates.settings.SettingsAccent
import killua.dev.arsenal.material.templates.settings.SettingsEntry
import killua.dev.arsenal.material.templates.settings.SettingsItemModel
import killua.dev.arsenal.navigation.Navigator
import killua.dev.arsenal.navigation.generated.GeneratedRoutes
import killua.dev.arsenal.spi.annotation.SpiImpl
import killua.dev.cleaner.BuildConfig
import killua.dev.cleaner.R
import killua.dev.cleaner.data.settings.AppSettings
import killua.dev.cleaner.data.settings.ThemeMode
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.model.StorageLocation
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.components.TabScaffold
import killua.dev.cleaner.ui.components.labelRes
import killua.dev.cleaner.ui.home.ModuleInstaller
import killua.dev.cleaner.utils.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SCREEN = "settings"
private const val GITHUB_REPO_URL = "https://github.com/KilluaSSR/Cleaner"
private const val GITHUB_ISSUES_URL = "https://github.com/KilluaSSR/Cleaner/issues"
@HiltViewModel
class SettingsViewModel @Inject constructor(val store: SettingsStore) : ViewModel()

@Composable
fun SettingsTab() {
    val viewModel: SettingsViewModel = hiltViewModel()
    TabScaffold(title = stringResource(R.string.settings_title)) { padding ->
        ArsenalSettingsScreen(
            screen = SCREEN,
            store = viewModel.store,
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
            itemContainerColor = CleanerSurfaces.row,
        )
    }
}

@SpiImpl
class ThemeSettingEntry @Inject constructor(@ApplicationContext private val context: Context) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_appearance)
    override val order = 0
    override fun model() = SettingsItemModel.Panel(
        definition = AppSettings.themeMode,
        title = context.getString(R.string.settings_theme),
        options = ThemeMode.entries,
        optionLabel = { context.getString(it.labelRes()) },
        icon = Icons.Rounded.Palette,
        accent = SettingsAccent.Primary,
    )
}

@SpiImpl
class DynamicColorSettingEntry @Inject constructor(@ApplicationContext private val context: Context) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_appearance)
    override val order = 1
    override fun model() = SettingsItemModel.Switch(
        definition = AppSettings.dynamicColor,
        title = context.getString(R.string.settings_dynamic_color),
        description = context.getString(R.string.settings_dynamic_color_description),
        icon = Icons.Rounded.Wallpaper,
        accent = SettingsAccent.Tertiary,
        gate = Gate { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    )
}

@SpiImpl
class DefaultLocationSettingEntry @Inject constructor(@ApplicationContext private val context: Context) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_isolation)
    override val order = 10
    override fun model() = SettingsItemModel.Panel(
        definition = AppSettings.defaultLocation,
        title = context.getString(R.string.settings_default_location),
        options = StorageLocation.entries,
        optionLabel = { context.getString(it.labelRes()) },
        icon = Icons.Rounded.SdStorage,
        accent = SettingsAccent.Secondary,
    )
}

@SpiImpl
class FileMonitorSettingEntry @Inject constructor(@ApplicationContext private val context: Context) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_isolation)
    override val order = 11
    override fun model() = SettingsItemModel.Switch(
        definition = AppSettings.fileMonitor,
        title = context.getString(R.string.settings_file_monitor),
        description = context.getString(R.string.settings_file_monitor_description),
        icon = Icons.AutoMirrored.Rounded.ManageSearch,
        accent = SettingsAccent.Tertiary,
    )
}

@SpiImpl
class AutoStartSettingEntry @Inject constructor(@ApplicationContext private val context: Context) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_isolation)
    override val order = 12
    override fun model() = SettingsItemModel.Switch(
        definition = AppSettings.autoStartServer,
        title = context.getString(R.string.settings_auto_start),
        description = context.getString(R.string.settings_auto_start_description),
        icon = Icons.Rounded.PowerSettingsNew,
        accent = SettingsAccent.Primary,
    )
}

@SpiImpl
class InstallModuleSettingEntry @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val installer: ModuleInstaller,
) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_module)
    override val order = 20
    override fun model() = SettingsItemModel.Navigation(
        title = context.getString(R.string.settings_install_module),
        description = context.getString(R.string.settings_install_module_description),
        icon = Icons.Rounded.Extension,
        accent = SettingsAccent.Secondary,
        onClick = { scope.launch { installer.installIfNeeded() } },
    )
}

@SpiImpl
class StopServiceSettingEntry @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val controller: IsolationController,
) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_module)
    override val order = 21
    override fun model() = SettingsItemModel.Navigation(
        title = context.getString(R.string.settings_stop_service),
        description = context.getString(R.string.settings_stop_service_description),
        icon = Icons.Rounded.StopCircle,
        accent = SettingsAccent.Error,
        onClick = { scope.launch { controller.stopServer() } },
    )
}

@SpiImpl
class VersionSettingEntry @Inject constructor(@ApplicationContext private val context: Context) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_about)
    override val order = 30
    override fun model() = SettingsItemModel.Info(
        title = context.getString(R.string.settings_version),
        value = BuildConfig.VERSION_NAME,
        icon = Icons.Rounded.Info,
        accent = SettingsAccent.Neutral,
    )
}

@SpiImpl
class HelpSettingEntry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navigator: Navigator,
) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_about)
    override val order = 31
    override fun model() = SettingsItemModel.Navigation(
        title = context.getString(R.string.settings_help),
        icon = Icons.AutoMirrored.Rounded.HelpOutline,
        accent = SettingsAccent.Tertiary,
        onClick = { navigator.navigate(GeneratedRoutes.HELP_SCREEN) },
    )
}

@SpiImpl
class FeedbackSettingEntry @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_about)
    override val order = 32
    override fun model() = SettingsItemModel.Navigation(
        title = context.getString(R.string.settings_group_feedback),
        icon = Icons.Default.Feedback,
        accent = SettingsAccent.Primary,
        onClick = { openUrl(context, GITHUB_ISSUES_URL) },
    )
}


@SpiImpl
class SourcecodeSettingEntry @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsEntry {
    override val screen = SCREEN
    override val group: String = context.getString(R.string.settings_group_about)
    override val order = 33
    override fun model() = SettingsItemModel.Navigation(
        title = context.getString(R.string.settings_group_sourcecode),
        icon = Icons.Default.Code,
        accent = SettingsAccent.Tertiary,
        onClick = { openUrl(context, GITHUB_REPO_URL) },
    )
}