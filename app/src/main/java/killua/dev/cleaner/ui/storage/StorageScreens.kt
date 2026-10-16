package killua.dev.cleaner.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.components.loadingindicator.ArsenalLinearWavyProgressIndicator
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.cleaner.R
import killua.dev.cleaner.domain.model.FileEntry
import killua.dev.cleaner.ui.components.AppDetailScaffold
import killua.dev.cleaner.ui.components.AppHeader
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.components.DetailScaffold
import killua.dev.arsenal.material.templates.basics.EmptyView
import killua.dev.arsenal.material.templates.basics.LoadingView
import killua.dev.cleaner.ui.components.Pill
import killua.dev.cleaner.ui.components.ServerRequiredView
import killua.dev.cleaner.ui.components.rememberAppLabel
import killua.dev.cleaner.ui.components.rememberFormattedSize
import killua.dev.cleaner.ui.components.rememberFormattedTime
import killua.dev.arsenal.material.templates.basics.stateItem

@Composable
private fun StorageBody(
    state: StorageState,
    padding: PaddingValues,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyText: String,
    content: @Composable (List<FileEntry>) -> Unit,
) {
    val entries = state.entries
    val data = (entries as? DataState.Success)?.data ?: (entries as? DataState.Loading)?.dataOrNull
    if (state.daemonOnline && data != null) {
        content(data)
        return
    }
    Box(Modifier.padding(padding)) {
        when {
            !state.daemonOnline -> ServerRequiredView()
            entries is DataState.Loading -> LoadingView()
            else -> EmptyView(icon = emptyIcon, title = emptyText)
        }
    }
}

@NavDestination(route = "isolated_browser/{packageName}")
@Composable
fun IsolatedBrowserScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: IsolatedBrowserViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.path.isNotEmpty()) { viewModel.dispatch(StorageIntent.Up) }

    DetailScaffold(
        title = state.path.lastOrNull() ?: stringResource(R.string.browser_title),
        subtitle = if (state.path.isEmpty()) {
            rememberAppLabel(state.packageName)
        } else {
            stringResource(R.string.browser_path, state.subPath)
        },
        onBack = { viewModel.dispatch(StorageIntent.Up) },
    ) { padding ->
        StorageBody(state, padding, Icons.Rounded.FolderOff, stringResource(R.string.browser_empty)) { entries ->
            FileList(entries, padding + PaddingValues(Spacing.md)) { viewModel.dispatch(StorageIntent.Enter(it)) }
        }
    }
}

@Composable
private fun FileList(entries: List<FileEntry>, contentPadding: PaddingValues, onOpen: (FileEntry) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.subPath }) { index, entry ->
            Surface(
                shape = groupedItemShape(itemPosition(index, entries.size)),
                color = CleanerSurfaces.row,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = entry.isDirectory) { onOpen(entry) }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            text = stringResource(
                                R.string.browser_file_summary,
                                if (entry.isDirectory) {
                                    pluralStringResource(R.plurals.browser_children, entry.childCount, entry.childCount)
                                } else {
                                    rememberFormattedSize(entry.size)
                                },
                                rememberFormattedTime(entry.lastModified),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@NavDestination(route = "gallery/{packageName}")
@Composable
fun GalleryScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: GalleryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DetailScaffold(
        title = stringResource(R.string.gallery_title),
        subtitle = rememberAppLabel(state.packageName),
        onBack = { viewModel.dispatch(StorageIntent.Back) },
    ) { padding ->
        Box {
            StorageBody(state, padding, Icons.Rounded.HideImage, stringResource(R.string.gallery_empty)) { media ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding + PaddingValues(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(media, key = { it.subPath }) { entry -> Thumbnail(entry, viewModel) }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(entry: FileEntry, viewModel: StorageViewModel) {
    val sizePx = with(LocalDensity.current) { 160.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, entry.subPath) {
        value = viewModel.thumbnail(entry.subPath, sizePx)?.asImageBitmap()
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.aspectRatio(1f),
    ) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@NavDestination(route = "folder_analysis/{packageName}")
@Composable
fun FolderAnalysisScreen(backStackEntry: NavBackStackEntry) {
    val viewModel: FolderAnalysisViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entries = state.entries
    val data = (entries as? DataState.Success)?.data ?: (entries as? DataState.Loading)?.dataOrNull
    val total = state.totalSize.coerceAtLeast(1L)

    AppDetailScaffold(onBack = { viewModel.dispatch(StorageIntent.Back) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "header") {
                AppHeader(
                    packageName = state.packageName,
                    label = rememberAppLabel(state.packageName),
                    modifier = Modifier.padding(bottom = Spacing.md),
                ) {
                    Pill(text = stringResource(R.string.analysis_title), modifier = Modifier.padding(top = Spacing.xs))
                    if (entries is DataState.Success) {
                        Text(
                            text = stringResource(R.string.analysis_total, rememberFormattedSize(state.totalSize)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when {
                !state.daemonOnline -> stateItem { ServerRequiredView(it) }
                data != null -> itemsIndexed(data, key = { _, entry -> entry.subPath }) { index, entry ->
                    AnalysisRow(entry, total, groupedItemShape(itemPosition(index, data.size)))
                }
                entries is DataState.Loading -> stateItem { LoadingView(it) }
                else -> stateItem { EmptyView(icon = Icons.Rounded.PieChart, title = stringResource(R.string.analysis_empty), modifier = it) }
            }
        }
    }
}

@Composable
private fun AnalysisRow(entry: FileEntry, total: Long, shape: Shape) {
    Surface(shape = shape, color = CleanerSurfaces.row) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = rememberFormattedSize(entry.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ArsenalLinearWavyProgressIndicator(
                progress = { entry.size.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
