package org.j96.flashairdownloader.ui.home

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.j96.flashairdownloader.R
import org.j96.flashairdownloader.domain.model.CardInfo
import org.j96.flashairdownloader.domain.model.FlashAirFailure
import org.j96.flashairdownloader.ui.messageRes
import org.j96.flashairdownloader.ui.theme.FlashAirDownloaderTheme

@Composable
fun HomeRoute(
    onBrowseClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRetry = viewModel::retry,
        // The app never joins a network by itself in this phase: the user picks
        // the card's SSID in the system panel (docs/design.md 3.3).
        onOpenWifiSettings = { context.startActivity(Intent(Settings.Panel.ACTION_WIFI)) },
        onBrowseClick = onBrowseClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onBrowseClick: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionCard(
                state = state,
                onRetry = onRetry,
                onOpenWifiSettings = onOpenWifiSettings,
            )
            Button(
                onClick = onBrowseClick,
                enabled = state is HomeUiState.Connected,
            ) {
                Text(stringResource(R.string.home_browse))
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenWifiSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                HomeUiState.Disconnected -> {
                    Text(
                        text = stringResource(R.string.home_disconnected),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = onOpenWifiSettings) {
                        Text(stringResource(R.string.home_open_wifi_settings))
                    }
                }

                HomeUiState.Probing -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.home_probing))
                }

                is HomeUiState.Connected -> CardInfoRows(state.card)

                is HomeUiState.Failed -> {
                    Text(
                        text = stringResource(state.failure.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onRetry) {
                            Text(stringResource(R.string.home_retry))
                        }
                        if (state.failure == FlashAirFailure.NOT_CONNECTED) {
                            OutlinedButton(onClick = onOpenWifiSettings) {
                                Text(stringResource(R.string.home_open_wifi_settings))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardInfoRows(card: CardInfo) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.home_connected),
        style = MaterialTheme.typography.titleMedium,
    )
    LabeledValue(stringResource(R.string.card_ssid), card.ssid)
    LabeledValue(stringResource(R.string.card_firmware_version), card.firmwareVersion)
    LabeledValue(
        label = stringResource(R.string.card_free_space),
        value = if (card.freeBytes != null && card.totalBytes != null) {
            stringResource(
                R.string.card_free_space_value,
                Formatter.formatFileSize(context, card.freeBytes),
                Formatter.formatFileSize(context, card.totalBytes),
            )
        } else {
            stringResource(R.string.value_unknown)
        },
    )
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedPreview() {
    FlashAirDownloaderTheme {
        HomeScreen(
            state = HomeUiState.Connected(
                CardInfo(
                    id = "0123456789ABCDEF0123456789ABCDEF",
                    ssid = "flashair_ABCDEF",
                    firmwareVersion = "F19BAW3AW2.00.00",
                    freeBytes = 15_000_000_000,
                    totalBytes = 31_000_000_000,
                ),
            ),
            onRetry = {},
            onOpenWifiSettings = {},
            onBrowseClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedPreview() {
    FlashAirDownloaderTheme {
        HomeScreen(
            state = HomeUiState.Disconnected,
            onRetry = {},
            onOpenWifiSettings = {},
            onBrowseClick = {},
        )
    }
}
