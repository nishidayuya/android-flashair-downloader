package io.github.nishidayuya.flashairdownloader.data.flashair

import java.io.IOException

/**
 * Failures of a FlashAir HTTP call.
 *
 * They extend [IOException] so that callers which already handle transport
 * errors (timeouts, connection refused) do not need a second catch clause.
 */
sealed class FlashAirException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** No Wi-Fi network that can reach the card is bound. See docs/design.md 3.1. */
class FlashAirNotConnectedException : FlashAirException("No FlashAir Wi-Fi network is available")

/** The card answered, but with an error status. */
class FlashAirHttpException(
    val code: Int,
    val url: String,
) : FlashAirException("HTTP $code for $url")

/** The card answered with something this app cannot make sense of. */
class FlashAirResponseFormatException(
    val body: String,
    val expected: String,
) : FlashAirException("Expected $expected but got \"${body.take(MAX_QUOTED_LENGTH)}\"") {
    private companion object {
        const val MAX_QUOTED_LENGTH = 200
    }
}
