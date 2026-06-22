package online.fujinet.go.apple2

import android.content.Context

/**
 * Emulated-machine configuration that maps onto AppleWin libretro core options
 * ("applewin_machine", "applewin_slotN"). The string values must match the
 * option labels registered in AppleWin's retroregistry.cpp exactly, since the
 * core matches the option value against those labels.
 */
data class Apple2Config(
    val machine: String = MACHINES[0],
    val slot3: String = "Empty",
    val slot4: String = "Mockingboard",
    val slot5: String = "Empty",
    val slot7: String = "FujiNet",
)

/** Apple II models (matches the "machine" core-option labels). */
val MACHINES = listOf(
    "Enhanced Apple //e",
    "Apple ][ (Original)",
    "Apple ][+",
    "Apple ][ J-Plus",
    "Apple //e",
    "Pravets 82",
    "Pravets 8M",
    "Pravets 8A",
    "Base64A",
    "TK3000 //e",
)

/** Cards available per slot (matches the "slotN" core-option labels). */
val SLOT3_CARDS = listOf("Empty", "Video HD")
val SLOT4_CARDS = listOf("Empty", "Mockingboard", "Mouse", "Phasor")
val SLOT5_CARDS = listOf("Empty", "CP/M", "Mockingboard", "Phasor", "SAM/DAC", "FujiNet")
val SLOT7_CARDS = listOf("Empty", "Hard Disk", "FujiNet")

/** Synchronous, SharedPreferences-backed settings store. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fujiapple2", Context.MODE_PRIVATE)

    var config: Apple2Config
        get() = Apple2Config(
            machine = prefs.getString(KEY_MACHINE, null)?.takeIf { it in MACHINES } ?: MACHINES[0],
            slot3 = prefs.getString(KEY_SLOT3, null)?.takeIf { it in SLOT3_CARDS } ?: "Empty",
            slot4 = prefs.getString(KEY_SLOT4, null)?.takeIf { it in SLOT4_CARDS } ?: "Mockingboard",
            slot5 = prefs.getString(KEY_SLOT5, null)?.takeIf { it in SLOT5_CARDS } ?: "Empty",
            slot7 = prefs.getString(KEY_SLOT7, null)?.takeIf { it in SLOT7_CARDS } ?: "FujiNet",
        )
        set(value) {
            prefs.edit()
                .putString(KEY_MACHINE, value.machine)
                .putString(KEY_SLOT3, value.slot3)
                .putString(KEY_SLOT4, value.slot4)
                .putString(KEY_SLOT5, value.slot5)
                .putString(KEY_SLOT7, value.slot7)
                .apply()
        }

    private companion object {
        const val KEY_MACHINE = "machine"
        const val KEY_SLOT3 = "slot3"
        const val KEY_SLOT4 = "slot4"
        const val KEY_SLOT5 = "slot5"
        const val KEY_SLOT7 = "slot7"
    }
}
