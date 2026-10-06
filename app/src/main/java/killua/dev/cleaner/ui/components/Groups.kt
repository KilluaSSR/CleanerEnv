package killua.dev.cleaner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.settings.SettingsGroup
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition

object CleanerSurfaces {
    val row: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceBright
}

@Composable
fun GroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Immutable
data class RowSlot(val shape: Shape, val color: Color)


@Composable
fun RowGroup(
    rows: List<@Composable (RowSlot) -> Unit>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    if (rows.isEmpty()) return
    val color = CleanerSurfaces.row
    SettingsGroup(title = title, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            rows.forEachIndexed { index, row ->
                row(RowSlot(groupedItemShape(itemPosition(index, rows.size)), color))
            }
        }
    }
}
