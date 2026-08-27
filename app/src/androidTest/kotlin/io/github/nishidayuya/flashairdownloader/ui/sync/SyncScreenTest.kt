package io.github.nishidayuya.flashairdownloader.ui.sync

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nishidayuya.flashairdownloader.R
import io.github.nishidayuya.flashairdownloader.domain.model.FlashAirFailure
import io.github.nishidayuya.flashairdownloader.domain.model.ScanStopReason
import io.github.nishidayuya.flashairdownloader.domain.model.SyncFailure
import io.github.nishidayuya.flashairdownloader.domain.model.SyncProgress
import io.github.nishidayuya.flashairdownloader.ui.theme.FlashAirDownloaderTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SyncScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsTheFileItIsWorkingOnAndOffersToStop() {
        var cancelled = false
        setSync(
            SyncProgress(
                state = SyncProgress.State.DOWNLOADING,
                totalFiles = 10,
                completedFiles = 3,
                currentFile = "/DCIM/100__TSB/IMG_0004.JPG",
            ),
            onCancel = { cancelled = true },
        )

        composeTestRule.onNodeWithText("/DCIM/100__TSB/IMG_0004.JPG").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sync_files_progress, 3, 10)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sync_cancel)).performClick()

        assertTrue(cancelled)
    }

    @Test
    fun swapsCancelForCloseOnceTheRunIsOver() {
        var closed = false
        setSync(
            SyncProgress(state = SyncProgress.State.FINISHED, totalFiles = 2, completedFiles = 2),
            onClose = { closed = true },
        )

        composeTestRule.onNodeWithText(string(R.string.sync_cancel)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.sync_close)).performClick()

        assertTrue(closed)
    }

    @Test
    fun namesTheFilesItCouldNotFetch() {
        setSync(
            SyncProgress(
                state = SyncProgress.State.FINISHED,
                totalFiles = 2,
                completedFiles = 1,
                failures = listOf(SyncFailure("/DCIM/100__TSB/IMG_0007.JPG", FlashAirFailure.CARD_ERROR)),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.sync_failures_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("/DCIM/100__TSB/IMG_0007.JPG").assertIsDisplayed()
    }

    @Test
    fun admitsWhenItStoppedLookingEarly() {
        setSync(
            SyncProgress(
                state = SyncProgress.State.FINISHED,
                totalFiles = 1,
                completedFiles = 1,
                stoppedEarly = ScanStopReason.FILE_LIMIT,
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.sync_stopped_early_files)).assertIsDisplayed()
    }

    @Test
    fun saysWhyTheWholeRunFailed() {
        setSync(
            SyncProgress(state = SyncProgress.State.FAILED, failure = FlashAirFailure.STORAGE_ERROR),
        )

        composeTestRule.onNodeWithText(string(R.string.error_storage)).assertIsDisplayed()
    }

    @Test
    fun tellsTheUserItIsWaitingForTheCardsWifi() {
        setSync(SyncProgress(state = SyncProgress.State.WAITING_FOR_NETWORK, totalFiles = 5))

        composeTestRule.onNodeWithText(string(R.string.sync_waiting_for_network)).assertIsDisplayed()
    }

    private fun setSync(
        progress: SyncProgress,
        onCancel: () -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            FlashAirDownloaderTheme {
                SyncScreen(progress = progress, onCancel = onCancel, onClose = onClose)
            }
        }
    }

    private fun string(id: Int, vararg formatArgs: Any) = context.getString(id, *formatArgs)
}
