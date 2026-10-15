package killua.dev.cleaner.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import killua.dev.arsenal.material.components.application.AppHeader as ArsenalAppHeader

/** App detail header — delegates to the framework's [ArsenalAppHeader]. */
@Composable
fun AppHeader(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {},
) {
    ArsenalAppHeader(packageName = packageName, label = label, modifier = modifier, details = details)
}
