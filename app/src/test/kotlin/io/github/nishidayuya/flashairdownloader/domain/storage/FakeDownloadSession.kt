package io.github.nishidayuya.flashairdownloader.domain.storage

import okio.Buffer
import okio.BufferedSink

/**
 * Keeps written files in memory, keyed by their path relative to the
 * destination root ("100__TSB/IMG_0001.JPG").
 */
class FakeDownloadSession : DownloadSession {
    val files: MutableMap<String, ByteArray> = linkedMapOf()

    override suspend fun list(directory: String): Map<String, Long> =
        files.entries
            .filter { it.key.substringBeforeLast('/', "") == directory.trim('/') }
            .associate { it.key.substringAfterLast('/') to it.value.size.toLong() }

    override suspend fun write(
        directory: String,
        name: String,
        body: suspend (BufferedSink) -> Unit,
    ): String {
        val buffer = Buffer()
        // A failed write leaves nothing behind, like the ".part" file being
        // deleted in the real store.
        body(buffer)
        val key = if (directory.isEmpty()) name else "${directory.trim('/')}/$name"
        files[key] = buffer.readByteArray()
        return "fake://$key"
    }
}
