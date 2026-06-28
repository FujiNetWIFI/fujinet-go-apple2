package online.fujinet.go.apple2.input

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Routes an attached hardware keyboard (USB / Bluetooth) to the Apple II, mirroring
 * what the on-screen [online.fujinet.go.apple2.ui.AppleKeyboard] sends into
 * AppleWin's libretro keyboard callback: a RETROK keycode + the typed Unicode
 * character + Shift/Ctrl mods. The core (game.cpp::processKeyDown) resolves the
 * Apple II character from those.
 *
 * Only events from a real *alphabetic* keyboard device are consumed; a TV remote's
 * D-pad (a non-alphabetic device) is left to Compose focus navigation so it keeps
 * driving the on-screen keyboard rather than typing. [onDown] / [onUp] push the
 * stroke; [onKey] returns true when it consumed the event.
 */
class HardwareKeyboard(
    private val onDown: (keycode: Int, character: Int, mods: Int) -> Unit,
    private val onUp: (keycode: Int, character: Int, mods: Int) -> Unit,
) {
    fun onKey(event: KeyEvent): Boolean {
        if (!event.isFromPhysicalKeyboard()) return false
        val stroke = mapKey(event) ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> onDown(stroke.keycode, stroke.character, stroke.mods)
            KeyEvent.ACTION_UP -> onUp(stroke.keycode, stroke.character, stroke.mods)
            else -> return false
        }
        return true
    }

    private fun KeyEvent.isFromPhysicalKeyboard(): Boolean {
        val d = device ?: return false
        return !d.isVirtual &&
            d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
            source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
    }
}

/** A resolved keystroke: RETROK keycode + Unicode character + libretro mod bits. */
internal data class Stroke(val keycode: Int, val character: Int, val mods: Int)

/**
 * Translate an Android key event to a libretro stroke, or null for keys we don't
 * forward (bare modifiers, volume, function keys, etc.).
 */
internal fun mapKey(event: KeyEvent): Stroke? {
    // A D-pad cluster event drives Compose focus navigation only when it comes from a
    // real D-pad device (a TV remote / gamepad, marked SOURCE_DPAD); see
    // isDpadNavigation(). Arrow keys typed on an attached keyboard carry no SOURCE_DPAD,
    // so they fall through here and reach the Apple II (e.g. to move the FujiNet CONFIG
    // selection bar).
    if (isDpadNavigation(event)) return null

    val mods = (if (event.isShiftPressed) Retro.MOD_SHIFT else 0) or
        (if (event.isCtrlPressed) Retro.MOD_CTRL else 0)

    specialRetroKeycode(event.keyCode)?.let { return Stroke(it, 0, mods) }

    // Printable key: the RETROK keycode equals the *unshifted* ASCII the key
    // produces (RETROK_a..z, RETROK_0..9 and the symbol keys all share their ASCII
    // values), so derive it from the no-modifier Unicode char.
    val base = event.getUnicodeChar(0) and KeyCharacterMap.COMBINING_ACCENT_MASK
    if (base == 0 || base > 0x7e) return null
    // Match the on-screen keyboard: Ctrl combos resolve via the keycode in the
    // core, so suppress the character; otherwise pass the typed (shifted) char.
    val character =
        if (event.isCtrlPressed) 0 else event.unicodeChar and KeyCharacterMap.COMBINING_ACCENT_MASK
    return Stroke(base, character, mods)
}

/**
 * Map the Android keycodes for non-printable / special keys to their RETROK code.
 * Pure (no [KeyEvent] instance) so it can be unit-tested. Returns null for keys
 * handled by the printable path or not forwarded at all.
 */
internal fun specialRetroKeycode(androidKeyCode: Int): Int? = when (androidKeyCode) {
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Retro.K_RETURN
    KeyEvent.KEYCODE_ESCAPE -> Retro.K_ESCAPE
    KeyEvent.KEYCODE_TAB -> Retro.K_TAB
    KeyEvent.KEYCODE_SPACE -> Retro.K_SPACE
    KeyEvent.KEYCODE_DEL -> Retro.K_BACKSPACE        // Backspace
    KeyEvent.KEYCODE_FORWARD_DEL -> Retro.K_DELETE
    KeyEvent.KEYCODE_ALT_LEFT -> Retro.K_LALT        // Open Apple
    KeyEvent.KEYCODE_ALT_RIGHT -> Retro.K_RALT       // Closed Apple
    // Arrow keys reach this table only for events isDpadNavigation() let through,
    // i.e. typed on a keyboard rather than a TV remote / gamepad D-pad. DPAD_CENTER
    // has no Apple II equivalent, so it is never forwarded.
    KeyEvent.KEYCODE_DPAD_UP -> Retro.K_UP
    KeyEvent.KEYCODE_DPAD_DOWN -> Retro.K_DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> Retro.K_LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> Retro.K_RIGHT
    else -> null
}

/**
 * True for the keys that must navigate/activate the on-screen keyboard rather than
 * type into the emulator. The D-pad cluster (arrows, DPAD_CENTER) and a remote's
 * "OK"/ENTER are reserved only when they carry a D-pad source -- i.e. they come from a
 * TV remote or gamepad. A typing keyboard's arrows and Enter carry no SOURCE_DPAD, so
 * they fall through and reach the Apple II (arrows as RETROK arrows, Enter as RETURN)
 * -- e.g. to drive the FujiNet CONFIG selection bar.
 */
private fun isDpadNavigation(event: KeyEvent): Boolean = when (event.keyCode) {
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
        event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
    else -> false
}
