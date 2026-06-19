# Licensing & Compliance

FujiNet Go Apple2 is a **mixed-license** project. The shipped app is built from
original glue code plus two third-party emulation/runtime components, plus the
Apple II system ROMs. Read this before distributing any build.

## Components and their licenses

### AppleWin (the emulator core) — GPLv2-or-later
- AppleWin © Tom Charlesworth, Michael Pohoreski, and contributors; libretro
  frontend and Linux port © the AppleWin / libretro contributors.
- License: **GNU GPL v2 or later.**
- Built from the FujiNetWIFI `AppleWin` fork (`linux` branch), whose libretro
  core (`source/frontends/libretro`) is compiled into `libapple2core.so` along
  with the SmartPort-over-SLIP card (`source/SmartPortOverSlip.cpp`,
  `source/devrelay/`) the FujiNet link depends on.
- The Android build applies a few source transforms (build the libretro frontend
  as a STATIC lib, NDK zlib / header-only Boost, skip libslirp/pcap, a
  non-throwing resource-folder lookup, an extra SmartPort slot option). These
  modifications are GPL and reproducible from
  `tools/applewin/build-applewin-core.sh`.

Unlike fujinet-go-adam's ADAMEm core, AppleWin is GPL — there is **no
non-commercial restriction**.

### FujiNet firmware / fujinet-pc (APPLE target) — GPLv3
- `libfujinet.so` is built from the FujiNet firmware (`fujinet-pc-apple2`,
  `FujiNetWIFI/fujinet-firmware`), which is GPLv3.
- The Android build applies source transforms (SHARED library target, an
  in-process entry wrapper, a `reboot()`/`exit()` guard, mbedTLS-for-Android
  wiring). These modifications are GPLv3 and reproducible from
  `tools/fujinet/build-fujinet.sh`.

### Bundled libraries (pulled in by the FujiNet build)
- **Mbed TLS** — Apache-2.0 (or GPL-2.0); cross-compiled from source.
- **libssh** — LGPL-2.1.
- **libsmb2** — LGPL-2.1.
- **libnfs** — LGPL-2.1.
- **expat** — MIT.
- **cJSON** — MIT.

### Bundled libraries (pulled in by the AppleWin build)
- **zlib** — zlib license (Android NDK sysroot).
- **libyaml** — MIT (vendored in AppleWin).
- **minizip** — zlib license (vendored in AppleWin).
- **Boost** (headers only: property_tree / algorithm / multi_array) — Boost
  Software License 1.0.

### Apple II system ROMs
The Apple II system, video, Disk ][, and SmartPort firmware ROMs (e.g.
`Apple2e_Enhanced.rom`, `DISK2.rom`, `spoverslip.bin`) are compiled into
`libapple2core.so` via AppleWin's embedded `apple2roms` resource target. The
Apple II monitor/Applesoft ROMs are **Apple copyrighted firmware** and are
**not** freely licensed. Bundling/redistributing them may infringe Apple's
copyright; for public distribution they should likely be supplied by the end
user rather than embedded. (The `spoverslip.bin` SmartPort-over-SLIP firmware is
part of the FujiNetWIFI AppleWin fork.)

## Net effect

A combined, distributed binary is bound by:
- AppleWin's **GPLv2-or-later** and FujiNet's **GPLv3** copyleft — the combined
  work is effectively **GPLv3** (offer corresponding source), and
- the Apple II ROM copyright question above.

The original FujiNet Go Apple2 glue code (build scripts, `apple2_host.cpp`,
`session_runtime.cpp`, `apple2_core.cpp`, `fujinet_android.cpp`, the Kotlin app)
is offered under the terms in [LICENSE](./LICENSE), within the GPL obligations of
the combined work.

See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for attribution details.
