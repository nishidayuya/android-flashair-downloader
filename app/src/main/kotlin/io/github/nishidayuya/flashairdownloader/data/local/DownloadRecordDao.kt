package io.github.nishidayuya.flashairdownloader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM DownloadRecordEntity WHERE cardId = :cardId")
    suspend fun forCard(cardId: String): List<DownloadRecordEntity>

    @Query("SELECT * FROM DownloadRecordEntity WHERE cardId = :cardId ORDER BY downloadedAtEpoch DESC, path ASC")
    fun observeForCard(cardId: String): Flow<List<DownloadRecordEntity>>

    @Query("SELECT MAX(downloadedAtEpoch) FROM DownloadRecordEntity WHERE cardId = :cardId")
    fun observeLastDownloadEpoch(cardId: String): Flow<Long?>

    @Upsert
    suspend fun upsert(record: DownloadRecordEntity)

    @Query("DELETE FROM DownloadRecordEntity WHERE cardId = :cardId")
    suspend fun clearCard(cardId: String)
}
