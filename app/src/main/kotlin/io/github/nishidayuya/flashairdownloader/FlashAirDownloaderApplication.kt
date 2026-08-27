package io.github.nishidayuya.flashairdownloader

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlashAirDownloaderApplication : Application(), SingletonImageLoader.Factory {
    /** Injected so that Coil uses the loader that knows how to read the card. */
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
