package online.fujinet.go.apple2.core

import android.view.Surface

/**
 * JNI bridge to libapple2core.so (AppleWin's libretro core + the Android host +
 * the session runtime, and -- from Phase 4 -- the in-process FujiNet runtime).
 *
 * The native side drives the libretro core one frame per `retro_run()` on a
 * worker thread; the FujiNet runtime runs in-process and the two meet over
 * SmartPort-over-SLIP on loopback TCP 1985 (the emulator's SmartPort card
 * listens, FujiNet connects in).
 */
object EmulatorNative {
    init {
        // libfujinet.so is dlopen'd by the native layer on demand (Phase 4); we
        // only load our own core, which statically links the libretro core.
        System.loadLibrary("apple2core")
    }

    external fun nativeStartSession(
        runtimeRoot: String,
        configPath: String,
        sdPath: String,
        dataPath: String,
    )

    external fun nativeStopSession()
    external fun nativeIsRunning(): Boolean
    external fun nativeAttachSurface(surface: Surface)
    external fun nativeDetachSurface()
    external fun nativeRequestReset()

    /**
     * Sets a libretro core-option override (e.g. "applewin_machine",
     * "applewin_slot7"). Must be set before the session starts; the core reads
     * options at load, so changing one requires restarting the session.
     */
    external fun nativeSetCoreOption(key: String, value: String)

    /**
     * Injects a key event into the emulated Apple II. [keycode] is a libretro
     * RETROK_* value, [character] the ASCII code (or 0), [mods] a RETROKMOD_*
     * bitmask.
     */
    external fun nativeInjectKey(down: Boolean, keycode: Int, character: Int, mods: Int)

    /** Sets a joypad button (RETRO_DEVICE_ID_JOYPAD_*) state for [port]. */
    external fun nativeSetJoystickButton(port: Int, id: Int, pressed: Boolean)

    /** Sets an analog axis (0=LX 1=LY 2=RX 3=RY) for [port], value -32768..32767. */
    external fun nativeSetJoystickAxis(port: Int, axis: Int, value: Int)

    /**
     * Blocks until [out] can be filled with a full block of interleaved stereo
     * signed-16 samples (44100 Hz), silence-padding on underrun. Returns the
     * count written (always [out].size).
     */
    external fun nativeFillAudio(out: ShortArray): Int

    /** Toggle audio drain; pass false on shutdown to unblock a waiting fill. */
    external fun nativeAudioSetActive(active: Boolean)
}
