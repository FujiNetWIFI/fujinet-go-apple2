# Third-Party Notices

FujiNet Go Apple2 incorporates the following third-party software. See
[COMPLIANCE.md](./COMPLIANCE.md) for how these licenses interact.

## AppleWin — Apple ][ emulator
- Copyright © Tom Charlesworth, Michael Pohoreski, and contributors.
- License: GNU GPL v2 or later.
- Source: https://github.com/FujiNetWIFI/AppleWin (`linux` branch).
- Used as the emulator core (`libapple2core.so`), via its libretro frontend.

## FujiNet firmware (fujinet-pc, APPLE target)
- Copyright © The FujiNet project / contributors.
- License: GNU GPL v3.
- Source: https://github.com/FujiNetWIFI/fujinet-firmware
- Used as the in-process FujiNet runtime (`libfujinet.so`).

## Mbed TLS
- Copyright © The Mbed TLS Contributors.
- License: Apache-2.0.
- Source: https://github.com/Mbed-TLS/mbedtls

## libssh
- Copyright © The libssh contributors.
- License: LGPL-2.1.

## libsmb2
- Copyright © Ronnie Sahlberg and contributors.
- License: LGPL-2.1.

## libnfs
- Copyright © Ronnie Sahlberg and contributors.
- License: LGPL-2.1.

## Expat (libexpat)
- Copyright © The Expat maintainers.
- License: MIT.

## cJSON
- Copyright © Dave Gamble and cJSON contributors.
- License: MIT.

## zlib
- Copyright © Jean-loup Gailly and Mark Adler.
- License: zlib license. (Android NDK sysroot.)

## libyaml
- Copyright © the libyaml contributors.
- License: MIT. (Vendored in AppleWin.)

## minizip
- Copyright © Gilles Vollant and contributors.
- License: zlib license. (Vendored in AppleWin.)

## Boost (headers only)
- Copyright © the Boost contributors.
- License: Boost Software License 1.0.
- Only header-only components (property_tree, algorithm/string, multi_array) are
  used, by AppleWin's common2 registry.

## Apple II system ROMs
- The Apple II system/video/Disk ][/SmartPort firmware images are Apple
  copyrighted firmware, compiled into `libapple2core.so` via AppleWin's embedded
  `apple2roms` resource target. They are not freely licensed. See the ROM note
  in [COMPLIANCE.md](./COMPLIANCE.md).
