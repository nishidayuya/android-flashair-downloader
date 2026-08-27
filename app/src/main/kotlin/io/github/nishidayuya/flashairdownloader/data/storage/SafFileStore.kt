package io.github.nishidayuya.flashairdownloader.data.storage

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nishidayuya.flashairdownloader.data.local.SettingsDataStore
import io.github.nishidayuya.flashairdownloader.domain.storage.DownloadSession
import io.github.nishidayuya.flashairdownloader.domain.storage.DownloadStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes downloads into the document tree the user picked with
 * `ACTION_OPEN_DOCUMENT_TREE`.
 *
 * SAF is used rather than MediaStore because the card can hold any kind of file
 * and the user should be able to save to an SD card or any folder they like,
 * without the app declaring storage permissions (docs/design.md 3.4).
 */
@Singleton
class SafFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val mediaStoreRegistrar: MediaStoreRegistrar,
) : DownloadStore {
    override suspend fun openSession(): DownloadSession? {
        val treeUri = settings.destinationTreeUri.first()?.toUri() ?: return null
        // The permission survives reboots only if it was taken persistably, and
        // the user can revoke it at any time.
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        if (!granted) return null
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri) ?: return null
        return SafSession(
            resolver = context.contentResolver,
            treeUri = treeUri,
            rootDocumentId = rootDocumentId,
            // Opt in, because since Android 10 the media provider indexes shared
            // storage by itself; this only makes it happen right away.
            mediaStoreRegistrar = mediaStoreRegistrar.takeIf { settings.registerInMediaStore.first() },
        )
    }

    private fun String.toUri(): Uri? = runCatching { Uri.parse(this) }.getOrNull()
}

// Small helpers around one awkward API; splitting them across classes would only
// spread the document tree bookkeeping around.
@Suppress("TooManyFunctions")
private class SafSession(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val rootDocumentId: String,
    private val mediaStoreRegistrar: MediaStoreRegistrar?,
) : DownloadSession {
    /** Document id per directory path relative to the tree root ("" is the root). */
    private val directoryIds = mutableMapOf("" to rootDocumentId)

    /** Children of a directory, cached because a document query is expensive. */
    private val children = mutableMapOf<String, MutableMap<String, Child>>()

    override suspend fun list(directory: String): Map<String, Long> = withContext(Dispatchers.IO) {
        val id = existingDirectoryId(directory) ?: return@withContext emptyMap()
        childrenOf(directory, id).mapValues { it.value.size }
    }

    override suspend fun write(
        directory: String,
        name: String,
        body: suspend (BufferedSink) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val directoryId = createDirectories(directory)
        val partName = "$name$PART_SUFFIX"
        // A leftover .part from an interrupted run would otherwise be turned into
        // "name.part (1)" by the provider and never be cleaned up.
        childrenOf(directory, directoryId).remove(partName)?.let { stale -> deleteDocument(stale.uri) }

        val partUri = DocumentsContract.createDocument(
            resolver,
            documentUri(directoryId),
            PART_MIME_TYPE,
            partName,
        ) ?: throw IOException("Could not create $partName in $directory")

        // Throwable and not IOException: a cancelled sync has to clean up its
        // half-written file too.
        @Suppress("TooGenericExceptionCaught")
        val finalUri = try {
            val stream = resolver.openOutputStream(partUri, "wt")
                ?: throw IOException("Could not open $partUri for writing")
            stream.sink().buffer().use { sink -> body(sink) }
            DocumentsContract.renameDocument(resolver, partUri, name)
                ?: throw IOException("Could not rename $partName to $name")
        } catch (failure: Throwable) {
            // Leaving a .part behind would look like a half-downloaded file.
            runCatching { deleteDocument(partUri) }
            throw failure
        }

        childrenOf(directory, directoryId)[name] =
            Child(name = name, uri = finalUri, size = documentSize(finalUri), isDirectory = false)
        mediaStoreRegistrar?.register(finalUri)
        finalUri.toString()
    }

    /** Resolves [directory] without creating anything; null when it is not there. */
    private fun existingDirectoryId(directory: String): String? {
        val normalized = normalize(directory)
        directoryIds[normalized]?.let { return it }
        var parent = ""
        var parentId = rootDocumentId
        for (segment in normalized.split('/').filter { it.isNotEmpty() }) {
            val child = childrenOf(parent, parentId)[segment]?.takeIf { it.isDirectory } ?: return null
            parent = if (parent.isEmpty()) segment else "$parent/$segment"
            parentId = DocumentsContract.getDocumentId(child.uri)
            directoryIds[parent] = parentId
        }
        return parentId
    }

    private fun createDirectories(directory: String): String {
        val normalized = normalize(directory)
        directoryIds[normalized]?.let { return it }
        var parent = ""
        var parentId = rootDocumentId
        for (segment in normalized.split('/').filter { it.isNotEmpty() }) {
            val siblings = childrenOf(parent, parentId)
            val existing = siblings[segment]?.takeIf { it.isDirectory }
            val childUri = existing?.uri ?: DocumentsContract.createDocument(
                resolver,
                documentUri(parentId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                segment,
            ) ?: throw IOException("Could not create directory $segment in $parent")
            if (existing == null) {
                siblings[segment] = Child(segment, childUri, size = 0, isDirectory = true)
            }
            parent = if (parent.isEmpty()) segment else "$parent/$segment"
            parentId = DocumentsContract.getDocumentId(childUri)
            directoryIds[parent] = parentId
        }
        return parentId
    }

    private fun childrenOf(directory: String, documentId: String): MutableMap<String, Child> =
        children.getOrPut(normalize(directory)) { queryChildren(documentId) }

    private fun queryChildren(documentId: String): MutableMap<String, Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val result = mutableMapOf<String, Child>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(COLUMN_ID)
                val name = cursor.getString(COLUMN_NAME)
                if (id == null || name == null) continue
                result[name] = Child(
                    name = name,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    size = cursor.longOrZero(COLUMN_SIZE),
                    isDirectory = cursor.getString(COLUMN_MIME_TYPE) == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
        }
        return result
    }

    private fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun documentSize(uri: Uri): Long = try {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.longOrZero(0) else 0L
        } ?: 0L
    } catch (_: FileNotFoundException) {
        0L
    }

    private fun deleteDocument(uri: Uri) {
        DocumentsContract.deleteDocument(resolver, uri)
    }

    private fun Cursor.longOrZero(index: Int): Long = if (isNull(index)) 0L else getLong(index)

    private fun normalize(directory: String): String = directory.trim('/')

    private data class Child(
        val name: String,
        val uri: Uri,
        val size: Long,
        val isDirectory: Boolean,
    )

    private companion object {
        // Indexes into the projection queryChildren asks for.
        const val COLUMN_ID = 0
        const val COLUMN_NAME = 1
        const val COLUMN_MIME_TYPE = 2
        const val COLUMN_SIZE = 3

        const val PART_SUFFIX = ".part"

        /**
         * Document providers append an extension that matches the MIME type, so
         * the ".part" name is created as an unknown type to keep it intact; the
         * rename at the end decides the final name.
         */
        const val PART_MIME_TYPE = "application/octet-stream"
    }
}
