package online.fujinet.go.apple2.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the Android-keycode -> RETROK mapping for the non-printable keys a hardware
 * keyboard sends to the Apple II. The KEYCODE_* values are compile-time constants
 * (inlined), so this runs under plain JUnit without an Android device. Printable
 * keys are resolved from the event's Unicode char at runtime and aren't covered here.
 */
class HardwareKeyboardTest {

    @Test
    fun specialKeysMapToRetroCodes() {
        assertEquals(Retro.K_RETURN, specialRetroKeycode(KeyEvent.KEYCODE_ENTER))
        assertEquals(Retro.K_RETURN, specialRetroKeycode(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(Retro.K_ESCAPE, specialRetroKeycode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(Retro.K_TAB, specialRetroKeycode(KeyEvent.KEYCODE_TAB))
        assertEquals(Retro.K_SPACE, specialRetroKeycode(KeyEvent.KEYCODE_SPACE))
        assertEquals(Retro.K_BACKSPACE, specialRetroKeycode(KeyEvent.KEYCODE_DEL))
        assertEquals(Retro.K_DELETE, specialRetroKeycode(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(Retro.K_LALT, specialRetroKeycode(KeyEvent.KEYCODE_ALT_LEFT))  // Open Apple
        assertEquals(Retro.K_RALT, specialRetroKeycode(KeyEvent.KEYCODE_ALT_RIGHT)) // Closed Apple
    }

    @Test
    fun printableAndUnknownKeysAreNotSpecialCased() {
        // Letters/digits go through the printable (Unicode) path, not the table.
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_A))
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_1))
        // Bare modifiers and volume keys are never forwarded.
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_CTRL_LEFT))
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun dpadArrowsAreReservedForNavigationNotTyping() {
        // Arrows must drive on-screen-keyboard focus (TV remote), so they are not in
        // the typing table; isDpadNavigation() filters them ahead of this lookup.
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertNull(specialRetroKeycode(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
