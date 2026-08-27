package io.github.nishidayuya.flashairdownloader.ui

import androidx.annotation.StringRes
import io.github.nishidayuya.flashairdownloader.R
import io.github.nishidayuya.flashairdownloader.domain.model.FlashAirFailure

/** The message every screen shows for a given failure. See docs/design.md 7. */
@get:StringRes
val FlashAirFailure.messageRes: Int
    get() = when (this) {
        FlashAirFailure.NOT_CONNECTED -> R.string.error_not_connected
        FlashAirFailure.UNREACHABLE -> R.string.error_unreachable
        FlashAirFailure.CARD_ERROR -> R.string.error_card
        FlashAirFailure.STORAGE_ERROR -> R.string.error_storage
        FlashAirFailure.UNKNOWN -> R.string.error_unknown
    }
