package org.j96.flashairdownloader.ui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.j96.flashairdownloader.R
import org.j96.flashairdownloader.data.flashair.model.FlashAirEntry
import org.j96.flashairdownloader.domain.model.FlashAirFailure
import org.j96.flashairdownloader.ui.theme.FlashAirDownloaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class BrowseScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun listsWhatTheDirectoryHolds() {
        setBrowse(BrowseUiState(directory = "/DCIM", entries = ENTRIES, isLoading = false))

        composeTestRule.onNodeWithText("100__TSB").assertIsDisplayed()
        // A file name with commas in it survives all the way to the screen.
        composeTestRule.onNodeWithText("a,b,c.JPG").assertIsDisplayed()
    }

    @Test
    fun opensDirectoriesButNotFiles() {
        var opened: FlashAirEntry? = null
        setBrowse(BrowseUiState(directory = "/DCIM", entries = ENTRIES, isLoading = false)) {
            opened = it
        }

        composeTestRule.onNodeWithText("a,b,c.JPG").performClick()
        assertNull(opened)

        composeTestRule.onNodeWithText("100__TSB").performClick()
        assertEquals("100__TSB", opened?.name)
    }

    @Test
    fun saysSoWhenTheDirectoryIsEmpty() {
        setBrowse(BrowseUiState(directory = "/DCIM", entries = emptyList(), isLoading = false))

        composeTestRule.onNodeWithText(context.getString(R.string.browse_empty)).assertIsDisplayed()
    }

    @Test
    fun showsWhyItCouldNotList() {
        setBrowse(
            BrowseUiState(
                directory = "/DCIM",
                isLoading = false,
                failure = FlashAirFailure.CARD_ERROR,
            ),
        )

        composeTestRule.onNodeWithText(context.getString(R.string.error_card)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.home_retry)).assertIsDisplayed()
    }

    private fun setBrowse(state: BrowseUiState, onEntryClick: (FlashAirEntry) -> Unit = {}) {
        composeTestRule.setContent {
            FlashAirDownloaderTheme {
                BrowseScreen(
                    state = state,
                    onEntryClick = onEntryClick,
                    onUpClick = {},
                    onRetryClick = {},
                )
            }
        }
    }

    private companion object {
        val MODIFIED_AT: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0, 0)

        val ENTRIES = listOf(
            FlashAirEntry(
                directory = "/DCIM",
                name = "100__TSB",
                size = 0,
                attribute = FlashAirEntry.ATTRIBUTE_DIRECTORY,
                modifiedAt = MODIFIED_AT,
            ),
            FlashAirEntry(
                directory = "/DCIM",
                name = "a,b,c.JPG",
                size = 1_188,
                attribute = FlashAirEntry.ATTRIBUTE_ARCHIVE,
                modifiedAt = MODIFIED_AT,
            ),
        )
    }
}
