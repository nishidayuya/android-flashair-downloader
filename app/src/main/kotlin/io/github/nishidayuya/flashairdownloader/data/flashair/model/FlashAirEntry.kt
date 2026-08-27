package io.github.nishidayuya.flashairdownloader.data.flashair.model

import java.time.LocalDateTime

/**
 * One entry of a `command.cgi?op=100` listing.
 *
 * [directory] is the directory the entry lives in, always starting with a slash
 * and never ending with one ("/" for the card's root). See docs/design.md 2.2.
 */
data class FlashAirEntry(
    val directory: String,
    val name: String,
    val size: Long,
    val attribute: Int,
    /** Decoded FAT timestamp, or null when the card reported an invalid one. */
    val modifiedAt: LocalDateTime?,
) {
    val path: String get() = if (directory == "/") "/$name" else "$directory/$name"

    val isDirectory: Boolean get() = attribute and ATTRIBUTE_DIRECTORY != 0
    val isReadOnly: Boolean get() = attribute and ATTRIBUTE_READ_ONLY != 0
    val isHidden: Boolean get() = attribute and ATTRIBUTE_HIDDEN != 0
    val isSystem: Boolean get() = attribute and ATTRIBUTE_SYSTEM != 0
    val isVolumeLabel: Boolean get() = attribute and ATTRIBUTE_VOLUME_LABEL != 0

    companion object {
        // FAT attribute bits, see docs/design.md 2.3.
        const val ATTRIBUTE_READ_ONLY = 0x01
        const val ATTRIBUTE_HIDDEN = 0x02
        const val ATTRIBUTE_SYSTEM = 0x04
        const val ATTRIBUTE_VOLUME_LABEL = 0x08
        const val ATTRIBUTE_DIRECTORY = 0x10
        const val ATTRIBUTE_ARCHIVE = 0x20
    }
}
