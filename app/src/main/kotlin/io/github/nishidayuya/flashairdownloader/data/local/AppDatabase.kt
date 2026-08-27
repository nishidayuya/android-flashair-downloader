package io.github.nishidayuya.flashairdownloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadRecordEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadRecordDao(): DownloadRecordDao

    companion object {
        const val NAME = "flashair-downloader.db"
    }
}
