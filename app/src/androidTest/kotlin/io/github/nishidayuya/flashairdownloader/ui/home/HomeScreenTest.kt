package io.github.nishidayuya.flashairdownloader.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nishidayuya.flashairdownloader.R
import io.github.nishidayuya.flashairdownloader.domain.model.CardInfo
import io.github.nishidayuya.flashairdownloader.domain.model.FlashAirFailure
import io.github.nishidayuya.flashairdownloader.domain.model.SyncProgress
import io.github.nishidayuya.flashairdownloader.ui.theme.FlashAirDownloaderTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun offersTheWifiPanelWhileNothingIsConnected() {
        var openedWifiSettings = false
        setHome(HomeUiState(connection = ConnectionState.Disconnected)) {
            openedWifiSettings = true
        }

        composeTestRule.onNodeWithText(string(R.string.home_disconnected)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.home_open_wifi_settings)).performClick()

        assertTrue(openedWifiSettings)
    }

    @Test
    fun showsWhatTheCardSaidAboutItself() {
        setHome(HomeUiState(connection = ConnectionState.Connected(CARD)))

        composeTestRule.onNodeWithText(CARD.ssid).assertIsDisplayed()
        composeTestRule.onNodeWithText(CARD.firmwareVersion).assertIsDisplayed()
    }

    @Test
    fun keepsSyncOutOfReachUntilThereIsSomewhereToPutTheFiles() {
        setHome(HomeUiState(connection = ConnectionState.Connected(CARD)))

        composeTestRule.onNodeWithText(string(R.string.home_start_sync)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.home_destination_missing)).assertIsDisplayed()
    }

    @Test
    fun allowsSyncOnceConnectedAndPointedSomewhere() {
        setHome(
            HomeUiState(
                connection = ConnectionState.Connected(CARD),
                destinationUri = "content://com.android.externalstorage.documents/tree/primary%3AFlashAir",
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.home_start_sync)).assertIsEnabled()
    }

    @Test
    fun doesNotStartASecondRunWhileOneIsGoing() {
        setHome(
            HomeUiState(
                connection = ConnectionState.Connected(CARD),
                destinationUri = "content://tree/primary%3AFlashAir",
                syncState = SyncProgress.State.DOWNLOADING,
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.home_start_sync)).assertIsNotEnabled()
    }

    @Test
    fun explainsAFailureAndOffersToTryAgain() {
        var retried = false
        composeTestRule.setContent {
            FlashAirDownloaderTheme {
                HomeScreen(
                    state = HomeUiState(connection = ConnectionState.Failed(FlashAirFailure.UNREACHABLE)),
                    onRetry = { retried = true },
                    onOpenWifiSettings = {},
                    onBrowseClick = {},
                    onSettingsClick = {},
                    onHistoryClick = {},
                    onChooseDestination = {},
                    onStartSync = {},
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.error_unreachable)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.home_retry)).performClick()

        assertTrue(retried)
    }

    private fun setHome(state: HomeUiState, onOpenWifiSettings: () -> Unit = {}) {
        composeTestRule.setContent {
            FlashAirDownloaderTheme {
                HomeScreen(
                    state = state,
                    onRetry = {},
                    onOpenWifiSettings = onOpenWifiSettings,
                    onBrowseClick = {},
                    onSettingsClick = {},
                    onHistoryClick = {},
                    onChooseDestination = {},
                    onStartSync = {},
                )
            }
        }
    }

    private fun string(id: Int) = context.getString(id)

    private companion object {
        val CARD = CardInfo(
            id = "0123456789ABCDEF0123456789ABCDEF",
            ssid = "flashair_ABCDEF",
            firmwareVersion = "F19BAW3AW2.00.00",
            freeBytes = 15_000_000_000,
            totalBytes = 31_000_000_000,
        )
    }
}
