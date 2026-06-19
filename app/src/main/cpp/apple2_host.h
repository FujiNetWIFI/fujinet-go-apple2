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
void apple2host_core_reset(void);
void apple2host_get_geometry(int* width, int* height);

// Override a libretro core-option variable before the core reads it (used to
// place the SmartPort-over-SLIP card in a slot for FujiNet). Call after
// apple2host_core_start has registered the variables is too late; the session
// sets these between registration and load -- see apple2host_core_start.
void apple2host_set_variable(const char* key, const char* value);

// --- audio: drained by the JNI audio feeder ---------------------------------
// Copies up to maxSamples interleaved stereo signed-16 samples (44100 Hz) into
// out; returns the number of int16 values written.
int  apple2host_read_audio(int16_t* out, int maxSamples);
void apple2host_clear_audio(void);

// --- input (fed from Kotlin via JNI) ----------------------------------------
// Keyboard: forwarded to the core's registered retro_keyboard_callback.
void apple2host_inject_key(int down, unsigned keycode, uint32_t character, uint16_t mods);
// Joypad button (RETRO_DEVICE_ID_JOYPAD_*) and analog axis state per port.
void apple2host_set_joystick_button(int port, int id, int pressed);
void apple2host_set_joystick_axis(int port, int axis, int16_t value);

}  // extern "C"
