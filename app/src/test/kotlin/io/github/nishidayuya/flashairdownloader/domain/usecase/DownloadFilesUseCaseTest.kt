package io.github.nishidayuya.flashairdownloader.domain.usecase

import io.github.nishidayuya.flashairdownloader.data.flashair.FakeFlashAirCard
import io.github.nishidayuya.flashairdownloader.data.flashair.FlashAirApi
import io.github.nishidayuya.flashairdownloader.data.flashair.model.FlashAirEntry
import io.github.nishidayuya.flashairdownloader.data.local.FakeDownloadRecordDao
import io.github.nishidayuya.flashairdownloader.domain.model.SyncPlan
import io.github.nishidayuya.flashairdownloader.domain.model.toEpochSeconds
import io.github.nishidayuya.flashairdownloader.domain.storage.FakeDownloadSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadFilesUseCaseTest {
    private val card = FakeFlashAirCard()
    private val records = FakeDownloadRecordDao()
    private val session = FakeDownloadSession()
    private val clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC)
    private val downloadFiles = DownloadFilesUseCase(FlashAirApi(card), records, clock)

    @Test
    fun `mirrors the card's folders below the root`() = runTest {
        card.file("/DCIM/100__TSB/IMG_0001.JPG", "one")
        card.file("/DCIM/README.TXT", "two")
        val plan = planOf(
            entry("/DCIM/100__TSB", "IMG_0001.JPG", size = 3),
            entry("/DCIM", "README.TXT", size = 3),
        )

        val result = downloadFiles(plan, session)

        assertEquals(2, result.downloaded)
        assertEquals("one", session.files["100__TSB/IMG_0001.JPG"]?.decodeToString())
        assertEquals("two", session.files["README.TXT"]?.decodeToString())
    }

    @Test
    fun `records every file it downloaded`() = runTest {
        card.file("/DCIM/IMG_0001.JPG", "content")
        val entry = entry("/DCIM", "IMG_0001.JPG", size = 7)

        downloadFiles(planOf(entry), session)

        val record = records.all.single()
        assertEquals("/DCIM/IMG_0001.JPG", record.path)
        assertEquals(7L, record.size)
        assertEquals(entry.modifiedAt?.toEpochSeconds(), record.modifiedAtEpoch)
        assertEquals(1_700_000_000L, record.downloadedAtEpoch)
        assertEquals("fake://IMG_0001.JPG", record.localUri)
    }

    @Test
    fun `keeps a file that is already in the destination`() = runTest {
        session.files["IMG_0001.JPG"] = "content".toByteArray()
        // Nothing is registered on the card, so any request would fail.

        val result = downloadFiles(planOf(entry("/DCIM", "IMG_0001.JPG", size = 7)), session)

        assertEquals(0, result.downloaded)
        assertEquals(1, result.alreadyPresent)
        assertEquals(1, records.all.size)
    }

    @Test
    fun `fetches again when the destination copy has a different size`() = runTest {
        session.files["IMG_0001.JPG"] = "old".toByteArray()
        card.file("/DCIM/IMG_0001.JPG", "content")

        val result = downloadFiles(planOf(entry("/DCIM", "IMG_0001.JPG", size = 7)), session)

        assertEquals(1, result.downloaded)
        assertEquals("content", session.files["IMG_0001.JPG"]?.decodeToString())
    }

    @Test
    fun `carries on after a file that cannot be fetched`() = runTest {
        card.file("/DCIM/IMG_0002.JPG", "two")
        val plan = planOf(
            // Not registered on the card: every attempt answers 404.
            entry("/DCIM", "MISSING.JPG", size = 1),
            entry("/DCIM", "IMG_0002.JPG", size = 3),
        )

        val result = downloadFiles(plan, session)

        assertEquals(1, result.downloaded)
        assertEquals(listOf("/DCIM/MISSING.JPG"), result.failures.map { it.path })
        assertEquals("two", session.files["IMG_0002.JPG"]?.decodeToString())
        assertTrue(session.files.keys.none { it.startsWith("MISSING") })
        assertEquals(listOf("/DCIM/IMG_0002.JPG"), records.all.map { it.path })
    }

    @Test
    fun `retries a failing file three times`() = runTest {
        downloadFiles(planOf(entry("/DCIM", "MISSING.JPG", size = 1)), session)

        assertEquals(3, card.requests.count { it == "/DCIM/MISSING.JPG" })
    }

    @Test
    fun `reports the bytes it transferred`() = runTest {
        card.file("/DCIM/IMG_0001.JPG", "0123456789")
        val transferred = mutableListOf<Long>()
        val events = mutableListOf<String>()

        downloadFiles(planOf(entry("/DCIM", "IMG_0001.JPG", size = 10)), session) { event ->
            when (event) {
                is DownloadFilesUseCase.Event.Transferred -> transferred += event.bytes
                is DownloadFilesUseCase.Event.Started -> events += "started"
                is DownloadFilesUseCase.Event.Finished -> events += "finished"
                is DownloadFilesUseCase.Event.Failed -> events += "failed"
            }
        }

        assertEquals(10L, transferred.sum())
        assertEquals(listOf("started", "finished"), events)
    }

    @Test
    fun `waits for the connection before each file`() = runTest {
        card.file("/DCIM/IMG_0001.JPG", "one")
        card.file("/DCIM/IMG_0002.JPG", "two")
        var waits = 0

        downloadFiles(
            plan = planOf(entry("/DCIM", "IMG_0001.JPG", size = 3), entry("/DCIM", "IMG_0002.JPG", size = 3)),
            session = session,
            awaitConnection = { waits++ },
        )

        assertEquals(2, waits)
    }

    private fun planOf(vararg files: FlashAirEntry) = SyncPlan(
        cardId = CARD,
        root = "/DCIM",
        files = files.toList(),
        unchangedCount = 0,
        filteredCount = 0,
    )

    private fun entry(directory: String, name: String, size: Long) = FlashAirEntry(
        directory = directory,
        name = name,
        size = size,
        attribute = FlashAirEntry.ATTRIBUTE_ARCHIVE,
        modifiedAt = LocalDateTime.of(2026, 8, 14, 12, 0, 0),
    )

    private companion object {
        const val CARD = "CID"
    }
}
