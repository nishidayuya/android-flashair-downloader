package io.github.nishidayuya.flashairdownloader.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nishidayuya.flashairdownloader.BuildConfig
import io.github.nishidayuya.flashairdownloader.data.local.DownloadRecordDao
import io.github.nishidayuya.flashairdownloader.data.local.SettingsDataStore
import io.github.nishidayuya.flashairdownloader.domain.model.CardInfo
import io.github.nishidayuya.flashairdownloader.domain.model.FlashAirFailure
import io.github.nishidayuya.flashairdownloader.domain.model.SyncProgress
import io.github.nishidayuya.flashairdownloader.domain.usecase.ProbeCardUseCase
import io.github.nishidayuya.flashairdownloader.net.FlashAirNetworkProvider
import io.github.nishidayuya.flashairdownloader.sync.SyncController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed interface ConnectionState {
    /** No Wi-Fi without internet access is bound, so there is nothing to talk to. */
    data object Disconnected : ConnectionState

    data object Probing : ConnectionState

    data class Connected(val card: CardInfo) : ConnectionState

    /**
     * @param detail what actually went wrong, for debug builds only: the
     *   message names a cause the user can act on, which the exception
     *   rarely does, but without the exception a failure like this one
     *   cannot be told apart from the other reasons a card stays silent.
     */
    data class Failed(
        val failure: FlashAirFailure,
        val detail: String? = null,
    ) : ConnectionState
}

data class HomeUiState(
    val connection: ConnectionState = ConnectionState.Probing,
    val destinationUri: String? = null,
    val lastSyncEpoch: Long? = null,
    val syncState: SyncProgress.State = SyncProgress.State.IDLE,
) {
    val canStartSync: Boolean
        get() = connection is ConnectionState.Connected && destinationUri != null && !syncState.isRunning
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val networkProvider: FlashAirNetworkProvider,
    private val probeCard: ProbeCardUseCase,
    private val settings: SettingsDataStore,
    private val records: DownloadRecordDao,
    private val syncController: SyncController,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val connection: StateFlow<ConnectionState> =
        combine(networkProvider.network, retries) { network, _ -> network }
            .flatMapLatest { network ->
                if (network == null) flowOf(ConnectionState.Disconnected) else probe()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS), ConnectionState.Probing)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        connection,
        connection.flatMapLatest { state ->
            if (state is ConnectionState.Connected) {
                records.observeLastDownloadEpoch(state.card.id)
            } else {
                flowOf(null)
            }
        },
        settings.destinationTreeUri,
        syncController.progress,
    ) { connection, lastSyncEpoch, destinationUri, progress ->
        HomeUiState(
            connection = connection,
            destinationUri = destinationUri,
            lastSyncEpoch = lastSyncEpoch,
            syncState = progress.state,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS), HomeUiState())

    fun retry() = retries.update { it + 1 }

    fun startSync() = syncController.start()

    fun setDestination(treeUri: String) {
        viewModelScope.launch { settings.setDestinationTreeUri(treeUri) }
    }

    private fun probe() = flow {
        emit(ConnectionState.Probing)
        emit(
            try {
                ConnectionState.Connected(probeCard())
            } catch (failure: IOException) {
                // Everything the card or the network can do wrong is an
                // IOException; anything else would be a bug worth crashing on.
                val bound = networkProvider.describeCurrentNetwork()
                Log.w(TAG, "probing the card failed over $bound", failure)
                ConnectionState.Failed(
                    failure = FlashAirFailure.of(failure),
                    detail = if (BuildConfig.DEBUG) {
                        "${failure.javaClass.simpleName}: ${failure.message}\n$bound"
                    } else {
                        null
                    },
                )
            },
        )
    }

    private companion object {
        const val TAG = "FlashAirHome"

        /** Keeps the probe alive across a configuration change. */
        const val SUBSCRIPTION_GRACE_MILLIS = 5_000L
    }
}
