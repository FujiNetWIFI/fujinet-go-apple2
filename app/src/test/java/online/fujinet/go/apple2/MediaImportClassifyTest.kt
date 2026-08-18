package online.fujinet.go.apple2

import online.fujinet.go.apple2.settings.RomStore
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaImportClassifyTest {

    @Test
    fun referenceDumpsClassifyCleanly() {
        for (rom in RomStore.REQUIRED) {
            assertEquals(
                MediaImport.RomImportResult.Success(rom.fileName, crcMatches = true),
                MediaImport.classify(rom.expectedSize.toLong(), rom.expectedCrc),
            )
        }
    }

    @Test
    fun rightSizeWrongCrcIsAcceptedWithWarning() {
        // Other //e ROM revisions exist: accept, but flag them.
        assertEquals(
            MediaImport.RomImportResult.Success("Apple2e_Enhanced.rom", crcMatches = false),
            MediaImport.classify(16384L, 0xDEADBEEFL),
        )
    }

    @Test
    fun sizesArePairwiseDistinct() {
        // The one-picker import flow depends on this invariant.
        val sizes = RomStore.REQUIRED.map { it.expectedSize }
        assertEquals(sizes.size, sizes.toSet().size)
    }

    @Test
    fun unknownSizeIsRejected() {
        assertEquals(
            MediaImport.RomImportResult.WrongSize(8192L),
            MediaImport.classify(8192L, 0x1D70B193L),
        )
    }
}
