package io.github.nishidayuya.flashairdownloader.data.flashair

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Collections

/**
 * A card that answers out of a map, so that a test can describe a whole
 * directory tree in a few lines without starting a server.
 */
class FakeFlashAirCard : FlashAirEndpointProvider {
    /** Response body per request target, e.g. "/command.cgi?op=100&DIR=/DCIM". */
    private val responses = mutableMapOf<String, String>()

    /** Every target that was requested, in order. */
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val target = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
            requests += target
            responses[target]?.let { response(request, code = 200, body = it) }
                ?: response(request, code = 404, body = "")
        }
        .build()

    override suspend fun currentEndpoint(): FlashAirEndpoint = FlashAirEndpoint(
        baseUrl = HttpUrl.Builder().scheme("http").host("192.168.0.1").build(),
        callFactory = client,
    )

    /** Declares the `op=100` answer for [path]; [lines] are CSV entry lines. */
    fun directory(path: String, vararg lines: String) {
        responses["/command.cgi?op=100&DIR=$path"] =
            (listOf("WLANSD_FILELIST") + lines).joinToString("\r\n", postfix = "\r\n")
    }

    fun file(path: String, content: String) {
        responses[path] = content
    }

    private fun response(request: Request, code: Int, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 200) "OK" else "Error")
        .body(body.toResponseBody())
        .build()
}
