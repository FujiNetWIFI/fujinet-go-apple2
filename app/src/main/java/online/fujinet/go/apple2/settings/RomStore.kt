package online.fujinet.go.apple2.settings

import android.content.Context
import java.io.File
import online.fujinet.go.apple2.MACHINES

/**
 * User-imported Apple II system ROMs. Release builds embed no Apple firmware
 * (the copyrighted resources are stripped from AppleWin's apple2roms target
 * by tools/applewin/build-applewin-core.sh); the core instead loads the files
 * in <filesDir>/applewin/roms via $APPLE2_ROMS_DIR, so the file names below
 * are load-bearing (they mirror AppleWin's resource names).
 *
 * v1 wires up the default machine only: the Enhanced Apple //e needs its
 * system + video ROM, plus the Disk ][ boot ROM for the slot-6 drive. Other
 * machines stay disabled in Settings until their ROM sets are added here.
 */
object RomStore {

    data class RomFile(
        val fileName: String,
        val label: String,
        val expectedSize: Int,
        val expectedCrc: Long,
    )

    /** The Enhanced //e boot set. Sizes are pairwise distinct, so imports
     *  classify by size; CRC is checked with a warning (revisions exist). */
    val REQUIRED = listOf(
        RomFile("Apple2e_Enhanced.rom", "Enhanced //e system ROM", 16384, 0x1D70B193L),
        RomFile("Apple2e_Enhanced_Video.rom", "Enhanced //e video ROM", 4096, 0x2651014DL),
        RomFile("DISK2.rom", "Disk ][ boot ROM", 256, 0xCE7144F6L),
    )

    /** Machines the app can boot, keyed by the "machine" core-option label. */
    private val MACHINE_ROMS: Map<String, List<RomFile>> = mapOf(
        "Enhanced Apple //e" to REQUIRED,
    )

    fun romsDir(context: Context): File =
        File(context.filesDir, "applewin/roms").apply { mkdirs() }

    data class RomStatus(val rom: RomFile, val present: Boolean)

    fun status(context: Context): List<RomStatus> {
        val dir = romsDir(context)
        return REQUIRED.map { rom ->
            val f = File(dir, rom.fileName)
            RomStatus(rom, f.isFile && f.length() == rom.expectedSize.toLong())
        }
    }

    fun hasSystemRoms(context: Context, machine: String): Boolean {
        val set = MACHINE_ROMS[machine] ?: return false
        val dir = romsDir(context)
        return set.all { rom ->
            val f = File(dir, rom.fileName)
            f.isFile && f.length() == rom.expectedSize.toLong()
        }
    }

    /** Machines whose full ROM set is present, in MACHINES order. */
    fun availableMachines(context: Context): List<String> =
        MACHINES.filter { hasSystemRoms(context, it) }
}
