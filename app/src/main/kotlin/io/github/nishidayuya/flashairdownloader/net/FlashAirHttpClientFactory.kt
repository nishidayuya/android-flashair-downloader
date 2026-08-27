package io.github.nishidayuya.flashairdownloader.net

import android.net.Network
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds an [OkHttpClient] whose traffic goes over one specific [Network].
 *
 * Only this client is bound to the FlashAir Wi-Fi, rather than the whole process
 * (`bindProcessToNetwork`), so that nothing else in the app is dragged onto a
 * network without internet access and there is no binding left to forget about.
 * Name resolution has to go through the network too, or only the DNS lookup ends
 * up on mobile data. See docs/design.md 3.1.
 */
@Singleton
class FlashAirHttpClientFactory @Inject constructor() {
    fun create(network: Network): OkHttpClient =
        OkHttpClient.Builder()
            .socketFactory(network.socketFactory)
            .dns(NetworkDns(network))
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .writeTimeout(READ_TIMEOUT)
            // No whole-call timeout: it would cut long downloads short.
            .callTimeout(Duration.ZERO)
            .build()

    private class NetworkDns(private val network: Network) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            network.getAllByName(hostname).toList()
    }

    private companion object {
        // docs/design.md 7. The card is on a local link, but a slow one.
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
