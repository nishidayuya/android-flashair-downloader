package org.j96.flashairdownloader.domain.usecase

import org.j96.flashairdownloader.data.flashair.FlashAirApi
import org.j96.flashairdownloader.data.flashair.model.FlashAirEntry
import org.j96.flashairdownloader.domain.model.ScanStopReason
import javax.inject.Inject

/**
 * Walks a directory tree on the card.
 *
 * Depth and file count are capped: a card with a pathological directory
 * structure (or a firmware that reports a directory as its own child) must not
 * turn a sync into an endless walk. Hidden, system and volume label entries are
 * skipped. See docs/design.md 7.
 */
class ScanRemoteFilesUseCase @Inject constructor(
    private val api: FlashAirApi,
) {
    data class Scan(
        val files: List<FlashAirEntry>,
        val directoriesVisited: Int,
        /** Set when a limit cut the walk short, so the UI can say so. */
        val stoppedEarly: ScanStopReason? = null,
    )

    suspend operator fun invoke(
        root: String,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxFiles: Int = DEFAULT_MAX_FILES,
        onDirectoryScanned: (directory: String, filesSoFar: Int) -> Unit = { _, _ -> },
    ): Scan {
        val files = mutableListOf<FlashAirEntry>()
        val visited = mutableSetOf<String>()
        // Depth first in listing order, so the result reads like the tree does.
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.addLast(root to 0)
        var stoppedEarly: ScanStopReason? = null
        var directoriesVisited = 0

        while (pending.isNotEmpty() && stoppedEarly != ScanStopReason.FILE_LIMIT) {
            val (directory, depth) = pending.removeLast()
            // A card that lists a directory as its own descendant would loop.
            if (!visited.add(directory)) continue
            directoriesVisited++

            val entries = api.listEntries(directory).filterNot { it.isHidden || it.isSystem || it.isVolumeLabel }
            files += entries.filterNot { it.isDirectory }
            onDirectoryScanned(directory, files.size)

            val subdirectories = entries.filter { it.isDirectory }
            when {
                files.size >= maxFiles -> stoppedEarly = ScanStopReason.FILE_LIMIT
                subdirectories.isEmpty() -> Unit
                depth + 1 > maxDepth -> stoppedEarly = stoppedEarly ?: ScanStopReason.DEPTH_LIMIT
                // Reversed, because the stack pops the last one first.
                else -> subdirectories.asReversed().forEach { pending.addLast(it.path to depth + 1) }
            }
        }

        return Scan(
            files = files.take(maxFiles),
            directoriesVisited = directoriesVisited,
            stoppedEarly = stoppedEarly,
        )
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 8
        const val DEFAULT_MAX_FILES = 20_000
    }
}
