package killua.dev.cleaner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import killua.dev.arsenal.material.components.application.AppIcon as ArsenalAppIcon
import killua.dev.arsenal.material.components.application.rememberAppLabel as arsenalRememberAppLabel

/**
 * Icon + label helpers now come from the Arsenal `components.application` framework;
 * these thin aliases keep existing call sites unchanged.
 */
@Composable
fun rememberAppLabel(packageName: String): String = arsenalRememberAppLabel(packageName)

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    ArsenalAppIcon(packageName = packageName, modifier = modifier, size = size)
}
