package online.fujinet.go.apple2.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sign

/**
 * Maps a Bluetooth/USB game controller to the Apple II analog joystick/paddles.
 *
 * The left analog stick (AXIS_X/Y) drives the paddles *proportionally*; the d-pad
 * (reported either as AXIS_HAT_X/Y motion or KEYCODE_DPAD_* keys) gives full
 * deflection as a fallback. The two primary face buttons map to the Apple II
 * paddle buttons: A/X -> button 0 (Open Apple, primary fire), B/Y -> button 1
 * (Closed Apple). State is pushed via [onPaddle] / [onButton]; each handler
 * returns true when it consumed the event.
 */
class GameControllerMapper(
    private val deadzone: Float = DEFAULT_DEADZONE,
    private val onPaddle: (x: Float, y: Float) -> Unit,
    private val onButton: (index: Int, pressed: Boolean) -> Unit,
) {
    private var stickX = 0f
    private var stickY = 0f
    private var hatX = 0f      // d-pad as AXIS_HAT (motion), -1/0/1
    private var hatY = 0f
    private var dpadUp = false // d-pad as key events
    private var dpadDown = false
    private var dpadLeft = false
    private var dpadRight = false

    fun onMotion(event: MotionEvent): Boolean {
        if (!event.isFromController() || event.action != MotionEvent.ACTION_MOVE) return false
        stickX = scale(event.getAxisValue(MotionEvent.AXIS_X))
        stickY = scale(event.getAxisValue(MotionEvent.AXIS_Y))
        hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        pushPaddle()
        return true
    }

    fun onKey(event: KeyEvent): Boolean {
        if (!event.isFromController()) return false
        val pressed = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> dpadUp = pressed
            KeyEvent.KEYCODE_DPAD_DOWN -> dpadDown = pressed
            KeyEvent.KEYCODE_DPAD_LEFT -> dpadLeft = pressed
            KeyEvent.KEYCODE_DPAD_RIGHT -> dpadRight = pressed
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X -> {
                onButton(0, pressed); return true // Open Apple
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_Y -> {
                onButton(1, pressed); return true // Closed Apple
            }
            else -> return false
        }
        pushPaddle()
        return true
    }

    /** Recenter the paddles (e.g. when a controller disconnects). */
    fun reset() {
        stickX = 0f; stickY = 0f; hatX = 0f; hatY = 0f
        dpadUp = false; dpadDown = false; dpadLeft = false; dpadRight = false
        onPaddle(0f, 0f)
    }

    private fun pushPaddle() {
        val dpadX = (if (dpadRight) 1f else 0f) - (if (dpadLeft) 1f else 0f)
        val dpadY = (if (dpadDown) 1f else 0f) - (if (dpadUp) 1f else 0f)
        // Analog stick wins; fall back to the hat axis, then the d-pad keys.
        val x = if (stickX != 0f) stickX else if (hatX != 0f) hatX else dpadX
        val y = if (stickY != 0f) stickY else if (hatY != 0f) hatY else dpadY
        onPaddle(x, y)
    }

    /** Deadzone with rescale so motion past the deadzone ramps smoothly from 0. */
    private fun scale(value: Float): Float {
        val v = value.coerceIn(-1f, 1f)
        if (abs(v) < deadzone) return 0f
        return sign(v) * ((abs(v) - deadzone) / (1f - deadzone))
    }

    private fun MotionEvent.isFromController(): Boolean =
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD

    private fun KeyEvent.isFromController(): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private companion object {
        const val DEFAULT_DEADZONE = 0.18f
    }
}
