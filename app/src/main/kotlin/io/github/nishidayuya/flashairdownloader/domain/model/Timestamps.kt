package io.github.nishidayuya.flashairdownloader.domain.model

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A FAT timestamp carries no time zone, so the card's wall clock is read as the
 * device's local time whenever an absolute instant is needed (docs/design.md 2.4).
 */
fun LocalDateTime.toEpochSeconds(): Long = atZone(ZoneId.systemDefault()).toEpochSecond()
