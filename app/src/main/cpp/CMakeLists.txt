cmake_minimum_required(VERSION 3.22.1)

project("apple2core")

# ---------------------------------------------------------------------------
# Staged AppleWin (FujiNetWIFI 'linux' branch) libretro core.
#
# tools/applewin/build-applewin-core.sh stages a subset of the AppleWin tree
# into ../cpp-generated/applewin and applies a few idempotent CMake edits so it
# builds as a subdirectory here (AppleWin normally assumes it is the top-level
# project). We compile its libretro frontend (now a STATIC lib) plus appleii /
# common2 / the Win32-on-POSIX shim / yaml / minizip / embedded ROMs, and link
# the whole thing into one libapple2core.so. The Android host (apple2_host.cpp)
# drives the core through the standard libretro C ABI.
# ---------------------------------------------------------------------------

set(AW "${CMAKE_CURRENT_SOURCE_DIR}/../cpp-generated/applewin")

if(NOT EXISTS "${AW}/source/frontends/libretro/libretro.cpp")
    message(FATAL_ERROR
        "Staged AppleWin source tree is missing. "
        "Run tools/applewin/build-applewin-core.sh before configuring CMake.")
endif()

# --- Globals AppleWin's top-level CMakeLists would normally establish --------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# AppleWin is a former Windows project: it uses _DEBUG, not NDEBUG.
add_compile_definitions("$<$<CONFIG:DEBUG>:_DEBUG>")
# Disable the VICE Z80's x86-32 regparm calling-convention optimisation. On the
# 32-bit x86 ABI clang defines __i386__, which turns REGPARM1/2 into
# __attribute__((regparm(n))); AppleWin declares some Uthernet handlers without
# the macro and defines them with it, which is a hard error on x86. The opt is
# pure micro-tuning, so disabling it is correctness-neutral (no-op on other ABIs).
add_compile_definitions(NO_REGPARM)
add_compile_options(-Wno-switch -Wno-multichar -Wno-psabi)
# AppleWin sources include their own headers relative to source/.
include_directories("${AW}/source")

# zlib comes from the NDK sysroot. Resolve it here, in the *parent* scope, so the
# value is inherited by every AppleWin subdirectory (sibling subdirectories do
# not see each other's find_package results otherwise).
find_package(ZLIB REQUIRED)

# Boost is header-only for AppleWin's use (property_tree / multi_array in
# common2). Boost headers are architecture-independent, so the host's installed
# copy is fine for an Android cross-build. Override with -DAPPLE2_BOOST_INCLUDE_DIR.
if(DEFINED APPLE2_BOOST_INCLUDE_DIR)
    # Trust an explicit override verbatim (the Android toolchain would otherwise
    # re-root the path into the NDK sysroot).
    set(APPLE2_BOOST_INCLUDE "${APPLE2_BOOST_INCLUDE_DIR}")
else()
    # Boost headers are architecture-independent, so search the *host* include
    # paths. NO_CMAKE_FIND_ROOT_PATH bypasses the Android toolchain's
    # CMAKE_FIND_ROOT_PATH_MODE_INCLUDE=ONLY restriction that hides /usr/include.
    find_path(APPLE2_BOOST_INCLUDE
        NAMES boost/version.hpp
        PATHS /usr/include /usr/local/include "$ENV{BOOST_ROOT}" "$ENV{BOOST_ROOT}/include"
        NO_DEFAULT_PATH
        NO_CMAKE_FIND_ROOT_PATH)
endif()
if(NOT APPLE2_BOOST_INCLUDE)
    message(FATAL_ERROR
        "Boost headers (boost/version.hpp) not found. Install Boost dev headers "
        "or pass -DAPPLE2_BOOST_INCLUDE_DIR=/path/to/include.")
endif()
# Setting Boost_INCLUDE_DIRS in parent scope makes the staged AppleWin
# CMakeLists skip find_package(Boost).
#
# Boost usually lives in /usr/include, which we must NOT put on the cross-build
# include path wholesale -- doing so leaks the host's glibc headers (gnu/stubs.h
# etc.) ahead of the NDK sysroot. Instead, build a tiny shim directory whose only
# entry is a "boost" symlink, and add THAT via -isystem. The compiler then sees
# boost/* and nothing else from the host. (include_directories() can't be used:
# CMake silently drops dirs it regards as implicit system dirs.)
set(Boost_INCLUDE_DIRS "${APPLE2_BOOST_INCLUDE}")
set(_boost_shim "${CMAKE_BINARY_DIR}/boost-shim")
file(MAKE_DIRECTORY "${_boost_shim}")
if(NOT EXISTS "${_boost_shim}/boost")
    file(CREATE_LINK "${APPLE2_BOOST_INCLUDE}/boost" "${_boost_shim}/boost" SYMBOLIC)
endif()
add_compile_options("-isystem" "${_boost_shim}")
message(STATUS "apple2core: using Boost headers from ${APPLE2_BOOST_INCLUDE} (via ${_boost_shim})")

# ARM tuning (no-ops on x86/x86_64).
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_ANDROID_ARM_MODE ON)
    set(CMAKE_ANDROID_ARM_NEON ON)
endif()

# 16K page-size support for newer NDKs on ARM (lld expects -z,max-page-size).
if((ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
        AND (NOT DEFINED CMAKE_ANDROID_NDK_VERSION OR CMAKE_ANDROID_NDK_VERSION VERSION_GREATER_EQUAL 26))
    add_link_options(-Wl,-z,max-page-size=16384)
endif()

# --- AppleWin component libraries, in dependency order ----------------------
add_subdirectory("${AW}/libyaml"                    applewin-libyaml)
add_subdirectory("${AW}/minizip"                    applewin-minizip)
add_subdirectory("${AW}/source/linux/libwindows"    applewin-libwindows)
add_subdirectory("${AW}/source"                     applewin-source)
add_subdirectory("${AW}/resource"                   applewin-resource)
add_subdirectory("${AW}/source/frontends/common2"   applewin-common2)
add_subdirectory("${AW}/source/frontends/libretro"  applewin-libretro)

# --- The app's native library -----------------------------------------------
add_library(
        apple2core
        SHARED
        apple2_core.cpp
        apple2_host.cpp
        session_runtime.cpp
        fujinet_android.cpp
)

target_include_directories(apple2core PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        "${AW}/source"
        "${AW}/source/frontends/libretro/libretro-common/include"
)

# The host references retro_* directly, which pulls the libretro objects (and
# transitively appleii / common2 / windows / yaml / minizip / apple2roms / zlib)
# into libapple2core.so.
target_link_libraries(apple2core
        applewin_libretro
        ZLIB::ZLIB
        log
        android
        dl       # fujinet_android.cpp dlopen's libfujinet.so
)
