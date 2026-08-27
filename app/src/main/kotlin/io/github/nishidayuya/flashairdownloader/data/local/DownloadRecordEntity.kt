package io.github.nishidayuya.flashairdownloader.data.local

import androidx.room.Entity

/**
 * A file that has been downloaded from a card.
 *
 * The key is (card, path): the CID keeps two cards from sharing sync state.
 * [size] and [modifiedAtEpoch] are what makes "same file" decidable -- if either
 * changed the card holds a different file under the same name and it is fetched
 * again. See docs/design.md 6.
 */
@Entity(primaryKeys = ["cardId", "path"])
data class DownloadRecordEntity(
    val cardId: String,
    val path: String,
    val size: Long,
    /** The card's FAT timestamp as epoch seconds, or null when it was invalid. */
    val modifiedAtEpoch: Long?,
    val downloadedAtEpoch: Long,
    /** Document URI of the saved copy, when it is known. */
    val localUri: String?,
)
