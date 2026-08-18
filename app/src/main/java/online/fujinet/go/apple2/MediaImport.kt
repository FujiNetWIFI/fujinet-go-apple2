package online.fujinet.go.apple2

import android.content.Context
import android.net.Uri
import java.io.File
import online.fujinet.go.apple2.settings.RomStore

/**
 * Storage Access Framework import for Apple II system ROMs, modelled on
 * fujinet-go-intv's MediaImport. The v1 ROM set's sizes are pairwise
 * distinct (16384 / 4096 / 256), so imports classify by exact byte size;
 * the CRC is checked against the reference dump but a mismatch is accepted
 * with a warning ([Success.crcMatches] false) since other //e ROM revisions
 * exist and mostly work.
 */
object MediaImport {

    sealed class RomImportResult {
        data class Success(val fileName: String, val crcMatches: Boolean) : RomImportResult()
        data class WrongSize(val actualSize: Long) : RomImportResult()
        object ReadFailed : RomImportResult()
    }

    /** Pure size/CRC verdict, split out so it is unit-testable. */
    internal fun classify(size: Long, crc: Long): RomImportResult {
        val rom = RomStore.REQUIRED.firstOrNull { it.expectedSize.toLong() == size }
            ?: return RomImportResult.WrongSize(size)
        return RomImportResult.Success(rom.fileName, crc == rom.expectedCrc)
    }

    fun importSystemRom(context: Context, uri: Uri): RomImportResult {
        val romsDir = RomStore.romsDir(context)
        val temp = File(romsDir, ".import-tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return RomImportResult.ReadFailed
        } catch (_: Exception) {
            temp.delete()
            return RomImportResult.ReadFailed
        }

        val verdict = classify(temp.length(), crc32Of(temp))
        if (verdict !is RomImportResult.Success) {
            temp.delete()
            return verdict
        }
        val dest = File(romsDir, verdict.fileName)
        dest.delete()
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
        return verdict
    }

    private fun crc32Of(file: File): Long {
        val crc = java.util.zip.CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }
}
