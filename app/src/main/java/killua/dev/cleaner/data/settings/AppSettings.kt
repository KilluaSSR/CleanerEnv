package killua.dev.cleaner.data.settings

import killua.dev.arsenal.data.settings.boolSetting
import killua.dev.arsenal.data.settings.enumSetting
import killua.dev.cleaner.domain.model.AppScope
import killua.dev.cleaner.domain.model.StorageLocation

enum class ThemeMode { System, Light, Dark }

object AppSettings {
    val themeMode = enumSetting("theme_mode", ThemeMode.System)
    val dynamicColor = boolSetting("dynamic_color", default = true)

    val defaultLocation = enumSetting("default_location", StorageLocation.DataFolder)

    val fileMonitor = boolSetting("file_monitor", default = false)

    val autoStartServer = boolSetting("auto_start_server", default = false)

    val rulesScope = enumSetting("rules_scope", AppScope.UserOnly)
    val rulesShowDisabled = boolSetting("rules_show_disabled", default = false)
    val rulesOnlyStorage = boolSetting("rules_only_storage", default = false)
}
