package org.j96.flashairdownloader.data.flashair

import okhttp3.Call
import okhttp3.HttpUrl

/**
 * Everything an HTTP call to the card needs that changes at runtime: where the
 * card is (the configured host) and which network to reach it over.
 *
 * [callFactory] is an `OkHttpClient` bound to the FlashAir Wi-Fi network; it has
 * to be replaced whenever that network changes, which is why the API always
 * asks a [FlashAirEndpointProvider] for a fresh one instead of holding a client
 * of its own. See docs/design.md 3.1.
 */
data class FlashAirEndpoint(
    val baseUrl: HttpUrl,
    val callFactory: Call.Factory,
)

interface FlashAirEndpointProvider {
    /** @return null while no FlashAir-capable Wi-Fi network is bound. */
    suspend fun currentEndpoint(): FlashAirEndpoint?
}
