package org.j96.flashairdownloader.data.flashair

import java.time.DateTimeException
import java.time.LocalDateTime

/**
 * The 16 bit FAT date and time fields of a `command.cgi?op=100` listing.
 *
 * The values carry no time zone: they are the card's local wall clock, so they
 * are decoded to a [LocalDateTime] and interpreted in the device's default zone
 * only where an absolute instant is actually needed. See docs/design.md 2.4.
 */
object FatDateTime {
    /** Returns null for the invalid values cards are known to report (month 0, day 0, ...). */
    fun decode(date: Int, time: Int): LocalDateTime? {
        val year = ((date shr 9) and 0x7F) + 1980
        val month = (date shr 5) and 0x0F
        val day = date and 0x1F
        val hour = (time shr 11) and 0x1F
        val minute = (time shr 5) and 0x3F
        val second = (time and 0x1F) * 2
        return try {
            LocalDateTime.of(year, month, day, hour, minute, second)
        } catch (_: DateTimeException) {
            null
        }
    }
}
