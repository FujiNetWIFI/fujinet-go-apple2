#pragma once

#include <cstdint>

// Android host for AppleWin's libretro core.
//
// libapple2core.so statically links AppleWin's libretro frontend, which exposes
// the standard libretro C ABI (retro_init / retro_run / retro_set_* ...). This
// host *is* the libretro frontend: it provides the environment / video / audio /
// input callbacks the core needs and adapts them to Android (an ANativeWindow
// surface, an AudioTrack feeder, and input fed from Kotlin). The session runtime
// owns the threads and calls apple2host_core_* on the emulator thread.

extern "C" {

// One decoded video frame (tightly packed XRGB8888, pitch == width*4). Invoked
// on the emulator thread from within retro_run().
typedef void (*Apple2FrameSink)(const uint32_t* xrgb8888, int width, int height, void* user);
void apple2host_set_frame_sink(Apple2FrameSink sink, void* user);

// --- libretro core lifecycle (call on the emulator thread) ------------------
// Sets up callbacks, retro_init, loads "no content" (boot to the FujiNet /
// firmware), and reads the AV geometry. Returns false on failure.
bool apple2host_core_start(void);
void apple2host_core_run_frame(void);   // one retro_run() == one ~60Hz frame
void apple2host_core_stop(void);
// Reset the emulated machine, mirroring the //e's two reset gestures:
// . Ctrl-Reset  -> warm reset (6502 reset vector). On a bare machine this drops
//   to BASIC/monitor without re-booting.
// . Ctrl-OpenApple-Reset -> power-cycle: re-inits the CPU and re-boots the disk
//   (and FujiNet) from scratch.
void apple2host_ctrl_reset(void);    // warm: Ctrl-Reset (abort to BASIC)
void apple2host_power_cycle(void);   // cold: Ctrl-OpenApple-Reset (boot)
void apple2host_get_geometry(int* width, int* height);

// Override a libretro core-option variable before the core reads it (used to
// place the SmartPort-over-SLIP card in a slot for FujiNet). Call after
// apple2host_core_start has registered the variables is too late; the session
// sets these between registration and load -- see apple2host_core_start.
void apple2host_set_variable(const char* key, const char* value);

// --- audio: drained by the JNI audio feeder ---------------------------------
// Blocks (bounded) until maxSamples interleaved stereo signed-16 samples
// (44100 Hz) are available, then copies a full block into out, silence-padding
// the remainder on underrun. Always returns maxSamples so the consumer writes a
// full, real-time-paced AudioTrack block.
int  apple2host_fill_audio(int16_t* out, int maxSamples);
// Toggle audio production drain; set 0 on shutdown to unblock a waiting fill.
void apple2host_audio_set_active(int active);
void apple2host_clear_audio(void);

// --- input (fed from Kotlin via JNI) ----------------------------------------
// Keyboard: forwarded to the core's registered retro_keyboard_callback.
void apple2host_inject_key(int down, unsigned keycode, uint32_t character, uint16_t mods);
// Joypad button (RETRO_DEVICE_ID_JOYPAD_*) and analog axis state per port.
void apple2host_set_joystick_button(int port, int id, int pressed);
void apple2host_set_joystick_axis(int port, int axis, int16_t value);

}  // extern "C"
