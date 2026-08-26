package org.j96.flashairdownloader.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
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
    onSyncStarted: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pickDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            // Without this the grant is gone as soon as the process dies
            // (docs/design.md 3.4).
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setDestination(treeUri.toString())
        }
    }

    val startSync = {
        viewModel.startSync()
        onSyncStarted()
    }
    // The foreground service needs a notification to show, so the permission is
    // asked for at the moment it is needed (docs/design.md 3.5).
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startSync() }

    HomeScreen(
        state = state,
        onRetry = viewModel::retry,
        // The app never joins a network by itself in this phase: the user picks
        // the card's SSID in the system panel (docs/design.md 3.3).
        onOpenWifiSettings = { context.startActivity(Intent(Settings.Panel.ACTION_WIFI)) },
        onBrowseClick = onBrowseClick,
        onSettingsClick = onSettingsClick,
        onHistoryClick = onHistoryClick,
        onChooseDestination = {
            // Every stock Android build ships a document picker, but a stripped
            // down one may not, and that must not take the app down with it.
            try {
                pickDestination.launch(null)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.error_storage, Toast.LENGTH_LONG).show()
            }
        },
        onStartSync = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onBrowseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onChooseDestination: () -> Unit,
    onStartSync: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.history_title),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionCard(
                connection = state.connection,
                onRetry = onRetry,
                onOpenWifiSettings = onOpenWifiSettings,
            )
            DestinationCard(
                state = state,
                onChooseDestination = onChooseDestination,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStartSync, enabled = state.canStartSync) {
                    Text(stringResource(R.string.home_start_sync))
                }
                OutlinedButton(
                    onClick = onBrowseClick,
                    enabled = state.connection is ConnectionState.Connected,
                ) {
                    Text(stringResource(R.string.home_browse))
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: ConnectionState,
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
            when (connection) {
                ConnectionState.Disconnected -> {
                    Text(
                        text = stringResource(R.string.home_disconnected),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = onOpenWifiSettings) {
                        Text(stringResource(R.string.home_open_wifi_settings))
                    }
                }

                ConnectionState.Probing -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.home_probing))
                }

                is ConnectionState.Connected -> CardInfoRows(connection.card)

                is ConnectionState.Failed -> {
                    Text(
                        text = stringResource(connection.failure.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // Debug builds only, and deliberately unlocalised: this is
                    // for whoever is diagnosing, not for the user.
                    connection.detail?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onRetry) {
                            Text(stringResource(R.string.home_retry))
                        }
                        if (connection.failure == FlashAirFailure.NOT_CONNECTED) {
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
private fun DestinationCard(state: HomeUiState, onChooseDestination: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledValue(
                label = stringResource(R.string.home_destination),
                value = state.destinationUri?.let(::destinationLabel)
                    ?: stringResource(R.string.home_destination_missing),
            )
            LabeledValue(
                label = stringResource(R.string.home_last_sync),
                value = state.lastSyncEpoch
                    ?.let { DateUtils.getRelativeTimeSpanString(it * MILLIS_PER_SECOND).toString() }
                    ?: stringResource(R.string.home_never_synced),
            )
            OutlinedButton(onClick = onChooseDestination) {
                Text(stringResource(R.string.home_choose_destination))
            }
        }
    }
}

/** The document tree URI as something a person can recognise. */
private fun destinationLabel(treeUri: String): String =
    treeUri.substringAfterLast("%3A").ifEmpty { treeUri }.substringAfterLast('/')

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
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000L

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedPreview() {
    FlashAirDownloaderTheme {
        HomeScreen(
            state = HomeUiState(
                connection = ConnectionState.Connected(
                    CardInfo(
                        id = "0123456789ABCDEF0123456789ABCDEF",
                        ssid = "flashair_ABCDEF",
                        firmwareVersion = "F19BAW3AW2.00.00",
                        freeBytes = 15_000_000_000,
                        totalBytes = 31_000_000_000,
                    ),
                ),
                destinationUri = "content://com.android.externalstorage.documents/tree/primary%3ADCIM",
            ),
            onRetry = {},
            onOpenWifiSettings = {},
            onBrowseClick = {},
            onSettingsClick = {},
            onHistoryClick = {},
            onChooseDestination = {},
            onStartSync = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedPreview() {
    FlashAirDownloaderTheme {
        HomeScreen(
            state = HomeUiState(connection = ConnectionState.Disconnected),
            onRetry = {},
            onOpenWifiSettings = {},
            onBrowseClick = {},
            onSettingsClick = {},
            onHistoryClick = {},
            onChooseDestination = {},
            onStartSync = {},
        )
    }
}
