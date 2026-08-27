package io.github.nishidayuya.flashairdownloader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nishidayuya.flashairdownloader.data.local.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val host: String = SettingsDataStore.DEFAULT_HOST,
    val targetDirectory: String = SettingsDataStore.DEFAULT_TARGET_DIRECTORY,
    val destinationUri: String? = null,
    val extensionFilter: String = "",
    val maxParallelDownloads: Int = SettingsDataStore.DEFAULT_MAX_PARALLEL_DOWNLOADS,
    val registerInMediaStore: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        settings.host,
        settings.targetDirectory,
        settings.destinationTreeUri,
        settings.extensionFilter,
        combine(settings.maxParallelDownloads, settings.registerInMediaStore, ::Pair),
    ) { host, targetDirectory, destinationUri, extensions, (parallel, mediaStore) ->
        SettingsUiState(
            host = host,
            targetDirectory = targetDirectory,
            destinationUri = destinationUri,
            extensionFilter = extensions.joinToString(", "),
            maxParallelDownloads = parallel,
            registerInMediaStore = mediaStore,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS), SettingsUiState())

    fun setHost(host: String) = update { settings.setHost(host) }

    fun setTargetDirectory(directory: String) = update { settings.setTargetDirectory(directory) }

    fun setDestination(treeUri: String) = update { settings.setDestinationTreeUri(treeUri) }

    fun setExtensionFilter(text: String) = update {
        settings.setExtensionFilter(
            text.split(',').map { it.trim().removePrefix(".").lowercase() }.filter { it.isNotEmpty() }.toSet(),
        )
    }

    fun setMaxParallelDownloads(count: Int) = update { settings.setMaxParallelDownloads(count) }

    fun setRegisterInMediaStore(enabled: Boolean) = update { settings.setRegisterInMediaStore(enabled) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val SUBSCRIPTION_GRACE_MILLIS = 5_000L
    }
}
