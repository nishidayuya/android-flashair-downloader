package org.j96.flashairdownloader.di

import android.content.Context
import coil3.ImageLoader
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.j96.flashairdownloader.data.flashair.FlashAirThumbnailFetcher
import org.j96.flashairdownloader.data.flashair.FlashAirThumbnailKeyer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    /**
     * The app's only image source is the card, reached through the fetcher, so
     * this loader has no HTTP client of its own.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        thumbnailFetcher: FlashAirThumbnailFetcher.Factory,
        thumbnailKeyer: FlashAirThumbnailKeyer,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(thumbnailFetcher)
            add(thumbnailKeyer)
        }
        .crossfade(true)
        .build()
}
