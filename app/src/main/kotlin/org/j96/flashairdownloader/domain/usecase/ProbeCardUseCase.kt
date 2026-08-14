package org.j96.flashairdownloader.domain.usecase

import org.j96.flashairdownloader.data.flashair.FlashAirApi
import org.j96.flashairdownloader.data.flashair.FlashAirHttpException
import org.j96.flashairdownloader.domain.model.CardInfo
import javax.inject.Inject

/**
 * Checks whether the card is reachable and reads what it says about itself.
 *
 * The firmware version comes first: it is the cheapest proof that this really is
 * a FlashAir answering and not some other device on the same address.
 * See docs/design.md 7.
 */
class ProbeCardUseCase @Inject constructor(
    private val api: FlashAirApi,
) {
    suspend operator fun invoke(): CardInfo {
        val firmwareVersion = api.firmwareVersion()
        val ssid = api.ssid()
        val freeSpace = api.freeSpace()
        // Firmware that does not report a CID answers with an error status; the
        // SSID then has to serve as the card's identity (docs/design.md 6).
        val id = try {
            api.cardId().takeIf { it.isNotBlank() }
        } catch (_: FlashAirHttpException) {
            null
        }
        return CardInfo(
            id = id ?: ssid,
            ssid = ssid,
            firmwareVersion = firmwareVersion,
            freeBytes = freeSpace?.freeBytes,
            totalBytes = freeSpace?.totalBytes,
        )
    }
}
