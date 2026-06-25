package online.fujinet.go.apple2

import android.content.Context
import android.util.Log
import android.view.Surface
import online.fujinet.go.apple2.core.EmulatorNative
import online.fujinet.go.apple2.input.Retro
import kotlin.concurrent.thread

/**
 * Owns the lifetime of one Apple II session: stages the FujiNet runtime assets,
 * starts the native emulator (AppleWin libretro core) and the in-process FujiNet
 * runtime (joined over SmartPort-over-SLIP on loopback TCP 1985), streams audio,
 * and forwards input.
 *
 * A process-wide singleton so the activity and the foreground service share one
 * running session (a relaunched activity reuses it instead of starting a second
 * emulator), mirroring fujinet-go-adam.
 */
class SessionController private constructor(private val context: Context) {

    private val settings = SettingsStore(context)

    @Volatile private var started = false
    @Volatile private var paths: RuntimeInstaller.Paths? = null
    private val audio = AudioOutput()
    private val lock = Any()

    /** The FujiNet SD directory (where imported media lands), once staged. */
    val sdPath: String? get() = paths?.sdPath

    /** The current machine/slot configuration. */
    val config: Apple2Config get() = settings.config

    fun startIfNeeded() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        thread(name = "apple2-bootstrap") { launch() }
    }

    /** Persist [config] and restart the session so the new machine/slots apply. */
    fun applyConfig(config: Apple2Config) {
        settings.config = config
        thread(name = "apple2-restart") {
            stopInternal()
            synchronized(lock) { started = true }
            launch()
        }
    }

    private fun launch() {
        try {
            val p = paths ?: RuntimeInstaller(context.applicationContext).install().also { paths = it }
            // Apply machine + slot core options before the core reads them at load.
            val c = settings.config
            EmulatorNative.nativeSetCoreOption("applewin_machine", c.machine)
            EmulatorNative.nativeSetCoreOption("applewin_slot3", c.slot3)
            EmulatorNative.nativeSetCoreOption("applewin_slot4", c.slot4)
            EmulatorNative.nativeSetCoreOption("applewin_slot5", c.slot5)
            EmulatorNative.nativeSetCoreOption("applewin_slot7", c.slot7)
            EmulatorNative.nativeStartSession(p.runtimeRoot, p.configPath, p.sdPath, p.dataPath)
            audio.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start session", t)
            synchronized(lock) { started = false }
        }
    }

    fun stop() = stopInternal()

    private fun stopInternal() {
        synchronized(lock) { if (!started) return }
        audio.stop()
        EmulatorNative.nativeStopSession()
        synchronized(lock) { started = false }
    }

    fun attachSurface(surface: Surface) = EmulatorNative.nativeAttachSurface(surface)
    fun detachSurface() = EmulatorNative.nativeDetachSurface()

    /** Tap a key: press then release, carrying RETROK keycode + ASCII + mods. */
    fun tapKey(keycode: Int, character: Int, mods: Int) {
        EmulatorNative.nativeInjectKey(true, keycode, character, mods)
        EmulatorNative.nativeInjectKey(false, keycode, character, mods)
    }

    fun keyDown(keycode: Int, character: Int, mods: Int) =
        EmulatorNative.nativeInjectKey(true, keycode, character, mods)

    fun keyUp(keycode: Int, character: Int, mods: Int) =
        EmulatorNative.nativeInjectKey(false, keycode, character, mods)

    /**
     * Reset the Apple II. [cold] = power-cycle that re-boots the disk/FujiNet,
     * like Ctrl-OpenApple-Reset on a real //e; otherwise a warm Ctrl-Reset that
     * aborts to BASIC without booting.
     */
    fun reset(cold: Boolean = false) = EmulatorNative.nativeRequestReset(cold)

    fun joypadButton(id: Int, pressed: Boolean, port: Int = 0) =
        EmulatorNative.nativeSetJoystickButton(port, id, pressed)

    /**
     * Set the Apple II analog paddle position from a normalized stick
     * (-1..1 each axis; x = paddle 0, y = paddle 1). The core (RETRO_DEVICE_ANALOG
     * on port 0) maps these proportionally to PDL0/PDL1.
     */
    fun paddle(x: Float, y: Float) {
        EmulatorNative.nativeSetJoystickAxis(0, Retro.AXIS_X, axisValue(x))
        EmulatorNative.nativeSetJoystickAxis(0, Retro.AXIS_Y, axisValue(y))
    }

    /** Paddle button: index 0 = Open Apple (button 0), 1 = Closed Apple (button 1). */
    fun paddleButton(index: Int, pressed: Boolean) {
        val id = if (index == 0) Retro.JOY_A else Retro.JOY_B
        EmulatorNative.nativeSetJoystickButton(0, id, pressed)
    }

    private fun axisValue(v: Float): Int =
        (v.coerceIn(-1f, 1f) * Retro.AXIS_MAX).toInt()

    companion object {
        @Volatile private var instance: SessionController? = null

        fun get(context: Context): SessionController =
            instance ?: synchronized(this) {
                instance ?: SessionController(context.applicationContext).also { instance = it }
            }

        private const val TAG = "FujiApple2"
    }
}
