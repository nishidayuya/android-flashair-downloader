package org.j96.flashairdownloader.domain.usecase

import kotlinx.coroutines.test.runTest
import org.j96.flashairdownloader.data.flashair.FakeFlashAirCard
import org.j96.flashairdownloader.data.flashair.FlashAirApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ListDirectoryUseCaseTest {
    private val card = FakeFlashAirCard()
    private val listDirectory = ListDirectoryUseCase(FlashAirApi(card))

    @Test
    fun `puts directories first and sorts by name, ignoring case`() = runTest {
        card.directory(
            "/DCIM",
            "/DCIM,b.jpg,1,32,17071,28040",
            "/DCIM,A.JPG,1,32,17071,28040",
            "/DCIM,zzz,0,16,17071,28040",
            "/DCIM,Album,0,16,17071,28040",
        )

        val entries = listDirectory("/DCIM")

        assertEquals(listOf("Album", "zzz", "A.JPG", "b.jpg"), entries.map { it.name })
    }

    @Test
    fun `hides hidden, system and volume label entries`() = runTest {
        card.directory(
            "/DCIM",
            "/DCIM,HIDDEN.JPG,1,34,17071,28040",
            "/DCIM,SYSTEM.JPG,1,36,17071,28040",
            "/DCIM,LABEL,0,8,17071,28040",
            "/DCIM,IMG_0001.JPG,1,32,17071,28040",
        )

        assertEquals(listOf("IMG_0001.JPG"), listDirectory("/DCIM").map { it.name })
    }
}
