package org.j96.flashairdownloader.ui.history

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.j96.flashairdownloader.data.local.DownloadRecordEntity
import org.j96.flashairdownloader.domain.model.FlashAirFailure
import org.j96.flashairdownloader.domain.model.SyncFailure
import org.j96.flashairdownloader.ui.messageRes
import org.j96.flashairdownloader.ui.theme.FlashAirDownloaderTheme

@Composable
fun HistoryRoute(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onResetRecords = viewModel::resetRecords,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onNavigateBack: () -> Unit,
    onResetRecords: () -> Unit,
) {
    var askingToReset by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { askingToReset = true },
                        enabled = state.records.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.history_reset))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.records.isEmpty() && state.failures.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            } else {
                HistoryList(state)
            }
        }
    }

    if (askingToReset) {
        ResetDialog(
            onConfirm = {
                askingToReset = false
                onResetRecords()
            },
            onDismiss = { askingToReset = false },
        )
    }
}

@Composable
private fun HistoryList(state: HistoryUiState) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.failures.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.sync_failures_title))
            }
            items(items = state.failures, key = { "failure:${it.path}" }) { failure ->
                ListItem(
                    headlineContent = {
                        Text(text = failure.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    },
                    supportingContent = { Text(stringResource(failure.failure.messageRes)) },
                )
                HorizontalDivider()
            }
        }

        item {
            SectionHeader(stringResource(R.string.history_downloaded))
        }
        items(items = state.records, key = { it.path }) { record ->
            ListItem(
                headlineContent = {
                    Text(text = record.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                },
                supportingContent = {
                    Text(
                        text = listOf(
                            Formatter.formatFileSize(context, record.size),
                            DateUtils.getRelativeTimeSpanString(record.downloadedAtEpoch * MILLIS_PER_SECOND),
                        ).joinToString("   "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_reset)) },
        text = { Text(stringResource(R.string.history_reset_explanation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.history_reset)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_cancel)) }
        },
    )
}

private const val MILLIS_PER_SECOND = 1_000L

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    FlashAirDownloaderTheme {
        HistoryScreen(
            state = HistoryUiState(
                cardId = "CID",
                records = listOf(
                    DownloadRecordEntity(
                        cardId = "CID",
                        path = "/DCIM/100__TSB/IMG_0001.JPG",
                        size = 2_188,
                        modifiedAtEpoch = 1_700_000_000,
                        downloadedAtEpoch = 1_700_000_100,
                        localUri = null,
                    ),
                ),
                failures = listOf(SyncFailure("/DCIM/100__TSB/IMG_0007.JPG", FlashAirFailure.CARD_ERROR)),
            ),
            onNavigateBack = {},
            onResetRecords = {},
        )
    }
}
