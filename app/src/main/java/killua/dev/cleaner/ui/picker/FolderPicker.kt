package killua.dev.cleaner.ui.picker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.settings.groupedItemShape
import killua.dev.arsenal.material.templates.settings.itemPosition
import killua.dev.arsenal.permission.rememberAllFilesAccessState
import killua.dev.cleaner.R
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.IsolatedStorageRepository
import killua.dev.cleaner.ipc.WatchCodec
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.arsenal.material.templates.basics.EmptyView
import killua.dev.arsenal.material.templates.basics.LoadingView
import killua.dev.cleaner.ui.components.ServerRequiredView
import javax.inject.Inject

sealed interface FolderSource {
    data object SharedStorage : FolderSource

    data class Isolated(val packageName: String, val withSharedStorage: Boolean = false) : FolderSource
}

data class FolderPickerState(
    val source: FolderSource = FolderSource.SharedStorage,
    val path: String = "",
    val folders: DataState<List<String>> = DataState.Loading(),
    val online: Boolean = true,
) : UiState {
    val segments: List<String> get() = if (path.isEmpty()) emptyList() else path.split('/')
}

sealed interface FolderPickerIntent : UiIntent {
    data class Open(val path: String, val source: FolderSource) : FolderPickerIntent
    data class Enter(val name: String) : FolderPickerIntent
    data object Up : FolderPickerIntent
}

sealed interface FolderPickerEffect : UiEffect

@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    @CurrentUser private val userId: Int,
    private val storage: IsolatedStorageRepository,
) : BaseViewModel<FolderPickerState, FolderPickerIntent, FolderPickerEffect>(FolderPickerState()) {

    override suspend fun handleIntent(intent: FolderPickerIntent) {
        when (intent) {
            is FolderPickerIntent.Open -> load(intent.source, WatchCodec.normalizeDirectory(intent.path).orEmpty())
            is FolderPickerIntent.Enter ->
                load(currentState.source, listOf(currentState.path, intent.name).filter { it.isNotEmpty() }.joinToString("/"))
            FolderPickerIntent.Up -> load(currentState.source, currentState.path.substringBeforeLast('/', missingDelimiterValue = ""))
        }
    }

    private suspend fun load(source: FolderSource, path: String) {
        setState { copy(source = source, path = path, folders = DataState.Loading()) }
        val folders = list(source, path)
        if (currentState.source != source || currentState.path != path) return
        setState {
            when {
                folders == null -> copy(online = false, folders = DataState.Empty())
                folders.isEmpty() -> copy(online = true, folders = DataState.Empty())
                else -> copy(online = true, folders = DataState.Success(folders))
            }
        }
    }

    private suspend fun list(source: FolderSource, path: String): List<String>? = when (source) {
        FolderSource.SharedStorage -> storage.realFolders(userId, path)
        is FolderSource.Isolated -> storage.isolatedFolders(source.packageName, userId, path)?.let { own ->
            if (!source.withSharedStorage) return@let own
            (own + storage.realFolders(userId, path).orEmpty()).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    title: String,
    initialPath: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    source: FolderSource = FolderSource.SharedStorage,
    allowManualPath: Boolean = true,
) {
    val viewModel: FolderPickerViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val access = rememberAllFilesAccessState()
    val granted = source is FolderSource.Isolated || access.isGranted
    LaunchedEffect(granted, source) {
        if (granted) viewModel.dispatch(FolderPickerIntent.Open(initialPath, source))
    }
    var typed by remember(state.path) { mutableStateOf(state.path) }
    val normalized = WatchCodec.normalizeDirectory(typed)

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (granted) {
                Breadcrumbs(source.rootLabel(), state.segments, onJump = { viewModel.dispatch(FolderPickerIntent.Open(it, source)) })
            }
            Box(Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
                if (granted) {
                    FolderList(state, onIntent = viewModel::dispatch)
                } else {
                    EmptyView(
                        icon = Icons.Rounded.SdStorage,
                        title = stringResource(R.string.picker_permission_title),
                        message = stringResource(
                            if (allowManualPath) R.string.picker_permission_message else R.string.picker_permission_message_browse,
                        ),
                        actionLabel = stringResource(R.string.picker_permission_grant),
                        onAction = access::request,
                    )
                }
            }
            if (allowManualPath) {
                PathField(
                    value = typed,
                    onValueChange = { typed = it },
                    isError = normalized == null,
                )
            }
            val picked = if (allowManualPath) normalized else state.path.takeIf { granted && it.isNotEmpty() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { picked?.let(onPick) }, enabled = picked != null) {
                    Text(stringResource(R.string.picker_confirm))
                }
            }
        }
    }
}

private val LIST_HEIGHT = 320.dp

@Composable
private fun Breadcrumbs(rootLabel: String, segments: List<String>, onJump: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onJump("") }) { Text(rootLabel) }
        segments.forEachIndexed { index, segment ->
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onJump(segments.take(index + 1).joinToString("/")) }) { Text(segment) }
        }
    }
}

@Composable
private fun FolderList(state: FolderPickerState, onIntent: (FolderPickerIntent) -> Unit) {
    val folders = (state.folders as? DataState.Success)?.data.orEmpty()
    val hasUp = state.path.isNotEmpty()
    when {
        !state.online -> ServerRequiredView()
        state.folders is DataState.Loading -> LoadingView()
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val count = folders.size + if (hasUp) 1 else 0
            if (hasUp) {
                item(key = "up") {
                    FolderRow(
                        icon = Icons.Rounded.ArrowUpward,
                        name = stringResource(R.string.picker_up),
                        shape = groupedItemShape(itemPosition(0, count)),
                        chevron = false,
                    ) { onIntent(FolderPickerIntent.Up) }
                }
            }
            if (folders.isEmpty()) {
                item(key = "empty") {
                    EmptyView(
                        icon = Icons.Rounded.FolderOff,
                        title = stringResource(R.string.picker_no_folders),
                        modifier = Modifier.fillParentMaxHeight(if (hasUp) 0.75f else 1f),
                    )
                }
            }
            itemsIndexed(folders, key = { _, name -> "${state.path}/$name" }) { index, name ->
                FolderRow(
                    icon = Icons.Rounded.Folder,
                    name = name,
                    shape = groupedItemShape(itemPosition(index + if (hasUp) 1 else 0, count)),
                    chevron = true,
                ) { onIntent(FolderPickerIntent.Enter(name)) }
            }
        }
    }
}

@Composable
private fun FolderRow(icon: ImageVector, name: String, shape: Shape, chevron: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = shape, color = CleanerSurfaces.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun PathField(value: String, onValueChange: (String) -> Unit, isError: Boolean) {
    val fill = CleanerSurfaces.row
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.picker_path_label)) },
        placeholder = { Text(stringResource(R.string.watch_directory_hint)) },
        supportingText = {
            Text(stringResource(if (isError) R.string.watch_directory_invalid else R.string.picker_path_supporting))
        },
        isError = isError,
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            errorContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun FolderSource.rootLabel(): String = stringResource(
    when (this) {
        FolderSource.SharedStorage -> R.string.watch_directory_root
        is FolderSource.Isolated -> R.string.picker_isolated_root
    },
)

@Composable
private fun FolderSource.pathText(path: String): String = when {
    path.isEmpty() -> rootLabel()
    this is FolderSource.Isolated -> stringResource(R.string.picker_isolated_path, path)
    else -> stringResource(R.string.watch_directory_path, path)
}

@Composable
fun FolderField(
    path: String?,
    source: FolderSource,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = CleanerSurfaces.row,
        border = if (isError) BorderStroke(1.dp, scheme.error) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = path,
            transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false)) },
            label = "folderField",
        ) { picked ->
            if (picked == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Surface(shape = CircleShape, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(Spacing.md).size(32.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) scheme.error else scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = scheme.primary)
                    Text(source.pathText(picked), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.watch_change_directory), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
            }
        }
    }
}
