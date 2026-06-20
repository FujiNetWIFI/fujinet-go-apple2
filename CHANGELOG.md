# Changelog

## 0.1.0 (in progress)

First working release fusing the AppleWin Apple ][ emulator and the FujiNet
firmware (APPLE PC target) into one Android app, in the spirit of FujiNet Go 800
and FujiNet Go Adam. Verified end-to-end on an x86_64 emulator: the Apple //e
boots and the FujiNet CONFIG menu loads and runs, served by the in-process
FujiNet over SmartPort-over-SLIP.

### Build pipeline
- `tools/applewin/build-applewin-core.sh` stages the AppleWin libretro core from
  the local checkout and applies idempotent CMake/source transforms for Android:
  drive the tree as a CMake subdirectory, use the NDK's zlib and the host's
  header-only Boost, skip libslirp/pcap (FujiNet uses SmartPort, not the
  emulated Ethernet card), a non-throwing resource-folder lookup, and a Slot 5/7
  SmartPort-over-SLIP card option.
- `tools/fujinet/build-fujinet.sh` cross-compiles the FujiNet APPLE runtime to
  `libfujinet.so` from the local `fujinet-pc-apple2` checkout, with the shared
  Android source transforms (SHARED target, in-process entry wrapper,
  `reboot()`/`exit()` guard, mbedTLS-for-Android, libssh→mbedTLS) and forces
  `[BOIP] host=127.0.0.1 port=1985` (the APPLE SLIP endpoint).

### Native (`libapple2core.so`)
- The whole AppleWin libretro core (appleii + common2 + the Win32-on-POSIX shim
  + yaml + minizip + embedded Apple II ROMs) links into one shared library.
- `apple2_host.cpp` — the Android libretro frontend: XRGB8888 video → an
  `ANativeWindow` surface (with the XRGB→RGBA channel swap), libretro audio →
  an AudioTrack feeder ring, keyboard/joypad input from Kotlin, and the
  core-option store that selects the SmartPort card slot.
- `session_runtime.cpp` drives the `retro_run()` frame loop + render thread and
  the dlopen'd in-process FujiNet runtime, joined over SmartPort-over-SLIP on
  loopback TCP 1985 (AppleWin listens, FujiNet connects).
- `apple2_core.cpp` JNI bridge; `fujinet_android.cpp` FujiNet dlopen bridge.

### App (Jetpack Compose)
- Emulator surface (aspect-correct), an on-screen Apple II keyboard
  (letters/symbols, Ctrl/Shift, Open/Closed-Apple, arrows, Return/Esc/Del), an
  on-screen *analog* joystick + two paddle buttons (Open/Closed Apple) that drive
  the Apple II paddles (PDL0/PDL1) proportionally via the libretro Analog
  controller, a control bar with mutually-exclusive keyboard/joystick toggles
  plus Ctrl-Reset, FujiNet WebUI and power, the FujiNet WebUI (WebView → the
  in-process web admin on loopback 8000, `-u 0.0.0.0:8000`), and a foreground
  service so the emulator + FujiNet keep running when backgrounded. Open/Closed
  Apple are shown as □ / ■.
- Physical Bluetooth/USB game-controller support: the left analog stick drives
  the Apple II paddles proportionally (d-pad/hat as full-deflection fallback) and
  the A/B (or X/Y) face buttons map to Apple II buttons 0/1 (Open/Closed Apple).
- Adaptive launcher icon; package `online.fujinet.go.apple2`.

### FujiNet web UI
- Serve the web admin on port 8000 (`-u 0.0.0.0:8000`); the FujiNet button opens
  it in the WebView.
- Fixed the web UI never loading ("Error opening file"): FujiNet's flash "data"
  filesystem used a CWD-relative base, but the in-process AppleWin emulator
  mutates the shared working directory, so after boot the relative path broke.
  The entry now exports FUJINET_RUNTIME_ROOT and fnFsSPIFFS roots "data"
  absolutely, immune to CWD changes.

### Performance & audio (set up as a game)
- Declared a game (`appCategory="game"`, `isGame="true"`) so vendor game
  optimizers (e.g. Motorola GameTime) engage, plus window sustained-performance
  mode and `Surface.setFrameRate(60)`.
- ADPF performance-hint session on the emulator thread (dlsym'd; API 33+),
  reporting per-frame CPU work so the SoC governor keeps clocks up for the 60Hz
  loop. Gracefully no-ops where the power HAL lacks hint sessions.
- Reworked audio for glitch-free output: the native side now hands the
  AudioTrack feeder *full* blocks via a blocking, silence-padded fill (instead
  of partial drains), over a ~80ms elastic ring that drops only whole stereo
  frames on overflow; the feeder uses the low-latency fast path, the game
  usage/content types, and URGENT_AUDIO priority. Verified with zero underruns
  on a Motorola razr 2023.

### Known gaps
- Apple II system ROMs are embedded (Apple copyright — see COMPLIANCE.md).
- A machine-type / slot settings dialog is not yet implemented (defaults to the
  Enhanced Apple //e, the standard FujiNet target).
