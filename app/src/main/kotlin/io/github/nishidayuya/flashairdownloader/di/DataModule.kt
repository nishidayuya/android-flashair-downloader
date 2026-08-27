package io.github.nishidayuya.flashairdownloader.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nishidayuya.flashairdownloader.data.flashair.FlashAirEndpointProvider
import io.github.nishidayuya.flashairdownloader.data.storage.SafFileStore
import io.github.nishidayuya.flashairdownloader.domain.storage.DownloadStore
import io.github.nishidayuya.flashairdownloader.net.NetworkBoundEndpointProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindFlashAirEndpointProvider(
        provider: NetworkBoundEndpointProvider,
    ): FlashAirEndpointProvider

    @Binds
    @Singleton
    abstract fun bindDownloadStore(store: SafFileStore): DownloadStore
}
