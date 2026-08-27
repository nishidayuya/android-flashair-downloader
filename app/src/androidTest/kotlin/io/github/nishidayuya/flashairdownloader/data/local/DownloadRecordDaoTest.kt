package io.github.nishidayuya.flashairdownloader.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The dao against a real Room database, which is the only place its generated
 * SQL actually runs. In particular the (cardId, path) primary key and the
 * upsert behaviour on top of it cannot be checked on the JVM.
 */
class DownloadRecordDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: DownloadRecordDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.downloadRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun keepsOneRecordPerCardAndPath() = runBlocking {
        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 10))
        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 20, downloadedAtEpoch = 200))

        val records = dao.forCard(CARD)
        assertEquals(1, records.size)
        assertEquals(20L, records.single().size)
        assertEquals(200L, records.single().downloadedAtEpoch)
    }

    @Test
    fun keepsCardsApart() = runBlocking {
        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 10))
        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 99, cardId = "OTHER"))

        assertEquals(listOf(10L), dao.forCard(CARD).map { it.size })
        assertEquals(listOf(99L), dao.forCard("OTHER").map { it.size })
    }

    @Test
    fun reportsTheMostRecentDownload() = runBlocking {
        assertNull(dao.observeLastDownloadEpoch(CARD).first())

        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 1, downloadedAtEpoch = 100))
        dao.upsert(record(path = "/DCIM/IMG_0002.JPG", size = 1, downloadedAtEpoch = 300))
        dao.upsert(record(path = "/DCIM/IMG_0003.JPG", size = 1, downloadedAtEpoch = 200))

        assertEquals(300L, dao.observeLastDownloadEpoch(CARD).first())
    }

    @Test
    fun listsTheNewestFirst() = runBlocking {
        dao.upsert(record(path = "/DCIM/OLD.JPG", size = 1, downloadedAtEpoch = 100))
        dao.upsert(record(path = "/DCIM/NEW.JPG", size = 1, downloadedAtEpoch = 300))

        assertEquals(
            listOf("/DCIM/NEW.JPG", "/DCIM/OLD.JPG"),
            dao.observeForCard(CARD).first().map { it.path },
        )
    }

    @Test
    fun clearsOnlyTheCardItWasAsked() = runBlocking {
        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 1))
        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 1, cardId = "OTHER"))

        dao.clearCard(CARD)

        assertEquals(emptyList<DownloadRecordEntity>(), dao.forCard(CARD))
        assertEquals(1, dao.forCard("OTHER").size)
    }

    @Test
    fun keepsAnUnknownModificationTime() = runBlocking {
        dao.upsert(record(path = "/DCIM/IMG_0001.JPG", size = 1).copy(modifiedAtEpoch = null))

        assertNull(dao.forCard(CARD).single().modifiedAtEpoch)
    }

    private fun record(
        path: String,
        size: Long,
        cardId: String = CARD,
        downloadedAtEpoch: Long = 100,
    ) = DownloadRecordEntity(
        cardId = cardId,
        path = path,
        size = size,
        modifiedAtEpoch = 1_700_000_000,
        downloadedAtEpoch = downloadedAtEpoch,
        localUri = "content://tree/$path",
    )

    private companion object {
        const val CARD = "CID"
    }
}
