package org.j96.flashairdownloader.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.j96.flashairdownloader.R
import org.j96.flashairdownloader.ui.theme.FlashAirDownloaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reportsEveryChangeToTheAddress() {
        val changes = mutableListOf<String>()
        setSettings(SettingsUiState(host = ""), onHostChange = { changes += it })

        field(R.string.settings_host).performTextInput("10.0.0.1")

        assertEquals("10.0.0.1", changes.last())
    }

    /**
     * Regression test. Settings are stored as they are typed and read back
     * through a flow that normalises them, and the field used to be re-seeded
     * from that echo, which swallowed characters typed in the meantime.
     */
    @Test
    fun keepsWhatWasTypedWhileTheStoredValueEchoesBack() {
        val echoes = mutableListOf<String>()
        setSettingsWithSlowNormalisingStore(echoes)

        val extensions = field(R.string.settings_extension_filter)
        extensions.performTextInput("J")
        extensions.performTextInput("P")
        extensions.performTextInput("G")
        // Wait for the store to have echoed every keystroke back.
        composeTestRule.waitUntil(ECHO_TIMEOUT_MILLIS) { echoes.size >= 3 }

        composeTestRule.onNode(hasSetTextAction() and hasText("JPG")).assertIsDisplayed()
    }

    @Test
    fun reportsTheGalleryOptionBeingSwitched() {
        var enabled: Boolean? = null
        setSettings(SettingsUiState(), onRegisterInMediaStoreChange = { enabled = it })

        composeTestRule.onNode(isToggleable()).performClick()

        assertEquals(true, enabled)
    }

    @Test
    fun offersOnlyTheParallelCountsTheCardCopesWith() {
        var chosen: Int? = null
        setSettings(SettingsUiState(), onMaxParallelDownloadsChange = { chosen = it })

        composeTestRule.onNodeWithText("2").performClick()
        assertEquals(2, chosen)
        composeTestRule.onNodeWithText("3").assertDoesNotExist()
    }

    @Test
    fun asksForADestinationWhenThereIsNone() {
        var asked = false
        setSettings(SettingsUiState(destinationUri = null), onChooseDestination = { asked = true })

        composeTestRule.onNodeWithText(context.getString(R.string.home_destination_missing)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.home_choose_destination)).performClick()

        assertTrue(asked)
    }

    private fun field(labelId: Int) =
        composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(labelId)))

    private fun setSettings(
        state: SettingsUiState,
        onHostChange: (String) -> Unit = {},
        onMaxParallelDownloadsChange: (Int) -> Unit = {},
        onRegisterInMediaStoreChange: (Boolean) -> Unit = {},
        onChooseDestination: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            FlashAirDownloaderTheme {
                SettingsScreen(
                    state = state,
                    onNavigateBack = {},
                    onHostChange = onHostChange,
                    onTargetDirectoryChange = {},
                    onExtensionFilterChange = {},
                    onMaxParallelDownloadsChange = onMaxParallelDownloadsChange,
                    onRegisterInMediaStoreChange = onRegisterInMediaStoreChange,
                    onChooseDestination = onChooseDestination,
                )
            }
        }
    }

    /** Stands in for DataStore: it lower cases what it is given, and takes its time. */
    private fun setSettingsWithSlowNormalisingStore(echoes: MutableList<String>) {
        composeTestRule.setContent {
            FlashAirDownloaderTheme {
                var stored by remember { mutableStateOf(SettingsUiState()) }
                val scope = rememberCoroutineScope()
                SettingsScreen(
                    state = stored,
                    onNavigateBack = {},
                    onHostChange = {},
                    onTargetDirectoryChange = {},
                    onExtensionFilterChange = { typed ->
                        scope.launch {
                            delay(ECHO_DELAY_MILLIS)
                            stored = stored.copy(extensionFilter = typed.lowercase())
                            echoes += typed.lowercase()
                        }
                    },
                    onMaxParallelDownloadsChange = {},
                    onRegisterInMediaStoreChange = {},
                    onChooseDestination = {},
                )
            }
        }
    }

    private companion object {
        const val ECHO_DELAY_MILLIS = 50L
        const val ECHO_TIMEOUT_MILLIS = 5_000L
    }
}
