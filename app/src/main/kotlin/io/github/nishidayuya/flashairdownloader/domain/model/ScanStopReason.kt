package io.github.nishidayuya.flashairdownloader.domain.model

/**
 * Why a walk of the card stopped before it had seen everything. The user is told
 * about it, because "no new files" and "we stopped looking" are different things.
 */
enum class ScanStopReason { DEPTH_LIMIT, FILE_LIMIT }
