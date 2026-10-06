package killua.dev.cleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import killua.dev.arsenal.material.colors.ArsenalTheme
import killua.dev.arsenal.material.colors.ColorSource
import killua.dev.cleaner.R
import killua.dev.cleaner.data.settings.ThemeMode

@Composable
fun CleanerTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val seed = colorResource(R.color.brand_seed)
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val source = if (dynamicColor) ColorSource.Wallpaper(fallbackSeed = seed) else ColorSource.Seed(seed = seed)
    ArsenalTheme(colorSource = source, darkTheme = darkTheme, content = content)
}
