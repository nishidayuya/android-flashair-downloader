package io.github.nishidayuya.flashairdownloader.data.flashair

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileListParserTest {
    @Test
    fun `parses a directory listing`() {
        val body = listOf(
            "WLANSD_FILELIST",
            "/DCIM,100__TSB,0,16,9944,129",
            "/DCIM,IMG_0001.JPG,70408,32,17071,28040",
        ).joinToString(CRLF, postfix = CRLF)

        val entries = FileListParser.parse("/DCIM", body)

        assertEquals(2, entries.size)
        val directory = entries[0]
        assertEquals("/DCIM", directory.directory)
        assertEquals("100__TSB", directory.name)
        assertEquals("/DCIM/100__TSB", directory.path)
        assertEquals(0L, directory.size)
        assertTrue(directory.isDirectory)

        val file = entries[1]
        assertEquals("IMG_0001.JPG", file.name)
        assertEquals("/DCIM/IMG_0001.JPG", file.path)
        assertEquals(70408L, file.size)
        assertFalse(file.isDirectory)
        assertEquals(LocalDateTime.of(2013, 5, 15, 13, 44, 16), file.modifiedAt)
    }

    @Test
    fun `keeps commas that belong to the file name`() {
        val body = "WLANSD_FILELIST$CRLF/DCIM,a,b,c.JPG,1234,32,17071,28040$CRLF"

        val entries = FileListParser.parse("/DCIM", body)

        assertEquals(1, entries.size)
        assertEquals("a,b,c.JPG", entries[0].name)
        assertEquals(1234L, entries[0].size)
        assertEquals("/DCIM/a,b,c.JPG", entries[0].path)
    }

    @Test
    fun `reads the empty directory field of a root listing`() {
        val body = "WLANSD_FILELIST$CRLF,DCIM,0,16,17071,28040$CRLF"

        val entries = FileListParser.parse("/", body)

        assertEquals(1, entries.size)
        assertEquals("/", entries[0].directory)
        assertEquals("DCIM", entries[0].name)
        assertEquals("/DCIM", entries[0].path)
    }

    @Test
    fun `accepts a requested directory that ends with a slash`() {
        val body = "WLANSD_FILELIST$CRLF/DCIM,IMG_0001.JPG,1,32,17071,28040$CRLF"

        val entries = FileListParser.parse("/DCIM/", body)

        assertEquals(listOf("/DCIM/IMG_0001.JPG"), entries.map { it.path })
    }

    @Test
    fun `reports an invalid FAT timestamp as null`() {
        val body = "WLANSD_FILELIST$CRLF/DCIM,IMG_0001.JPG,1,32,0,0$CRLF"

        val entries = FileListParser.parse("/DCIM", body)

        assertEquals(1, entries.size)
        assertNull(entries[0].modifiedAt)
    }

    @Test
    fun `handles sizes above four gigabytes`() {
        val size = 5_000_000_000L
        val body = "WLANSD_FILELIST$CRLF/DCIM,MOVIE.MOV,$size,32,17071,28040$CRLF"

        val entries = FileListParser.parse("/DCIM", body)

        assertEquals(size, entries[0].size)
    }

    @Test
    fun `returns nothing for a listing with only the header`() {
        assertEquals(emptyList(), FileListParser.parse("/DCIM", "WLANSD_FILELIST$CRLF"))
    }

    @Test
    fun `returns nothing for an empty body`() {
        assertEquals(emptyList(), FileListParser.parse("/DCIM", ""))
    }

    @Test
    fun `exposes the hidden and system attributes`() {
        val body = listOf(
            "WLANSD_FILELIST",
            "/DCIM,HIDDEN.JPG,1,34,17071,28040",
            "/DCIM,SYSTEM.JPG,1,36,17071,28040",
        ).joinToString(CRLF, postfix = CRLF)

        val entries = FileListParser.parse("/DCIM", body)

        assertTrue(entries[0].isHidden)
        assertFalse(entries[0].isSystem)
        assertTrue(entries[1].isSystem)
        assertFalse(entries[1].isHidden)
    }

    @Test
    fun `drops lines it cannot parse and keeps the rest`() {
        val body = listOf(
            "WLANSD_FILELIST",
            "/DCIM,TRUNCATED.JPG,1,32",
            "/DCIM,NOT_A_NUMBER.JPG,x,32,17071,28040",
            "/OTHER,ELSEWHERE.JPG,1,32,17071,28040",
            "",
            "/DCIM,IMG_0002.JPG,2,32,17071,28040",
        ).joinToString(CRLF, postfix = CRLF)

        val entries = FileListParser.parse("/DCIM", body)

        assertEquals(listOf("IMG_0002.JPG"), entries.map { it.name })
    }

    @Test
    fun `drops the dot entries`() {
        val body = listOf(
            "WLANSD_FILELIST",
            "/DCIM,.,0,16,17071,28040",
            "/DCIM,..,0,16,17071,28040",
        ).joinToString(CRLF, postfix = CRLF)

        assertEquals(emptyList(), FileListParser.parse("/DCIM", body))
    }

    @Test
    fun `normalizes directories`() {
        assertEquals("/", FileListParser.normalizeDirectory(""))
        assertEquals("/", FileListParser.normalizeDirectory("/"))
        assertEquals("/DCIM", FileListParser.normalizeDirectory("DCIM"))
        assertEquals("/DCIM", FileListParser.normalizeDirectory("/DCIM"))
        assertEquals("/DCIM", FileListParser.normalizeDirectory("/DCIM/"))
        assertEquals("/DCIM/100__TSB", FileListParser.normalizeDirectory("/DCIM/100__TSB/"))
    }

    private companion object {
        const val CRLF = "\r\n"
    }
}
