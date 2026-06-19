package online.fujinet.go.apple2

import android.content.Context
import android.util.Log
import android.view.Surface
import online.fujinet.go.apple2.core.EmulatorNative
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

    @Volatile private var started = false
    @Volatile private var paths: RuntimeInstaller.Paths? = null
    private val audio = AudioOutput()
    private val lock = Any()

    /** The FujiNet SD directory (where imported media lands), once staged. */
    val sdPath: String? get() = paths?.sdPath

    fun startIfNeeded() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        thread(name = "apple2-bootstrap") { launch() }
    }

    private fun launch() {
        try {
            val p = paths ?: RuntimeInstaller(context.applicationContext).install().also { paths = it }
            EmulatorNative.nativeStartSession(p.runtimeRoot, p.configPath, p.sdPath, p.dataPath)
            audio.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start session", t)
            synchronized(lock) { started = false }
        }
    }

    fun stop() {
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

    /** Ctrl-Reset (reboot the Apple II). */
    fun reset() = EmulatorNative.nativeRequestReset()

    fun joypadButton(id: Int, pressed: Boolean, port: Int = 0) =
        EmulatorNative.nativeSetJoystickButton(port, id, pressed)

    companion object {
        @Volatile private var instance: SessionController? = null

        fun get(context: Context): SessionController =
            instance ?: synchronized(this) {
                instance ?: SessionController(context.applicationContext).also { instance = it }
            }

        private const val TAG = "FujiApple2"
    }
}
