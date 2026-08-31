# Changelog

## 1.1.1

### Added
- **Every on-screen control now answers with a tactile blip.** Until now only
  the emulated machine's own controls -- the Apple II keyboard, the joystick
  and the paddle buttons -- pulsed under a fingertip, while the app's own
  controls said nothing back. The toolbar buttons, everything in Settings (both
  haptics switches, the model and slot pickers, Apply and Cancel), the
  Analog/Digital joystick toggle and the first-run ROM-import gate now give the
  same short confirmation.
- **A third haptics switch, "Interface haptics", in Settings.** It governs the
  app's own controls only, so the toolbar and the dialogs can be silenced
  without giving up the pulse under the keyboard and the joystick, or the other
  way round. It is on by default, and its pulse is deliberately lighter than a
  keypress: tapping Settings is incidental, pressing a key is the point.

## 1.1.0

### Added
- Updated the bundled FujiNet runtime, which adds calendar support: the new
  `CALENDAR`, `GCAL` and `ICAL` network protocols let programs read Google
  Calendar and iCalendar feeds over `N:`. Also brings in HTML parsing and
  channel mode for `N:`, a device password with web admin management, and a
  large round of upstream fixes.

### Fixed
- `tools/fujinet/build-fujinet.sh`: the `target_link_libraries` patch anchor
  now matches upstream's link line, which gained `gumbo_fn` (the vendored
  pure-C HTML5 parser behind FNSGML). The library is built in-tree with no
  external dependencies, so it needed nothing beyond joining the Android
  link line.

## 1.0.0

Google Play readiness release.

### Changed
- **Apple II system ROMs are no longer embedded in release builds.** The
  staging script strips every copyrighted entry from AppleWin's `apple2roms`
  resource target (keeping AppleWin's own GPL firmware), and a patched
  `GetResourceData` loads user-imported ROMs from app-private storage
  instead. On first run a ROM gate imports the Enhanced //e system ROM, its
  video ROM, and the Disk ][ boot ROM (classified by size, CRC-checked with
  a warning). Slots 1/2 are forced Empty (their default Printer/SSC firmware
  is no longer embedded) and slot 4's Mouse option is gone for the same
  reason; other Apple II models are disabled until their ROM sets become
  importable. Dev builds may keep the embedded set via `-Papple2Roms=true`,
  which release builds refuse; a `verifyNoEmbeddedRoms` byte-probe
  additionally guards `assembleRelease`/`bundleRelease`.
- The staged FujiNet media are sanitized before packaging: the `A3A2EMU`
  Apple II ROM image (Apple /// emulation boot support, unused inside this
  app) is removed from `autorun.po`/`mount-and-boot.po`, and `blank.do`'s
  DOS 3.3 boot tracks are zeroed (it becomes a plain data-disk template).
- Cleartext HTTP scoped to the loopback FujiNet web UI via a network
  security config; release builds carry full native debug symbols.

### Added
- Release signing via `keystore.properties`, `tools/release-play.sh`
  (signed AAB), privacy policy under `docs/`, and a Play submission
  checklist.

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
  (letters/symbols, Ctrl/Shift, Open/Closed-Apple, arrows, Return/Esc/Del) whose
  keys shrink to a compact height on TV (and other short/landscape screens) so it
  no longer fills most of the display, an
  on-screen *analog* joystick + two paddle buttons (Open/Closed Apple) that drive
  the Apple II paddles (PDL0/PDL1) proportionally via the libretro Analog
  controller, a control bar of Material icon buttons — mutually-exclusive
  keyboard/joystick toggles plus settings, the FujiNet web UI (the FujiNet "dot"
  logo, its white tinted to the accent via Modulate so the black centre stays)
  and power (active toggles tint to the accent), a RESET key on the keyboard for
  the
  authentic Ctrl-RESET (resets only while Ctrl is held), the FujiNet WebUI
  (WebView → the
  in-process web admin on loopback 8000, `-u 0.0.0.0:8000`), and a foreground
  service so the emulator + FujiNet keep running when backgrounded. Open/Closed
  Apple are shown as □ / ■.
- Settings dialog (⚙): choose the Apple II model (Enhanced //e, ][+, //e,
  Pravets, Base64A, TK3000 …) and the card in each expansion slot (3/4/5/7),
  driven through the libretro core options; applying restarts the session. The
  FujiNet SmartPort-over-SLIP card (defaulted to slot 7) is now labelled just
  "FujiNet". Fixed a crash (SIGABRT) when changing the model: the SmartPort
  Listener is a process-global singleton reused across the core re-init, and its
  start() move-assigned over a still-joinable std::thread; it now tears the prior
  listener down first.
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
