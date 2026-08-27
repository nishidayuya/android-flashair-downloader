package io.github.nishidayuya.flashairdownloader.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in for the Room dao, so the diff and download logic can be tested on the JVM. */
class FakeDownloadRecordDao : DownloadRecordDao {
    private val records = MutableStateFlow<Map<Pair<String, String>, DownloadRecordEntity>>(emptyMap())

    val all: List<DownloadRecordEntity> get() = records.value.values.toList()

    override suspend fun forCard(cardId: String): List<DownloadRecordEntity> =
        records.value.values.filter { it.cardId == cardId }

    override fun observeForCard(cardId: String): Flow<List<DownloadRecordEntity>> =
        records.map { current -> current.values.filter { it.cardId == cardId } }

    override fun observeLastDownloadEpoch(cardId: String): Flow<Long?> =
        records.map { current ->
            current.values.filter { it.cardId == cardId }.maxOfOrNull { it.downloadedAtEpoch }
        }

    override suspend fun upsert(record: DownloadRecordEntity) {
        records.value = records.value + ((record.cardId to record.path) to record)
    }

    override suspend fun clearCard(cardId: String) {
        records.value = records.value.filterKeys { it.first != cardId }
    }
}
