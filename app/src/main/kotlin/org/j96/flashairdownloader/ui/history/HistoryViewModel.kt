package org.j96.flashairdownloader.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.j96.flashairdownloader.data.local.DownloadRecordDao
import org.j96.flashairdownloader.data.local.DownloadRecordEntity
import org.j96.flashairdownloader.data.local.SettingsDataStore
import org.j96.flashairdownloader.domain.model.SyncFailure
import org.j96.flashairdownloader.sync.SyncController
import javax.inject.Inject

data class HistoryUiState(
    val cardId: String? = null,
    val records: List<DownloadRecordEntity> = emptyList(),
    /** Failures of the most recent run, which is where they are still interesting. */
    val failures: List<SyncFailure> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val records: DownloadRecordDao,
    private val settings: SettingsDataStore,
    syncController: SyncController,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        settings.lastCardId,
        settings.lastCardId.flatMapLatest { cardId ->
            if (cardId == null) flowOf(emptyList()) else records.observeForCard(cardId)
        },
        syncController.progress,
    ) { cardId, records, progress ->
        HistoryUiState(cardId = cardId, records = records, failures = progress.failures)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS), HistoryUiState())

    /**
     * Forgets what was downloaded from this card. The files stay where they are;
     * the next sync will see them in the destination and record them again
     * instead of fetching them twice.
     */
    fun resetRecords() {
        val cardId = uiState.value.cardId ?: return
        viewModelScope.launch { records.clearCard(cardId) }
    }

    private companion object {
        const val SUBSCRIPTION_GRACE_MILLIS = 5_000L
    }
}
