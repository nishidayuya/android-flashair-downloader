package org.j96.flashairdownloader.data.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks MediaStore to index a file that was just saved, so that photos and videos
 * show up in the gallery without waiting for the next media scan
 * (docs/design.md 3.4, Phase 5).
 *
 * Only the primary shared volume can be handled: MediaStore takes a path, while
 * SAF hands out document URIs, and the mapping between the two is only known for
 * the built-in storage provider. Anywhere else (an SD card, a cloud provider)
 * this does nothing, which costs nothing either -- since Android 10 the media
 * provider indexes shared storage by itself, just not necessarily at once.
 */
@Singleton
class MediaStoreRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun register(documentUri: Uri) {
        val file = fileFor(documentUri) ?: return
        try {
            MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
        } catch (_: SecurityException) {
            // Nothing to do: the file is saved, only the index is behind.
        }
    }

    private fun fileFor(documentUri: Uri): File? {
        val documentId = runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull() ?: return null
        val (volume, relativePath) = documentId.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
        if (volume != PRIMARY_VOLUME) return null
        val file = File(Environment.getExternalStorageDirectory(), relativePath)
        return file.takeIf { it.exists() }
    }

    private companion object {
        const val PRIMARY_VOLUME = "primary"
    }
}
