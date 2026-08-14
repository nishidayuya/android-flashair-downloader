package org.j96.flashairdownloader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.j96.flashairdownloader.domain.model.CardInfo
import org.j96.flashairdownloader.domain.model.FlashAirFailure
import org.j96.flashairdownloader.domain.usecase.ProbeCardUseCase
import org.j96.flashairdownloader.net.FlashAirNetworkProvider
import java.io.IOException
import javax.inject.Inject

sealed interface HomeUiState {
    /** No Wi-Fi without internet access is bound, so there is nothing to talk to. */
    data object Disconnected : HomeUiState

    data object Probing : HomeUiState

    data class Connected(val card: CardInfo) : HomeUiState

    data class Failed(val failure: FlashAirFailure) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    networkProvider: FlashAirNetworkProvider,
    private val probeCard: ProbeCardUseCase,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> =
        combine(networkProvider.network, retries) { network, _ -> network }
            .flatMapLatest { network ->
                if (network == null) {
                    flowOf(HomeUiState.Disconnected)
                } else {
                    probe()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS), HomeUiState.Probing)

    fun retry() = retries.update { it + 1 }

    private fun probe() = flow {
        emit(HomeUiState.Probing)
        emit(
            try {
                HomeUiState.Connected(probeCard())
            } catch (failure: IOException) {
                // Everything the card or the network can do wrong is an
                // IOException; anything else would be a bug worth crashing on.
                HomeUiState.Failed(FlashAirFailure.of(failure))
            },
        )
    }

    private companion object {
        /** Keeps the probe alive across a configuration change. */
        const val SUBSCRIPTION_GRACE_MILLIS = 5_000L
    }
}
