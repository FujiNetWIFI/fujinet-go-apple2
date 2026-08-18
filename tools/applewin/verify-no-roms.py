#!/usr/bin/env python3
"""Assert that a release build really carries no Apple II system ROMs.

Ported from fujinet-go-intv's tools/jzintv/verify-no-roms.py: take a
distinctive slice out of each copyrighted ROM/disk image and grep every file
in the release output for it. Unlike the sibling apps, apple2's ROM risk is
*inside* libapple2core.so (AppleWin's apple2roms embed), so scanning the
merged native libs is the check that matters. AppleWin's own GPL firmware
(Hddrvr, HDC-SmartPort, spoverslip) is deliberately NOT probed -- it is
allowed in the release.

usage: verify-no-roms.py [--require] <dir>...

With --require, the inability to run the check (no scan dirs, no local ROM
images to probe with) is itself a failure -- release builds must not pass on
a machine where the check silently could not run. Without it, those cases
skip with a warning. APPLEWIN_SRC overrides where the reference AppleWin
checkout (and so the reference ROM images) is found, same as the staging
script.
"""

import os
import sys
from pathlib import Path

PROBE = 64

# Paths relative to the AppleWin checkout. Only copyrighted material.
FORBIDDEN = [
    "resource/Apple2.rom",
    "resource/Apple2_Plus.rom",
    "resource/Apple2_JPlus.rom",
    "resource/Apple2e.rom",
    "resource/Apple2e_Enhanced.rom",
    "resource/Apple2_Video.rom",
    "resource/Apple2e_Enhanced_Video.rom",
    "resource/DISK2.rom",
    "resource/DISK2-13sector.rom",
    "resource/SSC.rom",
    "resource/Parallel.rom",
    "resource/Mockingboard-D.rom",
    "resource/MouseInterface.rom",
    "resource/ThunderClockPlus.rom",
    "resource/Freezes_Non-autostart_F8_Rom.rom",
    "resource/PRAVETS82.ROM",
    "resource/PRAVETS8M.ROM",
    "resource/PRAVETS8C.ROM",
    "resource/TK3000e.rom",
    "resource/Base64A.rom",
    "bin/DOS 3.3 System Master - 680-0210-A.dsk",
    "bin/ProDOS_2_4_3.po",
]

ROM_SEARCH_DIRS = [
    *([Path(os.environ["APPLEWIN_SRC"])] if os.environ.get("APPLEWIN_SRC") else []),
    Path.home() / "Workspace" / "AppleWin",
]


def probe_bytes(data):
    """A slice distinctive enough that finding it means something."""
    max_run = PROBE // 2
    min_distinct = 12

    for start in range(0, max(1, len(data) - PROBE), PROBE):
        chunk = data[start:start + PROBE]
        if len(chunk) != PROBE:
            continue
        if max(chunk.count(b) for b in set(chunk)) > max_run:
            continue
        if len(set(chunk)) < min_distinct:
            continue
        return chunk
    return None


def find_rom_dir():
    for d in ROM_SEARCH_DIRS:
        if (d / "resource" / "Apple2e_Enhanced.rom").is_file():
            return d
    return None


def main():
    args = sys.argv[1:]
    require = "--require" in args
    args = [a for a in args if a != "--require"]
    if not args:
        sys.exit(__doc__)

    scan_dirs = [Path(p) for p in args if Path(p).is_dir()]
    if not scan_dirs:
        if require:
            print("verify-no-roms: FAIL: none of the scan directories exist "
                  f"({args}); with --require the check must actually run")
            return 1
        print("verify-no-roms: no scan directories found; skipping "
              "(release build type's -Papple2Roms guard is the primary check)")
        return 0

    romdir = find_rom_dir()
    if romdir is None:
        msg = ("verify-no-roms: no local AppleWin checkout found to probe with "
               f"(checked {[str(d) for d in ROM_SEARCH_DIRS]}; "
               "set APPLEWIN_SRC to override)")
        if require:
            print(f"{msg} -- FAIL under --require")
            return 1
        print(f"{msg}; skipping -- "
              "this only means we couldn't test, not that the build is clean")
        return 0

    files = []
    for scan_dir in scan_dirs:
        files.extend(f for f in scan_dir.rglob("*") if f.is_file())

    checked = 0
    leaked = []

    for rel in FORBIDDEN:
        src = romdir / rel
        if not src.is_file():
            continue
        chunk = probe_bytes(src.read_bytes())
        if chunk is None:
            continue
        checked += 1
        for f in files:
            try:
                if chunk in f.read_bytes():
                    leaked.append((rel, str(f)))
            except OSError:
                continue

    if checked == 0:
        if require:
            print("verify-no-roms: FAIL: no ROM images could be probed; "
                  "with --require the check must actually run")
            return 1
        print("verify-no-roms: no ROM images could be probed; skipping")
        return 0

    for rel, path in leaked:
        print(f"FAIL: {rel} bytes found embedded in {path}")

    if leaked:
        return 1

    print(f"verify-no-roms: {checked} ROM images checked across "
          f"{len(files)} files; none embedded, as intended")
    return 0


if __name__ == "__main__":
    sys.exit(main())
