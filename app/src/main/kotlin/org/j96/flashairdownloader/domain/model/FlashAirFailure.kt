package org.j96.flashairdownloader.domain.model

import org.j96.flashairdownloader.data.flashair.FlashAirHttpException
import org.j96.flashairdownloader.data.flashair.FlashAirNotConnectedException
import org.j96.flashairdownloader.data.flashair.FlashAirResponseFormatException
import java.io.IOException

/**
 * What went wrong, at the level of detail the user can act on.
 * See docs/design.md 7.
 */
enum class FlashAirFailure {
    /** No Wi-Fi that could reach the card: the user has to connect to it. */
    NOT_CONNECTED,

    /** The network is there but the card is not answering: check the host setting. */
    UNREACHABLE,

    /** The card answered with an error or with nonsense. */
    CARD_ERROR,

    /** Writing to the chosen destination failed. */
    STORAGE_ERROR,

    UNKNOWN,
    ;

    companion object {
        fun of(throwable: Throwable): FlashAirFailure = when (throwable) {
            is FlashAirNotConnectedException -> NOT_CONNECTED
            is FlashAirHttpException, is FlashAirResponseFormatException -> CARD_ERROR
            is IOException -> UNREACHABLE
            else -> UNKNOWN
        }
    }
}
