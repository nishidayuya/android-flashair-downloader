package org.j96.flashairdownloader.data.flashair

import org.j96.flashairdownloader.data.flashair.model.FlashAirEntry

/**
 * Parser for the `command.cgi?op=100` response.
 *
 * ```
 * WLANSD_FILELIST
 * /DCIM,100__TSB,0,16,9944,129
 * /DCIM/100__TSB,IMG_0001.JPG,70408,32,17071,28040
 * ```
 *
 * A line is `<directory>,<filename>,<size>,<attribute>,<date>,<time>`, and a
 * FAT file name may contain commas, so neither `split(",")` nor a regexp with a
 * fixed field count can be used. Instead the four numeric fields are cut off
 * from the end, the directory is cut off from the front (its value is known:
 * it is what was passed as `DIR`), and whatever is left is the file name.
 * See docs/design.md 2.2.
 */
object FileListParser {
    private const val HEADER = "WLANSD_FILELIST"
    private const val TRAILING_FIELD_COUNT = 4

    /**
     * @param requestedDirectory the `DIR` parameter of the request, e.g. "/DCIM" or "/".
     * @return the entries in listing order. Lines that cannot be parsed are dropped:
     *   one broken line must not lose the rest of a directory.
     */
    fun parse(requestedDirectory: String, body: String): List<FlashAirEntry> {
        val normalized = normalizeDirectory(requestedDirectory)
        // For DIR=/ the card leaves the directory field empty (docs/design.md 2.2).
        val prefixes = listOf(if (normalized == "/") "" else normalized, normalized).distinct()
        return body.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && it.trim() != HEADER }
            .mapNotNull { parseLine(it, normalized, prefixes) }
            .toList()
    }

    /** Turns "/DCIM/", "DCIM" and "" into the canonical "/DCIM" / "/" form. */
    fun normalizeDirectory(directory: String): String {
        val trimmed = directory.trim().trim('/')
        return if (trimmed.isEmpty()) "/" else "/$trimmed"
    }

    // A parser made of guard clauses: every field that does not look the way the
    // spec says returns null and drops the line, which is exactly the shape
    // detekt's ReturnCount rule counts against.
    @Suppress("ReturnCount")
    private fun parseLine(line: String, directory: String, prefixes: List<String>): FlashAirEntry? {
        val prefix = prefixes.firstOrNull { line.startsWith("$it,") } ?: return null
        val rest = line.substring(prefix.length + 1)

        // Cut the four numeric fields off the end; everything before them is the name.
        val separators = IntArray(TRAILING_FIELD_COUNT)
        var searchTo = rest.length
        for (i in TRAILING_FIELD_COUNT - 1 downTo 0) {
            val comma = rest.lastIndexOf(',', searchTo - 1)
            if (comma < 0) return null
            separators[i] = comma
            searchTo = comma
        }

        val name = rest.substring(0, separators[0])
        if (name.isEmpty() || name == "." || name == "..") return null

        val fields = (0 until TRAILING_FIELD_COUNT).map { i ->
            val from = separators[i] + 1
            val to = if (i == TRAILING_FIELD_COUNT - 1) rest.length else separators[i + 1]
            rest.substring(from, to).trim()
        }
        val size = fields[0].toLongOrNull() ?: return null
        val attribute = fields[1].toIntOrNull() ?: return null
        val date = fields[2].toIntOrNull() ?: return null
        val time = fields[3].toIntOrNull() ?: return null

        return FlashAirEntry(
            directory = directory,
            name = name,
            size = size,
            attribute = attribute,
            modifiedAt = FatDateTime.decode(date, time),
        )
    }
}
