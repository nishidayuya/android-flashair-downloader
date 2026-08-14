package org.j96.flashairdownloader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * The user's settings.
 *
 * The host defaults to the card's factory IP address rather than to its host
 * name: name resolution over a network without internet access fails on a lot of
 * devices (docs/design.md 2, 3.1).
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val host: Flow<String> = context.settingsDataStore.data
        .map { it[KEY_HOST]?.takeIf(String::isNotBlank) ?: DEFAULT_HOST }

    val targetDirectory: Flow<String> = context.settingsDataStore.data
        .map { it[KEY_TARGET_DIRECTORY]?.takeIf(String::isNotBlank) ?: DEFAULT_TARGET_DIRECTORY }

    suspend fun setHost(host: String) {
        context.settingsDataStore.edit { it[KEY_HOST] = host.trim() }
    }

    suspend fun setTargetDirectory(directory: String) {
        context.settingsDataStore.edit { it[KEY_TARGET_DIRECTORY] = directory.trim() }
    }

    companion object {
        const val DEFAULT_HOST = "192.168.0.1"
        const val DEFAULT_TARGET_DIRECTORY = "/DCIM"

        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_TARGET_DIRECTORY = stringPreferencesKey("target_directory")
    }
}
