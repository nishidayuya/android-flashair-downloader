package org.j96.flashairdownloader.domain.usecase

import kotlinx.coroutines.test.runTest
import org.j96.flashairdownloader.data.flashair.FakeFlashAirCard
import org.j96.flashairdownloader.data.flashair.FlashAirApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScanRemoteFilesUseCaseTest {
    private val card = FakeFlashAirCard()
    private val scan = ScanRemoteFilesUseCase(FlashAirApi(card))

    @Test
    fun `walks the tree depth first in listing order`() = runTest {
        card.directory(
            "/DCIM",
            "/DCIM,100__TSB,0,16,17071,28040",
            "/DCIM,200__TSB,0,16,17071,28040",
            "/DCIM,ROOT.JPG,1,32,17071,28040",
        )
        card.directory("/DCIM/100__TSB", "/DCIM/100__TSB,IMG_0001.JPG,2,32,17071,28040")
        card.directory("/DCIM/200__TSB", "/DCIM/200__TSB,IMG_0002.JPG,3,32,17071,28040")

        val result = scan("/DCIM")

        assertEquals(
            listOf("/DCIM/ROOT.JPG", "/DCIM/100__TSB/IMG_0001.JPG", "/DCIM/200__TSB/IMG_0002.JPG"),
            result.files.map { it.path },
        )
        assertEquals(3, result.directoriesVisited)
        assertNull(result.stoppedEarly)
    }

    @Test
    fun `skips hidden and system entries, including their contents`() = runTest {
        card.directory(
            "/DCIM",
            "/DCIM,HIDDEN,0,18,17071,28040",
            "/DCIM,SYSTEM,0,20,17071,28040",
            "/DCIM,HIDDEN.JPG,1,34,17071,28040",
            "/DCIM,VISIBLE.JPG,1,32,17071,28040",
        )
        card.directory("/DCIM/HIDDEN", "/DCIM/HIDDEN,INSIDE.JPG,1,32,17071,28040")

        val result = scan("/DCIM")

        assertEquals(listOf("/DCIM/VISIBLE.JPG"), result.files.map { it.path })
        assertEquals(1, result.directoriesVisited)
    }

    @Test
    fun `stops at the depth limit but keeps what it found`() = runTest {
        card.directory("/DCIM", "/DCIM,A,0,16,17071,28040", "/DCIM,TOP.JPG,1,32,17071,28040")
        card.directory("/DCIM/A", "/DCIM/A,B,0,16,17071,28040", "/DCIM/A,DEEP.JPG,1,32,17071,28040")
        card.directory("/DCIM/A/B", "/DCIM/A/B,TOO_DEEP.JPG,1,32,17071,28040")

        val result = scan("/DCIM", maxDepth = 1)

        assertEquals(listOf("/DCIM/TOP.JPG", "/DCIM/A/DEEP.JPG"), result.files.map { it.path })
        assertEquals(ScanRemoteFilesUseCase.StopReason.DEPTH_LIMIT, result.stoppedEarly)
    }

    @Test
    fun `stops at the file limit`() = runTest {
        card.directory(
            "/DCIM",
            "/DCIM,IMG_0001.JPG,1,32,17071,28040",
            "/DCIM,IMG_0002.JPG,1,32,17071,28040",
            "/DCIM,IMG_0003.JPG,1,32,17071,28040",
        )

        val result = scan("/DCIM", maxFiles = 2)

        assertEquals(2, result.files.size)
        assertEquals(ScanRemoteFilesUseCase.StopReason.FILE_LIMIT, result.stoppedEarly)
    }

    @Test
    fun `does not loop when a directory contains itself`() = runTest {
        card.directory(
            "/DCIM",
            "/DCIM,SELF,0,16,17071,28040",
            "/DCIM,IMG_0001.JPG,1,32,17071,28040",
        )
        // A card that reports "/DCIM/SELF" as containing "/DCIM" again.
        card.directory("/DCIM/SELF", "/DCIM/SELF,..,0,16,17071,28040", "/DCIM/SELF,LOOP,0,16,17071,28040")
        card.directory("/DCIM/SELF/LOOP", "/DCIM/SELF/LOOP,DEEP.JPG,1,32,17071,28040")

        val result = scan("/DCIM")

        assertEquals(
            listOf("/DCIM/IMG_0001.JPG", "/DCIM/SELF/LOOP/DEEP.JPG"),
            result.files.map { it.path },
        )
    }

    @Test
    fun `reports progress per directory`() = runTest {
        card.directory("/DCIM", "/DCIM,A,0,16,17071,28040", "/DCIM,TOP.JPG,1,32,17071,28040")
        card.directory("/DCIM/A", "/DCIM/A,INSIDE.JPG,1,32,17071,28040")
        val progress = mutableListOf<Pair<String, Int>>()

        scan("/DCIM") { directory, filesSoFar -> progress += directory to filesSoFar }

        assertEquals(listOf("/DCIM" to 1, "/DCIM/A" to 2), progress)
    }
}
