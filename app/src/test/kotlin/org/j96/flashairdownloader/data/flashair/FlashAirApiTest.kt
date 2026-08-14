package org.j96.flashairdownloader.data.flashair

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlashAirApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private var endpoint: FlashAirEndpoint? = null
    private lateinit var api: FlashAirApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        endpoint = FlashAirEndpoint(baseUrl = server.url("/"), callFactory = client)
        api = FlashAirApi(
            object : FlashAirEndpointProvider {
                override suspend fun currentEndpoint(): FlashAirEndpoint? = endpoint
            },
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test
    fun `reads the firmware version`() = runTest {
        enqueue(body = "F19BAW3AW2.00.00\r\n")

        assertEquals("F19BAW3AW2.00.00", api.firmwareVersion())
        assertEquals("/command.cgi?op=108", server.takeRequest().target)
    }

    @Test
    fun `reads the SSID and the card id`() = runTest {
        enqueue(body = "flashair_ABCDEF")
        enqueue(body = "0123456789ABCDEF0123456789ABCDEF")

        assertEquals("flashair_ABCDEF", api.ssid())
        assertEquals("0123456789ABCDEF0123456789ABCDEF", api.cardId())
        assertEquals("/command.cgi?op=104", server.takeRequest().target)
        assertEquals("/command.cgi?op=120", server.takeRequest().target)
    }

    @Test
    fun `lists a directory`() = runTest {
        enqueue(body = "WLANSD_FILELIST\r\n/DCIM,IMG_0001.JPG,70408,32,17071,28040\r\n")

        val entries = api.listEntries("/DCIM")

        assertEquals(listOf("/DCIM/IMG_0001.JPG"), entries.map { it.path })
        assertEquals("/command.cgi?op=100&DIR=/DCIM", server.takeRequest().target)
    }

    @Test
    fun `normalizes the requested directory`() = runTest {
        enqueue(body = "WLANSD_FILELIST\r\n")

        api.listEntries("/DCIM/")

        assertEquals("/command.cgi?op=100&DIR=/DCIM", server.takeRequest().target)
    }

    @Test
    fun `lists the card root`() = runTest {
        enqueue(body = "WLANSD_FILELIST\r\n,DCIM,0,16,17071,28040\r\n")

        val entries = api.listEntries("/")

        assertEquals(listOf("/DCIM"), entries.map { it.path })
        assertEquals("/command.cgi?op=100&DIR=/", server.takeRequest().target)
    }

    @Test
    fun `escapes a directory that needs it while keeping the slashes`() = runTest {
        enqueue(body = "WLANSD_FILELIST\r\n")

        api.listEntries("/DCIM/写真 1")

        assertEquals(
            "/command.cgi?op=100&DIR=/DCIM/%E5%86%99%E7%9C%9F%201",
            server.takeRequest().target,
        )
    }

    @Test
    fun `counts the entries of a directory`() = runTest {
        enqueue(body = "42")

        assertEquals(42, api.countEntries("/DCIM"))
        assertEquals("/command.cgi?op=101&DIR=/DCIM", server.takeRequest().target)
    }

    @Test
    fun `reads the write status flag`() = runTest {
        enqueue(body = "1")
        enqueue(body = "0")

        assertTrue(api.consumeWriteStatus())
        assertEquals(false, api.consumeWriteStatus())
    }

    @Test
    fun `rejects an unexpected write status`() = runTest {
        enqueue(body = "yes")

        assertFailsWith<FlashAirResponseFormatException> { api.consumeWriteStatus() }
    }

    @Test
    fun `reads the free space`() = runTest {
        enqueue(body = "31082496/31088640,512")

        val freeSpace = api.freeSpace()

        assertEquals(31_082_496L, freeSpace?.freeSectors)
        assertEquals(31_088_640L, freeSpace?.totalSectors)
        assertEquals(512, freeSpace?.sectorSize)
        assertEquals(31_082_496L * 512, freeSpace?.freeBytes)
    }

    @Test
    fun `treats an unsupported optional op as absent`() = runTest {
        enqueue(code = 400, body = "")
        enqueue(body = "not a number")

        assertNull(api.freeSpace())
        assertNull(api.uptimeMillis())
    }

    @Test
    fun `fails a command that the card rejects`() = runTest {
        enqueue(code = 500, body = "")

        assertEquals(500, assertFailsWith<FlashAirHttpException> { api.firmwareVersion() }.code)
    }

    @Test
    fun `fails when no network is bound`() = runTest {
        endpoint = null

        assertFailsWith<FlashAirNotConnectedException> { api.firmwareVersion() }
    }

    @Test
    fun `downloads a file`() = runTest {
        enqueue(body = "JPEG-BYTES")

        val body = api.openFile("/DCIM/100__TSB/IMG_0001.JPG").use { it.body.string() }

        assertEquals("JPEG-BYTES", body)
        val request = server.takeRequest()
        assertEquals("/DCIM/100__TSB/IMG_0001.JPG", request.target)
        assertNull(request.headers["Range"])
    }

    @Test
    fun `asks for a range when resuming`() = runTest {
        enqueue(code = 206, body = "TAIL")

        api.openFile("/DCIM/IMG_0001.JPG", rangeStart = 1024).use { it.body.string() }

        assertEquals("bytes=1024-", server.takeRequest().headers["Range"])
    }

    @Test
    fun `escapes non ASCII path segments`() = runTest {
        enqueue(body = "JPEG-BYTES")

        api.openFile("/DCIM/写真 1.JPG").use { it.body.string() }

        assertEquals("/DCIM/%E5%86%99%E7%9C%9F%201.JPG", server.takeRequest().target)
    }

    @Test
    fun `fails a download that the card rejects`() = runTest {
        enqueue(code = 404, body = "")

        assertFailsWith<FlashAirHttpException> { api.openFile("/DCIM/MISSING.JPG") }
    }

    @Test
    fun `reads a thumbnail`() = runTest {
        enqueue(body = "THUMB")

        assertEquals("THUMB", api.thumbnail("/DCIM/IMG_0001.JPG")?.decodeToString())
        assertEquals("/thumbnail.cgi?/DCIM/IMG_0001.JPG", server.takeRequest().target)
    }

    @Test
    fun `reports a missing thumbnail as absent`() = runTest {
        enqueue(code = 404, body = "")

        assertNull(api.thumbnail("/DCIM/MOVIE.MOV"))
    }

    @Test
    fun `builds URLs for components that fetch on their own`() = runTest {
        assertEquals(
            server.url("/DCIM/100__TSB/IMG_0001.JPG"),
            api.fileUrl("/DCIM/100__TSB/IMG_0001.JPG"),
        )
        assertEquals(
            server.url("/thumbnail.cgi").newBuilder().encodedQuery("/DCIM/IMG_0001.JPG").build(),
            api.thumbnailUrl("/DCIM/IMG_0001.JPG"),
        )
    }

    private fun enqueue(code: Int = 200, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }
}
