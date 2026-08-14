package org.j96.flashairdownloader.data.flashair

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import org.j96.flashairdownloader.data.flashair.model.FlashAirEntry
import org.j96.flashairdownloader.data.flashair.model.FlashAirFreeSpace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The FlashAir HTTP API, as far as this app uses it. See docs/design.md 2.
 *
 * Every call resolves the endpoint again through the [FlashAirEndpointProvider],
 * because the network the card is reachable over comes and goes.
 */
// One function per op plus the URL builders: the class is wide by design, not
// because it does several things.
@Suppress("TooManyFunctions")
@Singleton
class FlashAirApi @Inject constructor(
    private val endpointProvider: FlashAirEndpointProvider,
) {
    /** `op=100`: the entries of [directory], in the order the card lists them. */
    suspend fun listEntries(directory: String): List<FlashAirEntry> {
        val normalized = FileListParser.normalizeDirectory(directory)
        val body = command(OP_FILE_LIST, normalized)
        return FileListParser.parse(normalized, body)
    }

    /** `op=101`: how many entries [directory] has. */
    suspend fun countEntries(directory: String): Int {
        val normalized = FileListParser.normalizeDirectory(directory)
        val body = command(OP_FILE_COUNT, normalized)
        return body.toIntOrNull() ?: throw FlashAirResponseFormatException(body, "a file count")
    }

    /**
     * `op=102`: whether the card was written to since this was last asked.
     *
     * Reading the flag clears it on the card, so this must be called from a
     * single place only. See docs/design.md 2.1.
     */
    suspend fun consumeWriteStatus(): Boolean =
        when (val body = command(OP_WRITE_STATUS)) {
            "1" -> true
            "0" -> false
            else -> throw FlashAirResponseFormatException(body, "0 or 1")
        }

    /** `op=104`: the card's SSID. */
    suspend fun ssid(): String = command(OP_SSID)

    /** `op=108`: the firmware version, e.g. "F19BAW3AW2.00.00". */
    suspend fun firmwareVersion(): String = command(OP_FIRMWARE_VERSION)

    /** `op=120`: the CID, used as the identity of the card. */
    suspend fun cardId(): String = command(OP_CARD_ID)

    /** `op=121`: milliseconds since the card powered up. Null on firmware that lacks it. */
    suspend fun uptimeMillis(): Long? = optional { command(OP_UPTIME).toLongOrNull() }

    /** `op=140`: free space. Null when the firmware lacks it or the answer is unusable. */
    suspend fun freeSpace(): FlashAirFreeSpace? = optional { parseFreeSpace(command(OP_FREE_SPACE)) }

    /** `op=220`: WebDAV state (0 disabled, 1 read only, 2 read/write). Null on older firmware. */
    suspend fun webDavStatus(): Int? = optional { command(OP_WEBDAV_STATUS).toIntOrNull() }

    /**
     * Starts a download of [path].
     *
     * The response is handed over open: the caller streams the body and is
     * responsible for closing it. [rangeStart], when given, asks the card to
     * resume from that offset -- support for it is not guaranteed, so the caller
     * has to check [Response.code] for 206 before trusting it
     * (docs/design.md 2.5).
     */
    suspend fun openFile(path: String, rangeStart: Long? = null): Response {
        val endpoint = requireEndpoint()
        val builder = Request.Builder().url(filePath(endpoint.baseUrl, path))
        if (rangeStart != null && rangeStart > 0) {
            builder.header("Range", "bytes=$rangeStart-")
        }
        val response = endpoint.callFactory.newCall(builder.build()).executeAsync()
        if (!response.isSuccessful) {
            response.close()
            throw FlashAirHttpException(response.code, response.request.url.toString())
        }
        return response
    }

    /**
     * `thumbnail.cgi`: the Exif thumbnail of [path].
     *
     * Only JPEG files have one; anything else answers with an error, which is
     * reported as null so that the caller can fall back to an icon.
     */
    suspend fun thumbnail(path: String): ByteArray? {
        val endpoint = requireEndpoint()
        val request = Request.Builder().url(thumbnailUrl(endpoint.baseUrl, path)).build()
        return endpoint.callFactory.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                withContext(Dispatchers.IO) { response.body.bytes() }
            }
        }
    }

    /** The URL of [path] on the card, for components that fetch it themselves (Coil). */
    suspend fun fileUrl(path: String): HttpUrl = filePath(requireEndpoint().baseUrl, path)

    /** The `thumbnail.cgi` URL of [path], for components that fetch it themselves (Coil). */
    suspend fun thumbnailUrl(path: String): HttpUrl = thumbnailUrl(requireEndpoint().baseUrl, path)

    /**
     * Runs a `command.cgi` request and returns the trimmed response body.
     *
     * [directory] is passed as the `DIR` parameter when given.
     */
    suspend fun command(op: Int, directory: String? = null): String {
        val endpoint = requireEndpoint()
        val url = endpoint.baseUrl.newBuilder()
            .addPathSegment(COMMAND_CGI)
            .addQueryParameter("op", op.toString())
            .apply {
                if (directory != null) {
                    addEncodedQueryParameter("DIR", encodedDirectory(endpoint.baseUrl, directory))
                }
            }
            .build()
        val request = Request.Builder().url(url).build()
        return endpoint.callFactory.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw FlashAirHttpException(response.code, url.toString())
            }
            withContext(Dispatchers.IO) { response.body.string() }.trim()
        }
    }

    private suspend fun requireEndpoint(): FlashAirEndpoint =
        endpointProvider.currentEndpoint() ?: throw FlashAirNotConnectedException()

    /**
     * Runs an optional op: firmware that does not know it answers with an error
     * status or with something unparseable, and neither must stop the caller.
     * Transport failures still propagate -- they mean the card is gone.
     */
    private inline fun <T> optional(block: () -> T): T? =
        try {
            block()
        } catch (_: FlashAirHttpException) {
            null
        } catch (_: FlashAirResponseFormatException) {
            null
        }

    // Guard clauses again: any field the card spells differently than documented
    // means "no free space information", not a crash.
    @Suppress("ReturnCount")
    private fun parseFreeSpace(body: String): FlashAirFreeSpace? {
        val (sectors, sectorSize) = body.split(',').takeIf { it.size == 2 } ?: return null
        val (free, total) = sectors.split('/').takeIf { it.size == 2 } ?: return null
        return FlashAirFreeSpace(
            freeSectors = free.trim().toLongOrNull() ?: return null,
            totalSectors = total.trim().toLongOrNull() ?: return null,
            sectorSize = sectorSize.trim().toIntOrNull() ?: return null,
        )
    }

    private companion object {
        const val COMMAND_CGI = "command.cgi"
        const val THUMBNAIL_CGI = "thumbnail.cgi"

        // command.cgi op codes, see docs/design.md 2.1.
        const val OP_FILE_LIST = 100
        const val OP_FILE_COUNT = 101
        const val OP_WRITE_STATUS = 102
        const val OP_SSID = 104
        const val OP_FIRMWARE_VERSION = 108
        const val OP_CARD_ID = 120
        const val OP_UPTIME = 121
        const val OP_FREE_SPACE = 140
        const val OP_WEBDAV_STATUS = 220

        /**
         * Path segments are added one by one so that OkHttp escapes them; the
         * app never builds percent-encoded strings itself (docs/design.md 2.5).
         */
        fun filePath(baseUrl: HttpUrl, path: String): HttpUrl =
            baseUrl.newBuilder()
                .apply {
                    path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
                }
                .build()

        /**
         * The `DIR` parameter, encoded for use as a query string value.
         *
         * The documented requests spell the directory with literal slashes
         * (`DIR=/DCIM`), and OkHttp would escape those to `%2F`, so the value is
         * encoded as a URL path -- which handles spaces and non-ASCII names --
         * and only the three characters that would then break the query string
         * apart are escaped by hand.
         */
        fun encodedDirectory(baseUrl: HttpUrl, directory: String): String =
            filePath(baseUrl, directory).encodedPath
                .replace("&", "%26")
                .replace("=", "%3D")
                .replace("+", "%2B")

        /**
         * `thumbnail.cgi` takes the file path as the whole query string rather
         * than as a named parameter, so the encoded path of the file URL is
         * reused verbatim.
         */
        fun thumbnailUrl(baseUrl: HttpUrl, path: String): HttpUrl =
            baseUrl.newBuilder()
                .addPathSegment(THUMBNAIL_CGI)
                .encodedQuery(filePath(baseUrl, path).encodedPath)
                .build()
    }
}
