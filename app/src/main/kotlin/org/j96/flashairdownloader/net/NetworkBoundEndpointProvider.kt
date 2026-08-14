package org.j96.flashairdownloader.net

import android.net.Network
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.j96.flashairdownloader.data.flashair.FlashAirEndpoint
import org.j96.flashairdownloader.data.flashair.FlashAirEndpointProvider
import org.j96.flashairdownloader.data.local.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ties the configured host together with the currently bound FlashAir network.
 *
 * A client belongs to exactly one [Network], so when the network changes the old
 * client is thrown away -- including its connection pool, whose sockets point at
 * a network that is gone. See docs/design.md 3.1.
 */
@Singleton
class NetworkBoundEndpointProvider @Inject constructor(
    private val networkProvider: FlashAirNetworkProvider,
    private val clientFactory: FlashAirHttpClientFactory,
    private val settings: SettingsDataStore,
) : FlashAirEndpointProvider {
    private val mutex = Mutex()
    private var boundNetwork: Network? = null
    private var boundClient: OkHttpClient? = null

    override suspend fun currentEndpoint(): FlashAirEndpoint? {
        val network = networkProvider.network.value ?: return null
        val host = settings.host.first()
        return FlashAirEndpoint(baseUrl = baseUrl(host), callFactory = clientFor(network))
    }

    private suspend fun clientFor(network: Network): OkHttpClient = mutex.withLock {
        boundClient?.takeIf { boundNetwork == network } ?: run {
            boundClient?.connectionPool?.evictAll()
            clientFactory.create(network).also {
                boundNetwork = network
                boundClient = it
            }
        }
    }

    /** Falls back to the factory address when the configured host is unusable. */
    private fun baseUrl(host: String): HttpUrl =
        "http://$host/".toHttpUrlOrNull() ?: "http://${SettingsDataStore.DEFAULT_HOST}/".toHttpUrl()
}
