package org.j96.flashairdownloader.data.flashair

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FatDateTimeTest {
    @Test
    fun `decodes the example from the FlashAir documentation`() {
        // 17071 = 2013-05-15, 28040 = 13:44:16.
        assertEquals(
            LocalDateTime.of(2013, 5, 15, 13, 44, 16),
            FatDateTime.decode(17071, 28040),
        )
    }

    @Test
    fun `decodes the first representable moment`() {
        // year 1980, month 1, day 1, 00:00:00
        assertEquals(
            LocalDateTime.of(1980, 1, 1, 0, 0, 0),
            FatDateTime.decode(date = (1 shl 5) or 1, time = 0),
        )
    }

    @Test
    fun `decodes seconds in two second steps`() {
        val time = (23 shl 11) or (59 shl 5) or 29
        assertEquals(
            LocalDateTime.of(1980, 1, 1, 23, 59, 58),
            FatDateTime.decode(date = (1 shl 5) or 1, time = time),
        )
    }

    @Test
    fun `returns null for an all zero date`() {
        assertNull(FatDateTime.decode(0, 0))
    }

    @Test
    fun `returns null when the month is out of range`() {
        // month 13 does not exist.
        assertNull(FatDateTime.decode(date = (13 shl 5) or 1, time = 0))
    }

    @Test
    fun `returns null when the day is zero`() {
        assertNull(FatDateTime.decode(date = (5 shl 5), time = 0))
    }

    @Test
    fun `returns null when the seconds field is out of range`() {
        // 31 * 2 = 62 seconds.
        assertNull(FatDateTime.decode(date = (1 shl 5) or 1, time = 31))
    }
}
