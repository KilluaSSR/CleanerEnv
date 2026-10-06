package killua.dev.cleaner.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import killua.dev.arsenal.material.Spacing
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.model.StorageLocation
import killua.dev.cleaner.ui.components.descriptionRes
import killua.dev.cleaner.ui.components.labelRes
import killua.dev.cleaner.ui.components.locationLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSheet(
    current: StorageLocation?,
    default: StorageLocation,
    onSelect: (StorageLocation?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.md).selectableGroup()) {
            Text(
                text = stringResource(R.string.location_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            LocationOption(
                title = locationLabel(null, default),
                description = stringResource(default.descriptionRes()),
                selected = current == null,
                onClick = { onSelect(null) },
            )
            StorageLocation.entries.forEach { location ->
                LocationOption(
                    title = stringResource(location.labelRes()),
                    description = stringResource(location.descriptionRes()),
                    selected = current == location,
                    onClick = { onSelect(location) },
                )
            }
        }
    }
}

@Composable
private fun LocationOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
