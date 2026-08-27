package io.github.nishidayuya.flashairdownloader.domain.storage

import okio.BufferedSink

/**
 * Where downloads are written to.
 *
 * The interface lives in `domain` and the SAF implementation in `data`, so that
 * the sync engine does not have to know about document trees (docs/design.md 5).
 */
interface DownloadStore {
    /**
     * Opens the destination the user picked.
     *
     * @return null when no destination is configured, or when the permission for
     *   it was revoked -- both of which the UI has to ask the user to fix.
     */
    suspend fun openSession(): DownloadSession?
}

interface DownloadSession {
    /**
     * The files that already exist in [directory] (relative to the destination
     * root), by name and size.
     *
     * Listing a whole directory at once and matching in memory is deliberate:
     * asking a document provider one file at a time is slow enough to dominate a
     * sync of a few thousand photos (docs/design.md 3.4).
     */
    suspend fun list(directory: String): Map<String, Long>

    /**
     * Writes [name] into [directory], creating missing directories on the way.
     *
     * The bytes go to a temporary neighbour first and the file is only given its
     * real name once [body] returns, so an interrupted download cannot be
     * mistaken for a complete one.
     *
     * @return the URI the file ended up at.
     */
    suspend fun write(
        directory: String,
        name: String,
        body: suspend (BufferedSink) -> Unit,
    ): String
}
