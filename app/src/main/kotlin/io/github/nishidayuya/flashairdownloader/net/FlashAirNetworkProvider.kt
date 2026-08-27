package io.github.nishidayuya.flashairdownloader.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nishidayuya.flashairdownloader.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps track of a Wi-Fi network that has no internet access -- which is what a
 * FlashAir card offers.
 *
 * Without this the app's traffic goes out over mobile data and never reaches the
 * card, because the system does not route to a network that cannot reach the
 * internet. See docs/design.md 3.1.
 *
 * The request stays registered for as long as the process lives: the app exists
 * to talk to the card, so it always wants to know whether the card's Wi-Fi is
 * there, and [network] would otherwise report null to whoever is not collecting
 * it (the sync service, for instance).
 */
@Singleton
class FlashAirNetworkProvider @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivityManager =
        requireNotNull(context.getSystemService(ConnectivityManager::class.java))

    /** The network to send FlashAir requests over, or null while there is none. */
    val network: StateFlow<Network?> = callbackFlow {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // More than one network can match; the most recent one wins, and losing
        // it falls back to the one before rather than to "disconnected".
        val available = MutableStateFlow<List<Network>>(emptyList())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.value = available.value.filterNot { it == network } + network
                trySend(available.value.lastOrNull())
            }

            override fun onLost(network: Network) {
                available.value = available.value.filterNot { it == network }
                trySend(available.value.lastOrNull())
            }

            override fun onUnavailable() {
                available.value = emptyList()
                trySend(null)
            }
        }

        connectivityManager.requestNetwork(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * What the app is actually bound to, for the failure details on screen and
     * in the log: which interface, which addresses, which routes. A request
     * that fails while this says the wrong network is a different problem from
     * one that fails while it says the right one.
     */
    fun describeCurrentNetwork(): String {
        val network = network.value ?: return "no network"
        val properties = connectivityManager.getLinkProperties(network) ?: return "$network (no link properties)"
        val addresses = properties.linkAddresses.joinToString(",")
        val routes = properties.routes.joinToString(",") { it.destination.toString() }
        return "${properties.interfaceName} addresses=[$addresses] routes=[$routes]"
    }
}
