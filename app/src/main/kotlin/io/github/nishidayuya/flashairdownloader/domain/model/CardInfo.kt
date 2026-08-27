package io.github.nishidayuya.flashairdownloader.domain.model

/**
 * What the card tells about itself once it answers.
 *
 * [id] is the CID (`op=120`), which identifies the physical card so that sync
 * state does not get mixed up between two of them. Cards that do not report one
 * fall back to their SSID. See docs/design.md 2.1, 6.
 */
data class CardInfo(
    val id: String,
    val ssid: String,
    val firmwareVersion: String,
    val freeBytes: Long?,
    val totalBytes: Long?,
)
