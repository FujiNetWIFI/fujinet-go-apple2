# FujiNet Go Apple2

Android Apple II emulation with integrated FujiNet, in the spirit of
[FujiNet Go 800](https://github.com/mozzwald/fujinet-go-800) (Atari 8-bit) and
FujiNet Go Adam (Coleco ADAM).

This repository fuses two desktop programs into one cohesive mobile app:

- **AppleWin** — the Apple ][ emulator (FujiNetWIFI `linux` fork). Its **libretro
  core** is compiled headless into a native library and driven frame-by-frame
  into an Android `Surface`.
- **fujinet-pc (APPLE target)** — the FujiNet firmware/PC port built as
  `libfujinet.so` and run in-process as a background runtime.

The two halves talk over **SmartPort-over-SLIP on loopback TCP 1985**: AppleWin's
`CT_SmartPortOverSlip` card runs the SLIP listener; the FujiNet runtime's IWM
SmartPort bus (`iwm_slip` / `connector_net`) connects in as the client and
registers its SmartPort drives. This is the Apple II analogue of FujiNet Go
800's NetSIO and FujiNet Go Adam's AdamNet "Bus over IP". To the user it is
transparent — boot the Apple //e and the FujiNet CONFIG is just there.

## Architecture

| Concern | Component |
|---|---|
| Emulator core | AppleWin libretro core, driven one frame per `retro_run()` on a worker thread |
| App native lib | `libapple2core.so` (libretro core + Android host + session + JNI) |
| Android host | `app/src/main/cpp/apple2_host.cpp` (libretro video/audio/input callbacks → Surface / AudioTrack / input) |
| FujiNet runtime | `libfujinet.so` (fujinet-pc APPLE target), `dlopen`'d in-process |
| Transport | SmartPort-over-SLIP, TCP 1985 (emulator listens, FujiNet connects) |
| UI | Jetpack Compose (emulator surface, on-screen Apple II keyboard, FujiNet WebUI) |

## Sources

The native components are built from local checkouts (not pinned GitHub
tarballs), so unpushed changes are used as-is:

- AppleWin: `~/Workspace/AppleWin` (branch `linux`)
- FujiNet: `~/Workspace/fujinet-pc-apple2`

Override with `APPLEWIN_SRC=` / `FUJINET_SRC=` when running the build scripts.

## Build requirements

- JDK 21 (the Gradle daemon is pinned to JDK 21; JDK 26 is too new for the daemon)
- Android SDK (compile SDK 36) + an installed NDK
- `bash`, `git`, `python3`, `cmake`, `rsync`, `xxd`
- Boost development headers on the host (header-only; architecture-independent —
  used for AppleWin's registry). Override the location with
  `-Papple2BoostInclude=/path/to/include`.
- The FujiNet build also clones and cross-compiles Mbed TLS.

`local.properties` records `sdk.dir` and `ndk.dir`.

## Build

```bash
# Full (all four ABIs):
./gradlew assembleDebug

# Fast single-ABI dev build:
./gradlew assembleDebug -Papple2Abi=arm64-v8a

# Unit tests:
./gradlew testDebugUnitTest
```

The application id / package is `online.fujinet.go.apple2`.

The Gradle build invokes the staging/cross-compile scripts:

- `bash tools/applewin/build-applewin-core.sh` — stages the AppleWin libretro
  core sources into the generated tree (Apple II ROMs are embedded by AppleWin's
  own resource target, so no ROM assets are staged).
- `bash tools/fujinet/build-fujinet.sh --all-abis` — builds `libfujinet.so` and
  the runtime assets (forced to `[BOIP] enabled=1 host=127.0.0.1 port=1985`).

## Generated (uncommitted) directories

- `app/src/main/cpp-generated/` — staged AppleWin sources
- `app/src/main/assets-generated/` — FujiNet runtime assets
- `app/src/main/jniLibs-generated/` — `libfujinet.so` per ABI
- `tools/applewin/work/`, `tools/fujinet/work/`

## Licensing

This is a mixed-license project — see [COMPLIANCE.md](./COMPLIANCE.md) and
[THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). AppleWin is GPLv2-or-later
and FujiNet is GPLv3 (so the combined work is effectively GPLv3); note that the
**Apple II system ROMs are Apple copyright**, which constrains distribution of
any combined binary.
