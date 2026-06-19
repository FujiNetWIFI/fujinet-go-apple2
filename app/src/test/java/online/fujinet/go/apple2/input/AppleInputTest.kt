package online.fujinet.go.apple2.input

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the libretro key/modifier constants the on-screen keyboard sends into
 * AppleWin's libretro keyboard callback. If these drift from libretro.h the
 * Apple II keyboard silently mistranslates, so pin the values.
 */
class AppleInputTest {

    @Test
    fun retroKeyConstantsMatchLibretro() {
        assertEquals(8, Retro.K_BACKSPACE)
        assertEquals(9, Retro.K_TAB)
        assertEquals(13, Retro.K_RETURN)
        assertEquals(27, Retro.K_ESCAPE)
        assertEquals(32, Retro.K_SPACE)
        assertEquals(48, Retro.K_0)
        assertEquals(97, Retro.K_a)
        assertEquals(127, Retro.K_DELETE)
        assertEquals(273, Retro.K_UP)
        assertEquals(274, Retro.K_DOWN)
        assertEquals(275, Retro.K_RIGHT)
        assertEquals(276, Retro.K_LEFT)
        assertEquals(308, Retro.K_LALT) // Open Apple
        assertEquals(307, Retro.K_RALT) // Closed Apple
    }

    @Test
    fun retroModifierBitsMatchLibretro() {
        assertEquals(0x01, Retro.MOD_SHIFT)
        assertEquals(0x02, Retro.MOD_CTRL)
    }

    @Test
    fun letterKeycodesSpanTheAlphabet() {
        // 'a'..'z' map to RETROK_a..RETROK_z contiguously.
        assertEquals(Retro.K_a + 25, Retro.K_z)
    }
}
