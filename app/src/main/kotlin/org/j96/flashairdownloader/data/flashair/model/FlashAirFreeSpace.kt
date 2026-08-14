package org.j96.flashairdownloader.data.flashair.model

/** Result of `command.cgi?op=140`: `<free sectors>/<total sectors>,<sector size>`. */
data class FlashAirFreeSpace(
    val freeSectors: Long,
    val totalSectors: Long,
    val sectorSize: Int,
) {
    val freeBytes: Long get() = freeSectors * sectorSize
    val totalBytes: Long get() = totalSectors * sectorSize
}
