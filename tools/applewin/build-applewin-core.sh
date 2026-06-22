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
# Apple II system ROMs and the SP-over-SLIP firmware are compiled into the core
# via AppleWin's own `apple2roms` resource target (xxd -i), so -- unlike adam,
# which bundles ROMs as assets -- this script stages no ROM assets.
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
python3 - "${GENERATED_ROOT}" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])

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
PY

cat > "${STAMP_PATH}" <<EOF
source_dir=${SOURCE_DIR}
source_branch=${SOURCE_BRANCH}
source_commit=${SOURCE_COMMIT}
source_fingerprint=${FP}
EOF

echo "AppleWin core staged: ${GENERATED_ROOT}"
