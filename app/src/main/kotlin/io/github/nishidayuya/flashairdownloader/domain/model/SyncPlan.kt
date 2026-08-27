package io.github.nishidayuya.flashairdownloader.domain.model

import io.github.nishidayuya.flashairdownloader.data.flashair.model.FlashAirEntry

/** What a sync run is going to do, worked out before anything is transferred. */
data class SyncPlan(
    val cardId: String,
    /** The directory the walk started at, e.g. "/DCIM". */
    val root: String,
    val files: List<FlashAirEntry>,
    /** Files the card has that this run leaves alone (already downloaded, or filtered out). */
    val unchangedCount: Int,
    val filteredCount: Int,
    val stoppedEarly: ScanStopReason? = null,
) {
    val totalBytes: Long get() = files.sumOf { it.size }
}
