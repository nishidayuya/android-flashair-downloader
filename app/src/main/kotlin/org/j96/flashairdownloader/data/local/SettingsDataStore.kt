package org.j96.flashairdownloader.data.local

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Everything the user can configure. See docs/design.md 8.
 *
 * The host defaults to the card's factory IP address rather than to its host
 * name: name resolution over a network without internet access fails on a lot of
 * devices (docs/design.md 2, 3.1).
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val data: Flow<Preferences> get() = context.settingsDataStore.data

    val host: Flow<String> = data.map { it[KEY_HOST]?.takeIf(String::isNotBlank) ?: DEFAULT_HOST }

    val targetDirectory: Flow<String> = data.map {
        it[KEY_TARGET_DIRECTORY]?.takeIf(String::isNotBlank) ?: DEFAULT_TARGET_DIRECTORY
    }

    /** The SAF tree the user picked, or null while there is none. */
    val destinationTreeUri: Flow<String?> = data.map { it[KEY_DESTINATION_TREE_URI]?.takeIf(String::isNotBlank) }

    /**
     * Lower case extensions to download, without the dot. Empty means everything.
     */
    val extensionFilter: Flow<Set<String>> = data.map { preferences ->
        preferences[KEY_EXTENSION_FILTER]
            .orEmpty()
            .split(',')
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** How many files to fetch at once. The card's HTTP server tolerates very few. */
    val maxParallelDownloads: Flow<Int> = data.map {
        (it[KEY_MAX_PARALLEL_DOWNLOADS] ?: DEFAULT_MAX_PARALLEL_DOWNLOADS)
            .coerceIn(1, MAX_PARALLEL_DOWNLOADS)
    }

    /** The card talked to most recently, so history works while it is away. */
    val lastCardId: Flow<String?> = data.map { it[KEY_LAST_CARD_ID]?.takeIf(String::isNotBlank) }

    /** Whether saved photos and videos are also registered with MediaStore. */
    val registerInMediaStore: Flow<Boolean> = data.map { it[KEY_REGISTER_IN_MEDIA_STORE] ?: false }

    suspend fun setHost(host: String) = edit { it[KEY_HOST] = host.trim() }

    suspend fun setTargetDirectory(directory: String) = edit { it[KEY_TARGET_DIRECTORY] = directory.trim() }

    suspend fun setDestinationTreeUri(uri: String) = edit { it[KEY_DESTINATION_TREE_URI] = uri }

    suspend fun setExtensionFilter(extensions: Set<String>) = edit {
        it[KEY_EXTENSION_FILTER] = extensions.joinToString(",")
    }

    suspend fun setMaxParallelDownloads(count: Int) = edit {
        it[KEY_MAX_PARALLEL_DOWNLOADS] = count.coerceIn(1, MAX_PARALLEL_DOWNLOADS)
    }

    suspend fun setLastCardId(cardId: String) = edit { it[KEY_LAST_CARD_ID] = cardId }

    suspend fun setRegisterInMediaStore(enabled: Boolean) = edit { it[KEY_REGISTER_IN_MEDIA_STORE] = enabled }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    companion object {
        const val DEFAULT_HOST = "192.168.0.1"
        const val DEFAULT_TARGET_DIRECTORY = "/DCIM"
        const val DEFAULT_MAX_PARALLEL_DOWNLOADS = 1

        /** docs/design.md 2.5: the card's HTTP server handles two or three connections. */
        const val MAX_PARALLEL_DOWNLOADS = 2

        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_TARGET_DIRECTORY = stringPreferencesKey("target_directory")
        private val KEY_DESTINATION_TREE_URI = stringPreferencesKey("destination_tree_uri")
        private val KEY_EXTENSION_FILTER = stringPreferencesKey("extension_filter")
        private val KEY_MAX_PARALLEL_DOWNLOADS = intPreferencesKey("max_parallel_downloads")
        private val KEY_REGISTER_IN_MEDIA_STORE = booleanPreferencesKey("register_in_media_store")
        private val KEY_LAST_CARD_ID = stringPreferencesKey("last_card_id")
    }
}
