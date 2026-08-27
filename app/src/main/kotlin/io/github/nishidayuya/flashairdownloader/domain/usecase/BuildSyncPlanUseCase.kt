package io.github.nishidayuya.flashairdownloader.domain.usecase

import io.github.nishidayuya.flashairdownloader.data.flashair.model.FlashAirEntry
import io.github.nishidayuya.flashairdownloader.data.local.DownloadRecordDao
import io.github.nishidayuya.flashairdownloader.domain.model.SyncPlan
import io.github.nishidayuya.flashairdownloader.domain.model.toEpochSeconds
import javax.inject.Inject

/**
 * Works out which of the files on the card this run has to fetch.
 *
 * A file counts as already downloaded when a record for the same card and path
 * has the same size and modification time. If either differs, the card holds
 * something else under that name now and it is fetched again (docs/design.md 6).
 */
class BuildSyncPlanUseCase @Inject constructor(
    private val records: DownloadRecordDao,
) {
    suspend operator fun invoke(
        cardId: String,
        root: String,
        scan: ScanRemoteFilesUseCase.Scan,
        /** Lower case extensions without the dot; empty means every file. */
        extensionFilter: Set<String> = emptySet(),
    ): SyncPlan {
        val known = records.forCard(cardId).associateBy { it.path }
        val matching = scan.files.filter { extensionFilter.isEmpty() || it.extension in extensionFilter }
        val (unchanged, toDownload) = matching.partition { entry ->
            val record = known[entry.path]
            record != null &&
                record.size == entry.size &&
                record.modifiedAtEpoch == entry.modifiedAt?.toEpochSeconds()
        }

        return SyncPlan(
            cardId = cardId,
            root = root,
            files = toDownload,
            unchangedCount = unchanged.size,
            filteredCount = scan.files.size - matching.size,
            stoppedEarly = scan.stoppedEarly,
        )
    }

    private val FlashAirEntry.extension: String
        get() = name.substringAfterLast('.', "").lowercase()
}
