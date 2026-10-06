package killua.dev.cleaner.ui.storage

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import killua.dev.arsenal.core.mvvm.BaseViewModel
import killua.dev.arsenal.core.mvvm.UiEffect
import killua.dev.arsenal.core.mvvm.UiIntent
import killua.dev.arsenal.core.mvvm.UiState
import killua.dev.arsenal.core.state.DataState
import killua.dev.arsenal.navigation.Navigator
import killua.dev.cleaner.di.CurrentUser
import killua.dev.cleaner.domain.IsolatedStorageRepository
import killua.dev.cleaner.domain.IsolationController
import killua.dev.cleaner.domain.model.FileEntry
import killua.dev.cleaner.ui.app.AppRuleViewModel
import javax.inject.Inject

data class StorageState(
    val packageName: String,
    val daemonOnline: Boolean = false,
    val path: List<String> = emptyList(),
    val entries: DataState<List<FileEntry>> = DataState.Loading(),
) : UiState {
    val subPath: String get() = path.joinToString("/")
    val totalSize: Long get() = entries.dataOrNull?.sumOf { it.size } ?: 0L
}

sealed interface StorageIntent : UiIntent {
    data class Enter(val entry: FileEntry) : StorageIntent
    data object Up : StorageIntent
    data object Reload : StorageIntent
    data object Back : StorageIntent
}

sealed interface StorageEffect : UiEffect

enum class StorageView { Browser, Gallery, Analysis }

abstract class StorageViewModel(
    savedStateHandle: SavedStateHandle,
    private val userId: Int,
    private val view: StorageView,
    private val storage: IsolatedStorageRepository,
    private val navigator: Navigator,
    controller: IsolationController,
) : BaseViewModel<StorageState, StorageIntent, StorageEffect>(
    StorageState(packageName = savedStateHandle.get<String>(AppRuleViewModel.ARG_PACKAGE).orEmpty()),
) {
    private val packageName = currentState.packageName

    init {
        controller.observeConnected().launchInScope { daemonOnline ->
            setState { copy(daemonOnline = daemonOnline) }
            if (daemonOnline) dispatch(StorageIntent.Reload)
        }
    }

    override suspend fun handleIntent(intent: StorageIntent) {
        when (intent) {
            is StorageIntent.Enter -> if (intent.entry.isDirectory && view == StorageView.Browser) {
                setState { copy(path = path + intent.entry.name) }
                load()
            }
            StorageIntent.Up -> if (currentState.path.isNotEmpty()) {
                setState { copy(path = path.dropLast(1)) }
                load()
            } else {
                navigator.navigateUp()
            }
            StorageIntent.Reload -> load()
            StorageIntent.Back -> navigator.navigateUp()
        }
    }

    suspend fun thumbnail(subPath: String, sizePx: Int): Bitmap? =
        storage.thumbnail(packageName, userId, subPath, sizePx)

    private suspend fun load() {
        if (!currentState.daemonOnline) return
        setState { copy(entries = DataState.Loading(entries.dataOrNull)) }
        val result = when (view) {
            StorageView.Browser -> storage.list(packageName, userId, currentState.subPath)
                .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            StorageView.Gallery -> storage.media(packageName, userId, MEDIA_LIMIT)
            StorageView.Analysis -> storage.analyze(packageName, userId).sortedByDescending { it.size }
        }
        setState { copy(entries = if (result.isEmpty()) DataState.Empty() else DataState.Success(result)) }
    }

    private companion object {
        const val MEDIA_LIMIT = 500
    }
}

@HiltViewModel
class IsolatedBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @CurrentUser userId: Int,
    storage: IsolatedStorageRepository,
    navigator: Navigator,
    controller: IsolationController,
) : StorageViewModel(savedStateHandle, userId, StorageView.Browser, storage, navigator, controller)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @CurrentUser userId: Int,
    storage: IsolatedStorageRepository,
    navigator: Navigator,
    controller: IsolationController,
) : StorageViewModel(savedStateHandle, userId, StorageView.Gallery, storage, navigator, controller)

@HiltViewModel
class FolderAnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @CurrentUser userId: Int,
    storage: IsolatedStorageRepository,
    navigator: Navigator,
    controller: IsolationController,
) : StorageViewModel(savedStateHandle, userId, StorageView.Analysis, storage, navigator, controller)
