package io.github.nishidayuya.flashairdownloader.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import io.github.nishidayuya.flashairdownloader.R
import io.github.nishidayuya.flashairdownloader.data.local.SettingsDataStore
import io.github.nishidayuya.flashairdownloader.ui.theme.FlashAirDownloaderTheme

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setDestination(treeUri.toString())
        }
    }

    SettingsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onHostChange = viewModel::setHost,
        onTargetDirectoryChange = viewModel::setTargetDirectory,
        onExtensionFilterChange = viewModel::setExtensionFilter,
        onMaxParallelDownloadsChange = viewModel::setMaxParallelDownloads,
        onRegisterInMediaStoreChange = viewModel::setRegisterInMediaStore,
        onChooseDestination = {
            try {
                pickDestination.launch(null)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.error_storage, Toast.LENGTH_LONG).show()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNavigateBack: () -> Unit,
    onHostChange: (String) -> Unit,
    onTargetDirectoryChange: (String) -> Unit,
    onExtensionFilterChange: (String) -> Unit,
    onMaxParallelDownloadsChange: (Int) -> Unit,
    onRegisterInMediaStoreChange: (Boolean) -> Unit,
    onChooseDestination: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditableSetting(
                value = state.host,
                onValueChange = onHostChange,
                label = stringResource(R.string.settings_host),
                supportingText = stringResource(R.string.settings_host_help),
            )
            EditableSetting(
                value = state.targetDirectory,
                onValueChange = onTargetDirectoryChange,
                label = stringResource(R.string.settings_target_directory),
                supportingText = stringResource(R.string.settings_target_directory_help),
            )
            EditableSetting(
                value = state.extensionFilter,
                onValueChange = onExtensionFilterChange,
                label = stringResource(R.string.settings_extension_filter),
                supportingText = stringResource(R.string.settings_extension_filter_help),
            )

            DestinationSetting(
                destinationUri = state.destinationUri,
                onChooseDestination = onChooseDestination,
            )
            ParallelDownloadsSetting(
                selected = state.maxParallelDownloads,
                onSelect = onMaxParallelDownloadsChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_media_store),
                supportingText = stringResource(R.string.settings_media_store_help),
                checked = state.registerInMediaStore,
                onCheckedChange = onRegisterInMediaStoreChange,
            )
        }
    }
}

@Composable
private fun DestinationSetting(destinationUri: String?, onChooseDestination: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_destination),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = destinationUri ?: stringResource(R.string.home_destination_missing),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(onClick = onChooseDestination) {
            Text(stringResource(R.string.home_choose_destination))
        }
    }
}

@Composable
private fun ParallelDownloadsSetting(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_parallel_downloads),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_parallel_downloads_help),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (count in 1..SettingsDataStore.MAX_PARALLEL_DOWNLOADS) {
                FilterChip(
                    selected = selected == count,
                    onClick = { onSelect(count) },
                    label = { Text(count.toString()) },
                )
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(WEIGHT_OF_LABEL)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = supportingText, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A text setting.
 *
 * The field shows the stored value until the first keystroke and is the user's
 * own from then on. Re-seeding it from the stored value on every change would
 * fight the typing: each keystroke goes to DataStore, comes back through the
 * flow -- normalised, and not necessarily in order -- and would overwrite what
 * has been typed since.
 */
@Composable
private fun EditableSetting(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
) {
    var typed by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(
        value = typed ?: value,
        onValueChange = {
            typed = it
            onValueChange(it)
        },
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val WEIGHT_OF_LABEL = 0.75f

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    FlashAirDownloaderTheme {
        SettingsScreen(
            state = SettingsUiState(extensionFilter = "jpg, mov"),
            onNavigateBack = {},
            onHostChange = {},
            onTargetDirectoryChange = {},
            onExtensionFilterChange = {},
            onMaxParallelDownloadsChange = {},
            onRegisterInMediaStoreChange = {},
            onChooseDestination = {},
        )
    }
}
