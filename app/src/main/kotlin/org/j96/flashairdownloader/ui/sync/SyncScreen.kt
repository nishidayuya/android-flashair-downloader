package org.j96.flashairdownloader.ui.sync

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.j96.flashairdownloader.R
import org.j96.flashairdownloader.domain.model.FlashAirFailure
import org.j96.flashairdownloader.domain.model.ScanStopReason
import org.j96.flashairdownloader.domain.model.SyncFailure
import org.j96.flashairdownloader.domain.model.SyncProgress
import org.j96.flashairdownloader.ui.messageRes
import org.j96.flashairdownloader.ui.theme.FlashAirDownloaderTheme

@Composable
fun SyncRoute(
    onDone: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    SyncScreen(
        progress = progress,
        onCancel = viewModel::cancel,
        onClose = {
            viewModel.acknowledge()
            onDone()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    progress: SyncProgress,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.sync_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = headline(progress), style = MaterialTheme.typography.titleMedium)
            ProgressBar(progress)
            Counters(progress)
            progress.currentFile?.let { path ->
                Text(text = path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
            progress.stoppedEarlyMessage?.let { Text(text = stringResource(it)) }
            Failures(progress.failures, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress.state.isRunning) {
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.sync_cancel))
                    }
                } else {
                    Button(onClick = onClose) {
                        Text(stringResource(R.string.sync_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: SyncProgress) {
    when {
        progress.state == SyncProgress.State.DOWNLOADING && progress.totalFiles > 0 ->
            LinearProgressIndicator(
                progress = {
                    (progress.completedFiles + progress.failures.size).toFloat() / progress.totalFiles
                },
                modifier = Modifier.fillMaxWidth(),
            )

        progress.state.isRunning -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else -> Unit
    }
}

@Composable
private fun Counters(progress: SyncProgress) {
    val context = LocalContext.current
    if (progress.state == SyncProgress.State.SCANNING) {
        Text(pluralStringResource(R.plurals.sync_scanning_count, progress.scannedFiles, progress.scannedFiles))
        progress.scannedDirectory?.let {
            Text(text = it, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        return
    }
    if (progress.totalFiles == 0 && !progress.state.isTerminal) return

    Text(stringResource(R.string.sync_files_progress, progress.completedFiles, progress.totalFiles))
    Text(
        stringResource(
            R.string.sync_bytes_progress,
            Formatter.formatFileSize(context, progress.transferredBytes),
            Formatter.formatFileSize(context, progress.totalBytes),
        ),
    )
    if (progress.state == SyncProgress.State.DOWNLOADING) {
        Text(stringResource(R.string.sync_speed, Formatter.formatFileSize(context, progress.bytesPerSecond)))
    }
    if (progress.alreadyPresentFiles > 0) {
        Text(stringResource(R.string.sync_already_present, progress.alreadyPresentFiles))
    }
    if (progress.unchangedFiles > 0) {
        Text(stringResource(R.string.sync_unchanged, progress.unchangedFiles))
    }
}

@Composable
private fun Failures(failures: List<SyncFailure>, modifier: Modifier = Modifier) {
    if (failures.isEmpty()) return
    Text(
        text = stringResource(R.string.sync_failures_title),
        style = MaterialTheme.typography.titleSmall,
    )
    LazyColumn(modifier = modifier) {
        items(items = failures, key = { it.path }) { failure ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(text = failure.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    text = stringResource(failure.failure.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun headline(progress: SyncProgress): String = when (progress.state) {
    SyncProgress.State.IDLE -> stringResource(R.string.sync_title)
    SyncProgress.State.PROBING -> stringResource(R.string.sync_probing)
    SyncProgress.State.SCANNING -> stringResource(R.string.sync_scanning)
    SyncProgress.State.DOWNLOADING -> stringResource(R.string.sync_notification_title)
    SyncProgress.State.WAITING_FOR_NETWORK -> stringResource(R.string.sync_waiting_for_network)
    SyncProgress.State.CANCELLED -> stringResource(R.string.sync_result_cancelled)
    SyncProgress.State.FAILED ->
        progress.failure?.let { stringResource(it.messageRes) }
            ?: stringResource(R.string.sync_result_failed)

    SyncProgress.State.FINISHED -> if (progress.failures.isEmpty()) {
        pluralStringResource(
            R.plurals.sync_result_done,
            progress.completedFiles,
            progress.completedFiles,
        )
    } else {
        stringResource(R.string.sync_result_partial, progress.completedFiles, progress.failures.size)
    }
}

private val SyncProgress.stoppedEarlyMessage: Int?
    get() = when (stoppedEarly) {
        ScanStopReason.DEPTH_LIMIT -> R.string.sync_stopped_early_depth
        ScanStopReason.FILE_LIMIT -> R.string.sync_stopped_early_files
        null -> null
    }

@Preview(showBackground = true)
@Composable
private fun SyncScreenDownloadingPreview() {
    FlashAirDownloaderTheme {
        SyncScreen(
            progress = SyncProgress(
                state = SyncProgress.State.DOWNLOADING,
                totalFiles = 120,
                completedFiles = 37,
                totalBytes = 900_000_000,
                transferredBytes = 270_000_000,
                currentFile = "/DCIM/100__TSB/IMG_0038.JPG",
                bytesPerSecond = 1_500_000,
                unchangedFiles = 12,
                failures = listOf(SyncFailure("/DCIM/100__TSB/IMG_0007.JPG", FlashAirFailure.CARD_ERROR)),
            ),
            onCancel = {},
            onClose = {},
        )
    }
}
