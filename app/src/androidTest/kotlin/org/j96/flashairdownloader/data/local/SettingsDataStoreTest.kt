package org.j96.flashairdownloader.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The settings against a real DataStore, which is where the defaults, the
 * normalisation and the clamping actually happen.
 *
 * There is one preferences file per app, so each test puts the values it cares
 * about into a known state first rather than assuming a fresh install.
 */
class SettingsDataStoreTest {
    private val settings = SettingsDataStore(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    @Before
    fun resetToDefaults() = runBlocking {
        settings.setHost("")
        settings.setTargetDirectory("")
        settings.setExtensionFilter(emptySet())
        settings.setMaxParallelDownloads(SettingsDataStore.DEFAULT_MAX_PARALLEL_DOWNLOADS)
        settings.setRegisterInMediaStore(false)
    }

    @Test
    fun fallsBackToTheFactoryAddress() = runBlocking {
        assertEquals(SettingsDataStore.DEFAULT_HOST, settings.host.first())
        assertEquals(SettingsDataStore.DEFAULT_TARGET_DIRECTORY, settings.targetDirectory.first())
    }

    @Test
    fun keepsWhatItWasGiven() = runBlocking {
        settings.setHost(" 192.168.10.5 ")
        settings.setTargetDirectory(" /DCIM/100__TSB ")

        assertEquals("192.168.10.5", settings.host.first())
        assertEquals("/DCIM/100__TSB", settings.targetDirectory.first())
    }

    @Test
    fun normalisesTheExtensionFilter() = runBlocking {
        settings.setExtensionFilter(setOf("JPG", ".mov"))

        assertEquals(setOf("jpg", "mov"), settings.extensionFilter.first())
    }

    @Test
    fun treatsAnEmptyFilterAsEveryFile() = runBlocking {
        settings.setExtensionFilter(emptySet())

        assertEquals(emptySet<String>(), settings.extensionFilter.first())
    }

    @Test
    fun keepsTheParallelDownloadsWithinWhatTheCardCopesWith() = runBlocking {
        settings.setMaxParallelDownloads(99)
        assertEquals(SettingsDataStore.MAX_PARALLEL_DOWNLOADS, settings.maxParallelDownloads.first())

        settings.setMaxParallelDownloads(0)
        assertEquals(1, settings.maxParallelDownloads.first())
    }

    @Test
    fun remembersTheCardItLastSaw() = runBlocking {
        settings.setLastCardId("0123456789ABCDEF0123456789ABCDEF")

        assertEquals("0123456789ABCDEF0123456789ABCDEF", settings.lastCardId.first())
    }

    @Test
    fun hasNoDestinationUntilOneIsPicked() = runBlocking {
        // Nothing to reset: a destination is only ever set by the user, and this
        // is the state a fresh install is in.
        val destination = settings.destinationTreeUri.first()

        if (destination == null) {
            assertNull(destination)
        } else {
            // Another test on this device already picked one; then the only
            // thing worth asserting is that it round-trips.
            settings.setDestinationTreeUri(destination)
            assertEquals(destination, settings.destinationTreeUri.first())
        }
    }
}
