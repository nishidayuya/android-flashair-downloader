package io.github.nishidayuya.flashairdownloader.domain.usecase

import io.github.nishidayuya.flashairdownloader.data.flashair.model.FlashAirEntry
import io.github.nishidayuya.flashairdownloader.data.local.DownloadRecordEntity
import io.github.nishidayuya.flashairdownloader.data.local.FakeDownloadRecordDao
import io.github.nishidayuya.flashairdownloader.domain.model.toEpochSeconds
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class BuildSyncPlanUseCaseTest {
    private val records = FakeDownloadRecordDao()
    private val buildSyncPlan = BuildSyncPlanUseCase(records)

    @Test
    fun `plans every file when nothing was downloaded yet`() = runTest {
        val plan = buildSyncPlan(CARD, ROOT, scanOf(entry("IMG_0001.JPG", size = 10), entry("IMG_0002.JPG", size = 20)))

        assertEquals(listOf("/DCIM/IMG_0001.JPG", "/DCIM/IMG_0002.JPG"), plan.files.map { it.path })
        assertEquals(30L, plan.totalBytes)
        assertEquals(0, plan.unchangedCount)
    }

    @Test
    fun `leaves a file alone when size and time still match`() = runTest {
        val entry = entry("IMG_0001.JPG", size = 10)
        records.upsert(recordFor(entry))

        val plan = buildSyncPlan(CARD, ROOT, scanOf(entry))

        assertEquals(emptyList(), plan.files)
        assertEquals(1, plan.unchangedCount)
    }

    @Test
    fun `fetches again when the size changed`() = runTest {
        records.upsert(recordFor(entry("IMG_0001.JPG", size = 10)))

        val plan = buildSyncPlan(CARD, ROOT, scanOf(entry("IMG_0001.JPG", size = 11)))

        assertEquals(listOf("/DCIM/IMG_0001.JPG"), plan.files.map { it.path })
    }

    @Test
    fun `fetches again when the modification time changed`() = runTest {
        records.upsert(recordFor(entry("IMG_0001.JPG", size = 10)))

        val plan = buildSyncPlan(
            CARD,
            ROOT,
            scanOf(entry("IMG_0001.JPG", size = 10, modifiedAt = MODIFIED_AT.plusMinutes(1))),
        )

        assertEquals(listOf("/DCIM/IMG_0001.JPG"), plan.files.map { it.path })
    }

    @Test
    fun `does not mistake another card's record for this one`() = runTest {
        records.upsert(recordFor(entry("IMG_0001.JPG", size = 10)).copy(cardId = "OTHER"))

        val plan = buildSyncPlan(CARD, ROOT, scanOf(entry("IMG_0001.JPG", size = 10)))

        assertEquals(1, plan.files.size)
    }

    @Test
    fun `applies the extension filter`() = runTest {
        val plan = buildSyncPlan(
            cardId = CARD,
            root = ROOT,
            scan = scanOf(
                entry("IMG_0001.JPG", size = 1),
                entry("MOVIE.MOV", size = 2),
                entry("NOTES.TXT", size = 3),
            ),
            extensionFilter = setOf("jpg", "jpeg"),
        )

        assertEquals(listOf("/DCIM/IMG_0001.JPG"), plan.files.map { it.path })
        assertEquals(2, plan.filteredCount)
    }

    private fun scanOf(vararg files: FlashAirEntry) =
        ScanRemoteFilesUseCase.Scan(files = files.toList(), directoriesVisited = 1)

    private fun entry(name: String, size: Long, modifiedAt: LocalDateTime = MODIFIED_AT) = FlashAirEntry(
        directory = ROOT,
        name = name,
        size = size,
        attribute = FlashAirEntry.ATTRIBUTE_ARCHIVE,
        modifiedAt = modifiedAt,
    )

    private fun recordFor(entry: FlashAirEntry) = DownloadRecordEntity(
        cardId = CARD,
        path = entry.path,
        size = entry.size,
        modifiedAtEpoch = entry.modifiedAt?.toEpochSeconds(),
        downloadedAtEpoch = 1_700_000_000,
        localUri = "fake://${entry.name}",
    )

    private companion object {
        const val CARD = "CID"
        const val ROOT = "/DCIM"
        val MODIFIED_AT: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0, 0)
    }
}
