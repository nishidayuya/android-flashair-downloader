package org.j96.flashairdownloader.ui.browse

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import org.j96.flashairdownloader.R
import org.j96.flashairdownloader.data.flashair.FlashAirThumbnail
import org.j96.flashairdownloader.data.flashair.model.FlashAirEntry
import org.j96.flashairdownloader.ui.messageRes
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BrowseRoute(
    onNavigateBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Back walks up the card's directory tree first and leaves the screen only
    // once there is nowhere left to go up to.
    BackHandler(enabled = state.canGoUp) { viewModel.goUp() }
    BrowseScreen(
        state = state,
        onEntryClick = viewModel::open,
        onUpClick = { if (!viewModel.goUp()) onNavigateBack() },
        onRetryClick = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onEntryClick: (FlashAirEntry) -> Unit,
    onUpClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = state.directory, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onUpClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.browse_up),
                        )
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
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.failure != null -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(state.failure.messageRes))
                    OutlinedButton(onClick = onRetryClick) {
                        Text(stringResource(R.string.home_retry))
                    }
                }

                state.entries.isEmpty() -> Text(
                    text = stringResource(R.string.browse_empty),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )

                else -> EntryList(entries = state.entries, onEntryClick = onEntryClick)
            }
        }
    }
}

@Composable
private fun EntryList(entries: List<FlashAirEntry>, onEntryClick: (FlashAirEntry) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = entries, key = { it.path }) { entry ->
            EntryRow(entry = entry, onClick = { onEntryClick(entry) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun EntryRow(entry: FlashAirEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val modifiedAt = entry.modifiedAt?.format(DATE_TIME_FORMAT)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isDirectory, onClick = onClick),
        headlineContent = { Text(text = entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = if (entry.isDirectory) {
                    modifiedAt.orEmpty()
                } else {
                    listOfNotNull(Formatter.formatFileSize(context, entry.size), modifiedAt)
                        .joinToString(separator = "   ")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = { EntryIcon(entry) },
    )
}

@Composable
private fun EntryIcon(entry: FlashAirEntry) {
    if (entry.isDirectory || !entry.hasExifThumbnail) {
        EntryFallbackIcon(entry)
        return
    }
    // thumbnail.cgi only works for JPEG, and even then the card may refuse, so
    // both the loading and the failed state show the icon (docs/design.md 2.6).
    SubcomposeAsyncImage(
        model = FlashAirThumbnail(entry.path),
        contentDescription = null,
        modifier = Modifier.size(THUMBNAIL_SIZE),
        contentScale = ContentScale.Crop,
        loading = { EntryFallbackIcon(entry) },
        error = { EntryFallbackIcon(entry) },
    )
}

@Composable
private fun EntryFallbackIcon(entry: FlashAirEntry) {
    Icon(
        imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
        contentDescription = null,
        modifier = Modifier.size(THUMBNAIL_SIZE),
    )
}

private val FlashAirEntry.hasExifThumbnail: Boolean
    get() = name.substringAfterLast('.', "").lowercase() in JPEG_EXTENSIONS

private val JPEG_EXTENSIONS = setOf("jpg", "jpeg")

private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

private val THUMBNAIL_SIZE = 40.dp
