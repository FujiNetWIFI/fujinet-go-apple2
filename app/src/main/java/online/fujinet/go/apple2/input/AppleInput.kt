package online.fujinet.go.apple2.input

/**
 * libretro key/modifier/joypad constants (subset) used to drive AppleWin's
 * libretro keyboard + joypad callbacks from the Compose UI. Values mirror
 * libretro.h (RETROK_*, RETROKMOD_*, RETRO_DEVICE_ID_JOYPAD_*).
 */
object Retro {
    const val K_BACKSPACE = 8
    const val K_TAB = 9
    const val K_RETURN = 13
    const val K_ESCAPE = 27
    const val K_SPACE = 32
    const val K_0 = 48
    const val K_9 = 57
    const val K_a = 97
    const val K_z = 122
    const val K_DELETE = 127
    const val K_UP = 273
    const val K_DOWN = 274
    const val K_RIGHT = 275
    const val K_LEFT = 276
    const val K_RALT = 307 // Closed (Solid) Apple
    const val K_LALT = 308 // Open Apple

    const val MOD_NONE = 0
    const val MOD_SHIFT = 0x01
    const val MOD_CTRL = 0x02

    // Joypad button ids (the Apple II paddle/joystick mapping).
    const val JOY_B = 0      // paddle button 0 (Open Apple)
    const val JOY_A = 8      // paddle button 1 (Closed Apple)
    const val JOY_UP = 4
    const val JOY_DOWN = 5
    const val JOY_LEFT = 6
    const val JOY_RIGHT = 7
}
