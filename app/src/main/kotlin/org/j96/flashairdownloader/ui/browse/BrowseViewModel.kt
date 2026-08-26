package org.j96.flashairdownloader.ui.browse

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.j96.flashairdownloader.data.flashair.FileListParser
import org.j96.flashairdownloader.data.flashair.model.FlashAirEntry
import org.j96.flashairdownloader.data.local.SettingsDataStore
import org.j96.flashairdownloader.domain.model.FlashAirFailure
import org.j96.flashairdownloader.domain.usecase.ListDirectoryUseCase
import java.io.IOException
import javax.inject.Inject

data class BrowseUiState(
    val directory: String = SettingsDataStore.DEFAULT_TARGET_DIRECTORY,
    val entries: List<FlashAirEntry> = emptyList(),
    val isLoading: Boolean = true,
    val failure: FlashAirFailure? = null,
    /** True while the shown directory is not the one browsing started at. */
    val canGoUp: Boolean = false,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val listDirectory: ListDirectoryUseCase,
    private val settings: SettingsDataStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    /**
     * Where browsing started, and every directory opened since. Keeping the
     * trail here rather than in the navigation graph means "up" works the same
     * whether it comes from the toolbar or from the system back gesture.
     */
    private val trail = ArrayDeque<String>()
    private var loading: Job? = null

    init {
        viewModelScope.launch {
            val root = FileListParser.normalizeDirectory(settings.targetDirectory.first())
            trail.addLast(root)
            load(root)
        }
    }

    fun open(entry: FlashAirEntry) {
        if (!entry.isDirectory) return
        trail.addLast(entry.path)
        load(entry.path)
    }

    /** @return false when already at the directory browsing started at. */
    fun goUp(): Boolean {
        if (trail.size <= 1) return false
        trail.removeLast()
        load(trail.last())
        return true
    }

    fun retry() = load(_uiState.value.directory)

    private fun load(directory: String) {
        loading?.cancel()
        _uiState.update {
            it.copy(
                directory = directory,
                entries = emptyList(),
                isLoading = true,
                failure = null,
                canGoUp = trail.size > 1,
            )
        }
        loading = viewModelScope.launch {
            val state = try {
                _uiState.value.copy(entries = listDirectory(directory), isLoading = false)
            } catch (failure: IOException) {
                Log.w(TAG, "listing $directory failed", failure)
                _uiState.value.copy(isLoading = false, failure = FlashAirFailure.of(failure))
            }
            _uiState.value = state
        }
    }

    private companion object {
        const val TAG = "FlashAirBrowse"
    }
}
