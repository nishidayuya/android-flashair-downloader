package org.j96.flashairdownloader.ui.browse

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.j96.flashairdownloader.data.flashair.FlashAirApi
import org.j96.flashairdownloader.data.flashair.FlashAirEndpoint
import org.j96.flashairdownloader.data.flashair.FlashAirEndpointProvider
import org.j96.flashairdownloader.data.local.SettingsDataStore
import org.j96.flashairdownloader.domain.model.FlashAirFailure
import org.j96.flashairdownloader.domain.usecase.ListDirectoryUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The browse view model on a device, where its StateFlow runs on the real main
 * dispatcher and its starting directory comes out of a real DataStore -- neither
 * of which exists in a JVM test.
 */
class BrowseViewModelTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val settings = SettingsDataStore(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    @Before
    fun setUp() = runBlocking {
        server.start()
        settings.setTargetDirectory("/DCIM")
    }

    @After
    fun tearDown() {
        server.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test
    fun startsAtTheConfiguredDirectory() {
        enqueueListing("/DCIM,100__TSB,0,16,17071,28040", "/DCIM,IMG_0001.JPG,10,32,17071,28040")

        val state = viewModel().awaitLoaded()

        assertEquals("/DCIM", state.directory)
        assertEquals(listOf("100__TSB", "IMG_0001.JPG"), state.entries.map { it.name })
        assertEquals(false, state.canGoUp)
    }

    @Test
    fun walksIntoADirectoryAndBackOut() {
        // Answered in the order they are enqueued: the root, then the
        // subdirectory, then the root again on the way back up.
        enqueueListing("/DCIM,100__TSB,0,16,17071,28040")
        enqueueListing("/DCIM/100__TSB,IMG_0001.JPG,10,32,17071,28040")
        enqueueListing("/DCIM,100__TSB,0,16,17071,28040")
        val viewModel = viewModel()

        val root = viewModel.awaitLoaded()
        viewModel.open(root.entries.single())
        val child = viewModel.awaitLoaded { it.directory == "/DCIM/100__TSB" }

        assertEquals(listOf("IMG_0001.JPG"), child.entries.map { it.name })
        assertTrue(child.canGoUp)

        assertTrue(viewModel.goUp())
        val backAtRoot = viewModel.awaitLoaded { it.directory == "/DCIM" }
        assertEquals(false, backAtRoot.canGoUp)
        // Nowhere left to go: the screen has to hand "back" to the navigation.
        assertEquals(false, viewModel.goUp())
    }

    @Test
    fun reportsWhatTheCardAnsweredWith() {
        server.enqueue(MockResponse.Builder().code(500).body("").build())

        val state = viewModel().awaitLoaded()

        assertEquals(FlashAirFailure.CARD_ERROR, state.failure)
        assertEquals(emptyList<String>(), state.entries.map { it.name })
    }

    private fun viewModel() = BrowseViewModel(
        listDirectory = ListDirectoryUseCase(FlashAirApi(endpointProvider())),
        settings = settings,
    )

    private fun endpointProvider() = object : FlashAirEndpointProvider {
        override suspend fun currentEndpoint() =
            FlashAirEndpoint(baseUrl = server.url("/"), callFactory = client)
    }

    private fun BrowseViewModel.awaitLoaded(
        predicate: (BrowseUiState) -> Boolean = { true },
    ): BrowseUiState = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            uiState.first { !it.isLoading && predicate(it) }
        }
    }

    private fun enqueueListing(vararg lines: String) {
        server.enqueue(
            MockResponse.Builder()
                .body((listOf("WLANSD_FILELIST") + lines).joinToString("\r\n", postfix = "\r\n"))
                .build(),
        )
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
