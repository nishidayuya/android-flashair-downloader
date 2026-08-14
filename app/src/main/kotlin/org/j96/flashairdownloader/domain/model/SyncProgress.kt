package org.j96.flashairdownloader.domain.model

/**
 * The single source of truth for what a sync run is doing.
 *
 * Both the notification and the UI read this same value, so they can never
 * disagree (docs/design.md 7).
 */
data class SyncProgress(
    val state: State = State.IDLE,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    /** Files the plan asked for that turned out to be in the destination already. */
    val alreadyPresentFiles: Int = 0,
    /** Files the card has that the plan left out because they were downloaded before. */
    val unchangedFiles: Int = 0,
    val totalBytes: Long = 0,
    val transferredBytes: Long = 0,
    val currentFile: String? = null,
    val bytesPerSecond: Long = 0,
    val failures: List<SyncFailure> = emptyList(),
    /** Set when the run itself could not go on, as opposed to a single file failing. */
    val failure: FlashAirFailure? = null,
    val scannedDirectory: String? = null,
    val scannedFiles: Int = 0,
    /** Set when the walk of the card hit a limit, so the result is not the whole card. */
    val stoppedEarly: ScanStopReason? = null,
) {
    enum class State {
        IDLE,
        PROBING,
        SCANNING,
        DOWNLOADING,

        /** The card's Wi-Fi went away mid-run; the transfer continues when it is back. */
        WAITING_FOR_NETWORK,
        FINISHED,
        CANCELLED,
        FAILED,
        ;

        val isRunning: Boolean
            get() = this == PROBING || this == SCANNING || this == DOWNLOADING || this == WAITING_FOR_NETWORK

        val isTerminal: Boolean get() = this == FINISHED || this == CANCELLED || this == FAILED
    }

    val remainingFiles: Int get() = (totalFiles - completedFiles - failures.size).coerceAtLeast(0)
}

data class SyncFailure(val path: String, val failure: FlashAirFailure)
