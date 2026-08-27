package io.github.nishidayuya.flashairdownloader.data.flashair

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * Coil model for the Exif thumbnail of a file on the card.
 *
 * Going through a Coil [Fetcher] rather than handing Coil a URL keeps the
 * network-bound client and the `thumbnail.cgi` quirks in one place: Coil never
 * needs an HTTP client of its own, so it cannot accidentally fetch over mobile
 * data. Only JPEG files have a thumbnail (docs/design.md 2.6).
 */
data class FlashAirThumbnail(val path: String)

class FlashAirThumbnailFetcher(
    private val api: FlashAirApi,
    private val thumbnail: FlashAirThumbnail,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = api.thumbnail(thumbnail.path)
            ?: throw FileNotFoundException("No thumbnail for ${thumbnail.path}")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory @Inject constructor(
        private val api: FlashAirApi,
    ) : Fetcher.Factory<FlashAirThumbnail> {
        override fun create(
            data: FlashAirThumbnail,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = FlashAirThumbnailFetcher(api, data)
    }
}

/** Memory cache key for a thumbnail; without it every recomposition refetches. */
class FlashAirThumbnailKeyer @Inject constructor() : Keyer<FlashAirThumbnail> {
    override fun key(data: FlashAirThumbnail, options: Options): String = "flashair-thumbnail:${data.path}"
}
