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

    // Joypad button ids. AppleWin's libretro paddle reads getButton(0)=JOYPAD_A
    // -> Apple II button 0 (Open Apple / primary fire), getButton(1)=JOYPAD_B
    // -> Apple II button 1 (Closed Apple / secondary).
    const val JOY_A = 8      // Apple II button 0 (Open Apple)
    const val JOY_B = 0      // Apple II button 1 (Closed Apple)

    // Analog axis indices fed to nativeSetJoystickAxis: 0 = paddle 0 (X),
    // 1 = paddle 1 (Y). Values are -32768..32767.
    const val AXIS_X = 0
    const val AXIS_Y = 1
    const val AXIS_MAX = 32767
}
