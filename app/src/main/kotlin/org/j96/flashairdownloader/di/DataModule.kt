package org.j96.flashairdownloader.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.j96.flashairdownloader.data.flashair.FlashAirEndpointProvider
import org.j96.flashairdownloader.net.NetworkBoundEndpointProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindFlashAirEndpointProvider(
        provider: NetworkBoundEndpointProvider,
    ): FlashAirEndpointProvider
}
