package killua.dev.cleaner.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import killua.dev.arsenal.material.ArsenalTokens
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition
import killua.dev.cleaner.R
import killua.dev.cleaner.ui.components.AppDetailScaffold
import killua.dev.cleaner.ui.components.AppHeader
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.arsenal.material.templates.basics.EmptyView
import killua.dev.cleaner.ui.components.Pill
import killua.dev.arsenal.material.templates.basics.stateItem

data class EditorItem(val key: String, val title: String, val subtitle: String? = null)

@Composable
fun RuleListEditor(
    title: String,
    packageName: String,
    label: String,
    description: String,
    items: List<EditorItem>,
    emptyText: String,
    itemIcon: ImageVector,
    addLabel: String,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    header: LazyListScope.() -> Unit = {},
) {
    AppDetailScaffold(
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(addLabel) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = onAdd,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = ArsenalTokens.spacing.md, end = ArsenalTokens.spacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(ArsenalTokens.spacing.xs),
        ) {
            item(key = "header") {
                AppHeader(packageName = packageName, label = label, modifier = Modifier.padding(bottom = ArsenalTokens.spacing.sm)) {
                    Pill(text = title, modifier = Modifier.padding(top = ArsenalTokens.spacing.xs))
                }
            }
            item(key = "description") {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ArsenalTokens.spacing.sm, vertical = ArsenalTokens.spacing.sm),
                )
            }
            header()
            if (items.isEmpty()) {
                stateItem {
                    EmptyView(
                        icon = itemIcon,
                        title = emptyText,
                        message = stringResource(R.string.editor_empty_hint, addLabel),
                        modifier = it.animateItem(),
                    )
                }
            }
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                Surface(
                    shape = groupedItemShape(itemPosition(index, items.size)),
                    color = CleanerSurfaces.row,
                    modifier = Modifier.animateItem(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(start = ArsenalTokens.spacing.lg, end = ArsenalTokens.spacing.sm, top = ArsenalTokens.spacing.sm, bottom = ArsenalTokens.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ArsenalTokens.spacing.md),
                    ) {
                        Icon(itemIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            item.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onRemove(item.key) }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = stringResource(R.string.action_remove))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(
    title: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = ArsenalTokens.spacing.lg)
                .padding(bottom = ArsenalTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(ArsenalTokens.spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ArsenalTokens.spacing.sm, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = onConfirm, enabled = confirmEnabled) { Text(stringResource(R.string.action_add)) }
            }
        }
    }
}
