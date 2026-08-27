package io.github.nishidayuya.flashairdownloader.domain.usecase

import io.github.nishidayuya.flashairdownloader.data.flashair.FlashAirApi
import io.github.nishidayuya.flashairdownloader.data.flashair.model.FlashAirEntry
import javax.inject.Inject

/**
 * One directory, ready to show: hidden, system and volume label entries removed,
 * directories first, then files, both by name.
 */
class ListDirectoryUseCase @Inject constructor(
    private val api: FlashAirApi,
) {
    suspend operator fun invoke(directory: String): List<FlashAirEntry> =
        api.listEntries(directory)
            .filterNot { it.isHidden || it.isSystem || it.isVolumeLabel }
            .sortedWith(
                compareByDescending<FlashAirEntry> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
}
