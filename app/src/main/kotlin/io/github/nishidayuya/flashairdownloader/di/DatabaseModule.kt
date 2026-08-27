package io.github.nishidayuya.flashairdownloader.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.nishidayuya.flashairdownloader.data.local.AppDatabase
import io.github.nishidayuya.flashairdownloader.data.local.DownloadRecordDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideDownloadRecordDao(database: AppDatabase): DownloadRecordDao = database.downloadRecordDao()
}
