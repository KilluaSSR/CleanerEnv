package killua.dev.cleaner.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import killua.dev.arsenal.material.components.application.AppPill

/** Capsule label — delegates to the framework's [AppPill]. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leading: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    AppPill(
        text = text,
        modifier = modifier,
        container = container,
        content = content,
        leading = leading,
        onClick = onClick,
    )
}
