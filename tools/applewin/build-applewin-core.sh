#!/usr/bin/env bash
#
# Stage the AppleWin emulator (FujiNetWIFI fork, `linux` branch) from a local
# checkout into the Android native source tree
# (app/src/main/cpp-generated/applewin) so the libretro core
# (source/frontends/libretro) can be cross-compiled into the app's native build.
#
# Like fujinet-go-adam's build-adamem-core.sh, the source is the user's LOCAL
# working copy (not a pinned GitHub tarball) so unpushed changes -- including the
# SmartPort-over-SLIP card the FujiNet link depends on -- are used as-is.
#
# Apple firmware policy: the Apple II system ROMs are copyrighted, so by
# default this script STRIPS them from AppleWin's `apple2roms` resource target
# (and from the staged resource/ tree) -- release builds embed only the
# GPL-safe AppleWin-authored firmware (Hddrvr/HDC-SmartPort/spoverslip). At
# runtime the core loads user-imported ROMs from $APPLE2_ROMS_DIR instead (a
# GetResourceData override patched in below). --with-roms (wired to
# -Papple2Roms=true, dev debug builds only) keeps the upstream embedded set.
#
# The only transforms are a handful of idempotent CMake edits that let AppleWin's
# build run as a subdirectory of the app's Android CMake project (it normally
# assumes it is the top-level project): fix two ${CMAKE_SOURCE_DIR} references,
# use the NDK's zlib + the host's header-only Boost instead of pkg-config, and
# guard a desktop-only debugger-symbol copy.

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
PROJECT_ROOT=$(cd -- "${SCRIPT_DIR}/../.." &>/dev/null && pwd)

# Source location (override with APPLEWIN_SRC=/path bash build-applewin-core.sh)
SOURCE_DIR="${APPLEWIN_SRC:-${HOME}/Workspace/AppleWin}"
SOURCE_BRANCH="linux"
SOURCE_COMMIT="unknown"

GENERATED_ROOT="${PROJECT_ROOT}/app/src/main/cpp-generated/applewin"
STAMP_PATH="${GENERATED_ROOT}/.source-info"

# Subset of the AppleWin tree the Android libretro build needs. The desktop
# frontends (sdl/qt/ncurses), tests, docs, web help and Windows-only bits are
# intentionally excluded.
STAGE_DIRS=(
    "source"
    "resource"
    "libyaml"
    "minizip"
    "bin"          # APPLE2E.SYM etc., copied by a POST_BUILD step in source/
)

fail() {
    echo "build-applewin-core.sh: $*" >&2
    exit 1
}

WITH_ROMS=0
for arg in "$@"; do
    case "$arg" in
        --with-roms) WITH_ROMS=1 ;;
        *) fail "unknown argument: $arg" ;;
    esac
done

[[ -d "${SOURCE_DIR}" ]] || fail "AppleWin source not found at ${SOURCE_DIR} (set APPLEWIN_SRC)"
[[ -f "${SOURCE_DIR}/source/frontends/libretro/libretro.cpp" ]] || \
    fail "libretro frontend missing under ${SOURCE_DIR} (is this the FujiNetWIFI 'linux' branch?)"

# Record the resolved commit/branch when the checkout is a git repo (best effort).
if command -v git >/dev/null 2>&1 && git -C "${SOURCE_DIR}" rev-parse --git-dir >/dev/null 2>&1; then
    SOURCE_COMMIT=$(git -C "${SOURCE_DIR}" rev-parse --short HEAD 2>/dev/null || echo "${SOURCE_COMMIT}")
    SOURCE_BRANCH=$(git -C "${SOURCE_DIR}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "${SOURCE_BRANCH}")
fi

# Fingerprint the checkout so we only re-stage when it actually changes: the
# resolved commit plus a hash of any working-tree diff (catches unpushed edits).
source_fingerprint() {
    {
        echo "${SOURCE_COMMIT}"
        # Re-stage when the ROM policy flips, not only on source changes.
        echo "with_roms=${WITH_ROMS}"
        # Re-stage when this script's staging/patch logic changes, not only when
        # the AppleWin checkout changes.
        sha256sum "${BASH_SOURCE[0]}" 2>/dev/null || true
        if command -v git >/dev/null 2>&1 && git -C "${SOURCE_DIR}" rev-parse --git-dir >/dev/null 2>&1; then
            git -C "${SOURCE_DIR}" diff --no-color 2>/dev/null || true
            git -C "${SOURCE_DIR}" status --porcelain 2>/dev/null || true
        else
            find "${SOURCE_DIR}/source" "${SOURCE_DIR}/resource" -type f -printf '%P %s %T@\n' 2>/dev/null | sort
        fi
    } | sha256sum | awk '{ print $1 }'
}

source_is_current() {
    local fp="$1"
    [[ -f "${STAMP_PATH}" ]] &&
    grep -q "^source_fingerprint=${fp}$" "${STAMP_PATH}" &&
    [[ -f "${GENERATED_ROOT}/source/frontends/libretro/libretro.cpp" ]]
}

FP=$(source_fingerprint)
if source_is_current "${FP}"; then
    exit 0
fi

echo "Staging AppleWin core from ${SOURCE_DIR} (${SOURCE_BRANCH} ${SOURCE_COMMIT})"

rm -rf "${GENERATED_ROOT}"
mkdir -p "${GENERATED_ROOT}"

for d in "${STAGE_DIRS[@]}"; do
    [[ -d "${SOURCE_DIR}/${d}" ]] || fail "expected source directory missing: ${d}"
    # Copy the subtree minus VCS metadata and any stale CMake/build artifacts.
    rsync -a --delete \
        --exclude '.git' \
        --exclude 'build/' \
        --exclude 'CMakeFiles/' \
        --exclude 'CMakeCache.txt' \
        --exclude 'cmake_install.cmake' \
        --exclude '*.o' --exclude '*.a' --exclude '*.so' \
        --exclude 'compile_commands.json' \
        "${SOURCE_DIR}/${d}/" "${GENERATED_ROOT}/${d}/"
done

# --- Idempotent CMake transforms for the Android subdirectory build ----------
python3 - "${GENERATED_ROOT}" "${WITH_ROMS}" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
with_roms = sys.argv[2] == "1"

def patch(rel, transforms):
    p = root / rel
    if not p.is_file():
        sys.exit(f"build-applewin-core.sh: expected file missing: {rel}")
    text = p.read_text()
    for old, new in transforms:
        if old not in text:
            # Idempotent: skip if the post-transform text is already present.
            if new and new in text:
                continue
            sys.exit(f"build-applewin-core.sh: patch anchor not found in {rel}:\n---\n{old}\n---")
        text = text.replace(old, new, 1)
    p.write_text(text)

# AppleWin assumes it is the top-level CMake project; when we add it as a
# subdirectory, ${CMAKE_SOURCE_DIR} points at the app's cpp/ dir. Anchor the two
# such references in source/CMakeLists.txt to the staged tree instead.
patch("source/CMakeLists.txt", [
    (
        "include(${CMAKE_SOURCE_DIR}/source/slip.cmake)",
        "include(${CMAKE_CURRENT_SOURCE_DIR}/slip.cmake)",
    ),
    # Skip libslirp / libpcap detection on Android. The host pkg-config would
    # otherwise resolve the developer's desktop libslirp (installed to build
    # AppleWin for Linux) and emit -lslirp/-lglib-2.0 into the link, which do
    # not exist for Android. With both unset, appleii falls back to its dummy
    # network backend (linux/duplicates/tfearch.cpp) and Uthernet/slirp2 compile
    # out -- FujiNet does not use the emulated Ethernet card.
    (
        "if (NOT WIN32)\n  pkg_search_module(SLIRP slirp)",
        "if (NOT WIN32 AND NOT ANDROID)\n  pkg_search_module(SLIRP slirp)",
    ),
    (
        "pkg_search_module(ZLIB REQUIRED zlib)",
        "find_package(ZLIB REQUIRED)",
    ),
    (
        "find_package(Boost REQUIRED)",
        "if(NOT DEFINED Boost_INCLUDE_DIRS)\n  find_package(Boost REQUIRED)\nendif()",
    ),
    (
        "${CMAKE_SOURCE_DIR}/bin/APPLE2E.SYM ${CMAKE_SOURCE_DIR}/bin/A2_BASIC.SYM ${CMAKE_SOURCE_DIR}/bin/A2_DOS33.SYM2",
        "${CMAKE_CURRENT_SOURCE_DIR}/../bin/APPLE2E.SYM ${CMAKE_CURRENT_SOURCE_DIR}/../bin/A2_BASIC.SYM ${CMAKE_CURRENT_SOURCE_DIR}/../bin/A2_DOS33.SYM2",
    ),
])

# common2 also calls find_package(Boost REQUIRED); skip it when the app's
# CMakeLists has already provided the header-only include path.
patch("source/frontends/common2/CMakeLists.txt", [
    (
        "find_package(Boost REQUIRED)",
        "if(NOT DEFINED Boost_INCLUDE_DIRS)\n  find_package(Boost REQUIRED)\nendif()",
    ),
])

# getResourceFolder() canonicalises <exe dir>/ROOT_PATH (a build-relative path)
# to locate the desktop resource tree. On Android /proc/self/exe is app_process
# in /system/bin and that path does not exist, so the throwing canonical()
# overload aborts retro_load_game. ROMs come from the embedded apple2roms map,
# not this folder (it only seeds g_sProgramDir for debug symbols / printer
# output), so use the non-throwing overload and fall through to the cwd default.
patch("source/frontends/common2/gnuframe.cpp", [
    (
        "            const auto root = std::filesystem::canonical(executable.parent_path() / ROOT_PATH);\n"
        "            paths.push_back(root);",
        "            std::error_code ec;\n"
        "            const auto root = std::filesystem::canonical(executable.parent_path() / ROOT_PATH, ec);\n"
        "            if (!ec)\n"
        "            {\n"
        "                paths.push_back(root);\n"
        "            }",
    ),
])

# The SmartPort-over-SLIP Listener is a process-global singleton
# (GetCommandListener) reused across emulator re-inits when the user changes the
# machine type. Its start() did `listening_thread_ = std::thread(...)`, which
# std::terminate()s when assigned over a still-joinable thread from the previous
# init -> SIGABRT on the second machine switch. Make start() tear down any prior
# listener first, and make stop() join whenever the thread is joinable.
patch("source/devrelay/service/Listener.cpp", [
    (
        "void Listener::start()\n"
        "{\n"
        "\tis_listening_ = true;\n",
        "void Listener::start()\n"
        "{\n"
        "\t// [fujinet-go-apple2] The Listener is a process-global singleton reused\n"
        "\t// across emulator re-inits (machine-type change); tear down any prior\n"
        "\t// listener thread first so the std::thread move-assign can't terminate.\n"
        "\tif (is_listening_ || listening_thread_.joinable())\n"
        "\t{\n"
        "\t\tstop();\n"
        "\t}\n"
        "\tis_listening_ = true;\n",
    ),
    (
        "\tif (is_listening_)\n"
        "\t{\n"
        "\t\t// Stop listener first, otherwise the PC might reboot too fast and be picked up\n"
        "\t\tis_listening_ = false;\n"
        "\t\tLogFileOutput(\"Listener::stop() ... joining listener until it stops\\n\");\n"
        "\t\tlistening_thread_.join();",
        "\tis_listening_ = false;\n"
        "\tif (listening_thread_.joinable())\n"
        "\t{\n"
        "\t\t// Stop listener first, otherwise the PC might reboot too fast and be picked up\n"
        "\t\tLogFileOutput(\"Listener::stop() ... joining listener until it stops\\n\");\n"
        "\t\tlistening_thread_.join();",
    ),
])

# Build the libretro frontend as a STATIC library so it links whole into the
# app's single libapple2core.so (the host calls retro_* directly, RetroArch-
# style indirection is not needed), matching adam's single-native-lib model.
patch("source/frontends/libretro/CMakeLists.txt", [
    (
        "add_library(applewin_libretro SHARED",
        "add_library(applewin_libretro STATIC",
    ),
])

# Expose the SmartPort-over-SLIP card (the FujiNet bridge) in the libretro core
# registry. Slot 7 is the bootable SmartPort/HDD slot the //e autostart scans
# before the Disk][ in slot 6, so placing FujiNet there boots its CONFIG
# directly. The card is also offered in Slot 5 for flexibility. The Android
# session selects it via the "applewin_slot7" core-option
# (apple2host_set_variable), which makes the core insert CT_SmartPortOverSlip and
# start its Listener on TCP 1985 for the in-process FujiNet runtime.
patch("source/frontends/libretro/retroregistry.cpp", [
    (
        '                    {"SAM/DAC", CT_SAM},\n'
        '                },',
        '                    {"SAM/DAC", CT_SAM},\n'
        '                    {"FujiNet", CT_SmartPortOverSlip},\n'
        '                },',
    ),
    (
        '        {\n'
        '            {\n'
        '                "video_mode",',
        '        {\n'
        '            {\n'
        '                "slot7",\n'
        '                "Card in Slot 7",\n'
        '                CATEGORY_SYSTEM,\n'
        '                {\n'
        '                    {"Empty", CT_Empty},\n'
        '                    {"Hard Disk", CT_GenericHDD},\n'
        '                    {"FujiNet", CT_SmartPortOverSlip},\n'
        '                },\n'
        '            },\n'
        '            "Configuration\\\\Slot 7",\n'
        '            REGVALUE_CARD_TYPE, // reset required\n'
        '        },\n'
        '        {\n'
        '            {\n'
        '                "video_mode",',
    ),
])

# Fix a sample-doubling bug in the libretro speaker mixer. When a generator's
# ring-buffer Read wraps it returns two segments; writeAudio mixes them with two
# mixBuffer() calls, but ptr is passed by value so the second (wrapped) segment
# is written back at buffer.data() instead of after the first. Since mixBuffer
# does *ptr += ..., the segments sum on top of each other every ring wrap
# (~16384 frames), doubling samples -- an audible ~2.7Hz "gallop" click over any
# tone. Offset the second segment by the first's length. (Upstream libretro-
# AppleWin bug; remove this patch once fixed there.)
patch("source/frontends/libretro/rdirectsound.cpp", [
    (
        "                mixBuffer(generator, lpvAudioPtr1, dwAudioBytes1, ptr);\n"
        "                mixBuffer(generator, lpvAudioPtr2, dwAudioBytes2, ptr);",
        "                mixBuffer(generator, lpvAudioPtr1, dwAudioBytes1, ptr);\n"
        "                mixBuffer(generator, lpvAudioPtr2, dwAudioBytes2,\n"
        "                          ptr + dwAudioBytes1 / sizeof(int16_t));",
    ),
])

# User-imported ROM override: GetResourceData consults $APPLE2_ROMS_DIR (set by
# the app's session runtime, pointing at the app-private directory the first-run
# ROM gate imports into) before the embedded apple2roms map. File-first so an
# imported ROM also overrides a dev-embedded copy. Applied unconditionally --
# harmless in --with-roms builds. The id->filename table mirrors
# resource/CMakeLists.txt; ids come from resource/resource.h (included by
# relative path).
patch("source/frontends/common2/gnuframe.cpp", [
    (
        "#include <filesystem>\n",
        "#include <filesystem>\n"
        "#include <cstdlib>\n"
        "#include <fstream>\n"
        "#include <iterator>\n"
        "#include <vector>\n"
        "#include \"../../../resource/resource.h\"\n",
    ),
    (
        "        const auto it = apple2roms::data.find(id);\n"
        "        if (it == apple2roms::data.end())\n",
        "        // [fujinet-go-apple2] User-imported ROM override; see\n"
        "        // tools/applewin/build-applewin-core.sh. Loaded once per id into a\n"
        "        // process-lifetime cache (callers keep the returned pointer); only\n"
        "        // ever called from the emulator thread.\n"
        "        static const std::map<WORD, const char *> s_romFiles = {\n"
        "            {IDR_APPLE2_ROM, \"Apple2.rom\"},\n"
        "            {IDR_APPLE2_PLUS_ROM, \"Apple2_Plus.rom\"},\n"
        "            {IDR_APPLE2_JPLUS_ROM, \"Apple2_JPlus.rom\"},\n"
        "            {IDR_APPLE2E_ROM, \"Apple2e.rom\"},\n"
        "            {IDR_APPLE2E_ENHANCED_ROM, \"Apple2e_Enhanced.rom\"},\n"
        "            {IDR_PRAVETS_82_ROM, \"PRAVETS82.ROM\"},\n"
        "            {IDR_PRAVETS_8M_ROM, \"PRAVETS8M.ROM\"},\n"
        "            {IDR_PRAVETS_8C_ROM, \"PRAVETS8C.ROM\"},\n"
        "            {IDR_TK3000_2E_ROM, \"TK3000e.rom\"},\n"
        "            {IDR_BASE_64A_ROM, \"Base64A.rom\"},\n"
        "            {IDR_FREEZES_F8_ROM, \"Freezes_Non-autostart_F8_Rom.rom\"},\n"
        "            {IDR_APPLE2_VIDEO_ROM, \"Apple2_Video.rom\"},\n"
        "            {IDR_APPLE2_JPLUS_VIDEO_ROM, \"Apple2_JPlus_Video.rom\"},\n"
        "            {IDR_APPLE2E_ENHANCED_VIDEO_ROM, \"Apple2e_Enhanced_Video.rom\"},\n"
        "            {IDR_BASE64A_VIDEO_ROM, \"Base64A_German_Video.rom\"},\n"
        "            {IDR_DISK2_13SECTOR_FW, \"DISK2-13sector.rom\"},\n"
        "            {IDR_DISK2_16SECTOR_FW, \"DISK2.rom\"},\n"
        "            {IDR_SSC_FW, \"SSC.rom\"},\n"
        "            {IDR_PRINTDRVR_FW, \"Parallel.rom\"},\n"
        "            {IDR_MOCKINGBOARD_D_FW, \"Mockingboard-D.rom\"},\n"
        "            {IDR_MOUSEINTERFACE_FW, \"MouseInterface.rom\"},\n"
        "            {IDR_THUNDERCLOCKPLUS_FW, \"ThunderClockPlus.rom\"},\n"
        "            {IDR_TKCLOCK_FW, \"TKClock.rom\"},\n"
        "            {IDB_CHARSET82, \"CHARSET82.bmp\"},\n"
        "            {IDB_CHARSET8M, \"CHARSET8M.bmp\"},\n"
        "            {IDB_CHARSET8C, \"CHARSET8C.bmp\"},\n"
        "        };\n"
        "        const char *romsDir = std::getenv(\"APPLE2_ROMS_DIR\");\n"
        "        const auto nameIt = s_romFiles.find(id);\n"
        "        if (romsDir && nameIt != s_romFiles.end())\n"
        "        {\n"
        "            static std::map<WORD, std::vector<unsigned char>> s_cache;\n"
        "            auto cIt = s_cache.find(id);\n"
        "            if (cIt == s_cache.end())\n"
        "            {\n"
        "                std::ifstream f(std::filesystem::path(romsDir) / nameIt->second, std::ios::binary);\n"
        "                if (f)\n"
        "                {\n"
        "                    std::vector<unsigned char> bytes(\n"
        "                        (std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());\n"
        "                    if (!bytes.empty())\n"
        "                    {\n"
        "                        cIt = s_cache.emplace(id, std::move(bytes)).first;\n"
        "                    }\n"
        "                }\n"
        "            }\n"
        "            if (cIt != s_cache.end())\n"
        "            {\n"
        "                return {cIt->second.data(), static_cast<unsigned int>(cIt->second.size())};\n"
        "            }\n"
        "        }\n"
        "        const auto it = apple2roms::data.find(id);\n"
        "        if (it == apple2roms::data.end())\n",
    ),
])

# Register slot1/slot2 core options with Empty as the default. CardManager's
# constructor defaults are Printer (slot 1) and SSC (slot 2), whose firmware
# (Parallel.rom / SSC.rom) is stripped from release builds -- without these
# options forcing the slots empty, every boot would fetch the missing firmware
# and fail the load. The Android session sets both to "Empty" via
# nativeSetCoreOption before start (users who import those firmwares can be
# offered the cards later).
patch("source/frontends/libretro/retroregistry.cpp", [
    (
        '        {\n'
        '            {\n'
        '                "slot3",',
        '        {\n'
        '            {\n'
        '                "slot1",\n'
        '                "Card in Slot 1",\n'
        '                CATEGORY_SYSTEM,\n'
        '                {\n'
        '                    {"Empty", CT_Empty},\n'
        '                    {"Printer", CT_GenericPrinter},\n'
        '                },\n'
        '            },\n'
        '            "Configuration\\\\Slot 1",\n'
        '            REGVALUE_CARD_TYPE, // reset required\n'
        '        },\n'
        '        {\n'
        '            {\n'
        '                "slot2",\n'
        '                "Card in Slot 2",\n'
        '                CATEGORY_SYSTEM,\n'
        '                {\n'
        '                    {"Empty", CT_Empty},\n'
        '                    {"SSC", CT_SSC},\n'
        '                },\n'
        '            },\n'
        '            "Configuration\\\\Slot 2",\n'
        '            REGVALUE_CARD_TYPE, // reset required\n'
        '        },\n'
        '        {\n'
        '            {\n'
        '                "slot3",',
    ),
])

# --- Apple ROM strip (default; skipped with --with-roms) ---------------------
# Remove every copyrighted resource from the apple2roms embed list and delete
# the corresponding files from the staged tree, keeping only AppleWin-authored
# GPL-safe firmware (Hddrvr/Hddrvr-v2/HDC-SmartPort/spoverslip) and non-ROM
# assets (logo/icon/debug font). Also drop the Apple-copyrighted disk images
# staged under bin/ (they are never packaged, but keep the tree clean).
if not with_roms:
    strip_ids = {
        "IDR_DISK2_13SECTOR_FW", "IDR_DISK2_16SECTOR_FW", "IDR_SSC_FW",
        "IDR_PRINTDRVR_FW", "IDR_MOCKINGBOARD_D_FW", "IDR_MOUSEINTERFACE_FW",
        "IDR_THUNDERCLOCKPLUS_FW", "IDR_TKCLOCK_FW",
        "IDR_APPLE2_ROM", "IDR_APPLE2_PLUS_ROM", "IDR_APPLE2_JPLUS_ROM",
        "IDR_APPLE2E_ROM", "IDR_APPLE2E_ENHANCED_ROM",
        "IDR_PRAVETS_82_ROM", "IDR_PRAVETS_8M_ROM", "IDR_PRAVETS_8C_ROM",
        "IDR_TK3000_2E_ROM", "IDR_BASE_64A_ROM", "IDR_FREEZES_F8_ROM",
        "IDR_APPLE2_VIDEO_ROM", "IDR_APPLE2_JPLUS_VIDEO_ROM",
        "IDR_APPLE2E_ENHANCED_VIDEO_ROM", "IDR_BASE64A_VIDEO_ROM",
        "IDB_CHARSET82", "IDB_CHARSET8M", "IDB_CHARSET8C",
    }
    cml = root / "resource/CMakeLists.txt"
    kept, removed_files = [], []
    for line in cml.read_text().splitlines(keepends=True):
        m = re.match(r'\s*(ID[A-Z0-9_]+)\s+"([^"]+)"', line)
        if m and m.group(1) in strip_ids:
            removed_files.append(m.group(2))
            continue
        kept.append(line)
    if len(removed_files) != len(strip_ids):
        missing = strip_ids - {m.group(1) for l in cml.read_text().splitlines()
                               if (m := re.match(r'\s*(ID[A-Z0-9_]+)\s+"', l))}
        sys.exit(f"build-applewin-core.sh: ROM strip expected {len(strip_ids)} "
                 f"entries, removed {len(removed_files)} (unmatched: {sorted(missing)})")
    cml.write_text("".join(kept))
    for name in removed_files:
        f = root / "resource" / name
        if f.is_file():
            f.unlink()
    for name in ["DOS 3.3 System Master - 680-0210-A.dsk", "ProDOS_2_4_3.po"]:
        f = root / "bin" / name
        if f.is_file():
            f.unlink()
    print(f"build-applewin-core.sh: stripped {len(removed_files)} copyrighted "
          "resources from apple2roms (release policy)")
PY

cat > "${STAMP_PATH}" <<EOF
source_dir=${SOURCE_DIR}
source_branch=${SOURCE_BRANCH}
source_commit=${SOURCE_COMMIT}
source_fingerprint=${FP}
with_roms=${WITH_ROMS}
EOF

echo "AppleWin core staged: ${GENERATED_ROOT}"
