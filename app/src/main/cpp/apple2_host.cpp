#include "apple2_host.h"

#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "libretro.h"

#define LOG_TAG "Apple2Host"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// --- frame sink -------------------------------------------------------------
Apple2FrameSink g_frame_sink = nullptr;
void* g_frame_user = nullptr;
std::vector<uint32_t> g_repack;  // tight XRGB8888 scratch (emulator thread only)

// --- geometry ---------------------------------------------------------------
int g_width = 560;
int g_height = 384;

// --- audio ring (interleaved stereo int16 @ 44100) --------------------------
// The libretro core pushes samples once per ~60Hz frame (producer); the Kotlin
// audio feeder pulls fixed full blocks (consumer). The consumer blocks on
// g_audio_cv until a whole block is ready, so it always writes full, real-time-
// paced blocks to AudioTrack -- no partial/choppy writes -- and degrades to a
// brief silence pad on underrun instead of stuttering.
std::mutex g_audio_mutex;
std::condition_variable g_audio_cv;
std::vector<int16_t> g_audio;            // FIFO
bool g_audio_active = true;
// ~250ms cap: enough elastic buffer to ride out frame-pacing jitter without
// unbounded latency growth.
constexpr size_t kAudioCapSamples = (44100 / 4) * 2;

// --- core options (libretro variables) --------------------------------------
std::mutex g_var_mutex;
std::map<std::string, std::string> g_vars;
bool g_vars_dirty = false;

// --- input ------------------------------------------------------------------
constexpr int kMaxPorts = 2;
constexpr int kJoypadIds = 16;
int16_t g_buttons[kMaxPorts][kJoypadIds] = {};
int16_t g_axes[kMaxPorts][4] = {};       // [analog index*2 + x/y]

// --- keyboard ---------------------------------------------------------------
retro_keyboard_event_t g_keyboard_cb = nullptr;

// --- logging ----------------------------------------------------------------
void RETRO_CALLCONV host_log(enum retro_log_level level, const char* fmt, ...) {
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case RETRO_LOG_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_WARN:  prio = ANDROID_LOG_WARN;  break;
        case RETRO_LOG_ERROR: prio = ANDROID_LOG_ERROR; break;
        default:              prio = ANDROID_LOG_INFO;  break;
    }
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(prio, "Apple2Core", fmt, ap);
    va_end(ap);
}

// Parse a SET_VARIABLES value string ("Description; Default|Other|...") and
// return the default option (the first token after "; ").
std::string default_from_spec(const char* spec) {
    if (!spec) return {};
    const char* semi = std::strstr(spec, "; ");
    const char* opts = semi ? semi + 2 : spec;
    const char* bar = std::strchr(opts, '|');
    return bar ? std::string(opts, bar - opts) : std::string(opts);
}

// --- libretro callbacks -----------------------------------------------------
void RETRO_CALLCONV host_video_refresh(const void* data, unsigned width,
                                       unsigned height, size_t pitch) {
    if (!data || !g_frame_sink || width == 0 || height == 0) return;
    const auto* src = static_cast<const uint8_t*>(data);
    const size_t row_px = width;
    if (g_repack.size() != row_px * height) g_repack.resize(row_px * height);
    for (unsigned y = 0; y < height; ++y) {
        std::memcpy(g_repack.data() + static_cast<size_t>(y) * row_px,
                    src + static_cast<size_t>(y) * pitch,
                    row_px * sizeof(uint32_t));
    }
    g_frame_sink(g_repack.data(), static_cast<int>(width), static_cast<int>(height),
                 g_frame_user);
}

void push_audio(const int16_t* data, size_t count) {
    {
        std::lock_guard<std::mutex> lock(g_audio_mutex);
        g_audio.insert(g_audio.end(), data, data + count);
        if (g_audio.size() > kAudioCapSamples) {
            // Drop oldest in whole stereo frames to bound latency if the consumer
            // falls behind (keeps L/R alignment, avoids a channel-swap glitch).
            size_t drop = g_audio.size() - kAudioCapSamples;
            drop &= ~static_cast<size_t>(1);
            g_audio.erase(g_audio.begin(), g_audio.begin() + drop);
        }
    }
    g_audio_cv.notify_one();
}

size_t RETRO_CALLCONV host_audio_batch(const int16_t* data, size_t frames) {
    if (data && frames) push_audio(data, frames * 2);
    return frames;
}

void RETRO_CALLCONV host_audio_sample(int16_t left, int16_t right) {
    const int16_t pair[2] = {left, right};
    push_audio(pair, 2);
}

void RETRO_CALLCONV host_input_poll(void) {}

int16_t RETRO_CALLCONV host_input_state(unsigned port, unsigned device,
                                        unsigned index, unsigned id) {
    if (port >= kMaxPorts) return 0;
    switch (device) {
        case RETRO_DEVICE_JOYPAD:
            if (id < kJoypadIds) return g_buttons[port][id];
            return 0;
        case RETRO_DEVICE_ANALOG: {
            const int slot = (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT ? 2 : 0)
                           + (id == RETRO_DEVICE_ID_ANALOG_Y ? 1 : 0);
            if (slot < 4) return g_axes[port][slot];
            return 0;
        }
        default:
            return 0;
    }
}

bool RETRO_CALLCONV host_environment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const auto fmt = *static_cast<const enum retro_pixel_format*>(data);
            return fmt == RETRO_PIXEL_FORMAT_XRGB8888;
        }
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            static_cast<retro_log_callback*>(data)->log = host_log;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK: {
            g_keyboard_cb = static_cast<const retro_keyboard_callback*>(data)->callback;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *static_cast<bool*>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return false;  // core reads individual buttons
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE:
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE:
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *static_cast<unsigned*>(data) = 0;  // use the SET_VARIABLES path
            return true;
        case RETRO_ENVIRONMENT_GET_DISK_CONTROL_INTERFACE_VERSION:
            return false;  // core falls back to the basic interface
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            std::lock_guard<std::mutex> lock(g_var_mutex);
            for (auto* v = static_cast<const retro_variable*>(data); v && v->key; ++v) {
                // Don't clobber an override already set by the session.
                if (g_vars.find(v->key) == g_vars.end()) {
                    g_vars[v->key] = default_from_spec(v->value);
                }
            }
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto* v = static_cast<retro_variable*>(data);
            if (!v || !v->key) return false;
            std::lock_guard<std::mutex> lock(g_var_mutex);
            auto it = g_vars.find(v->key);
            if (it == g_vars.end() || it->second.empty()) {
                v->value = nullptr;
                return false;
            }
            v->value = it->second.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            std::lock_guard<std::mutex> lock(g_var_mutex);
            *static_cast<bool*>(data) = g_vars_dirty;
            g_vars_dirty = false;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            const auto* m = static_cast<const retro_message*>(data);
            if (m && m->msg) LOGI("core message: %s", m->msg);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            const auto* gi = static_cast<const retro_game_geometry*>(data);
            if (gi && gi->base_width && gi->base_height) {
                g_width = static_cast<int>(gi->base_width);
                g_height = static_cast<int>(gi->base_height);
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            const auto* av = static_cast<const retro_system_av_info*>(data);
            if (av && av->geometry.base_width && av->geometry.base_height) {
                g_width = static_cast<int>(av->geometry.base_width);
                g_height = static_cast<int>(av->geometry.base_height);
            }
            return true;
        }
        default:
            return false;
    }
}

}  // namespace

// --- C API ------------------------------------------------------------------
extern "C" {

void apple2host_set_frame_sink(Apple2FrameSink sink, void* user) {
    g_frame_sink = sink;
    g_frame_user = user;
}

void apple2host_set_variable(const char* key, const char* value) {
    if (!key || !value) return;
    std::lock_guard<std::mutex> lock(g_var_mutex);
    g_vars[key] = value;
    g_vars_dirty = true;
}

bool apple2host_core_start(void) {
    retro_set_environment(host_environment);
    retro_set_video_refresh(host_video_refresh);
    retro_set_audio_sample(host_audio_sample);
    retro_set_audio_sample_batch(host_audio_batch);
    retro_set_input_poll(host_input_poll);
    retro_set_input_state(host_input_state);

    retro_init();
    retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    retro_set_controller_port_device(1, RETRO_DEVICE_JOYPAD);

    if (!retro_load_game(nullptr)) {
        LOGE("retro_load_game(no content) failed");
        return false;
    }

    retro_system_av_info av;
    std::memset(&av, 0, sizeof(av));
    retro_get_system_av_info(&av);
    if (av.geometry.base_width && av.geometry.base_height) {
        g_width = static_cast<int>(av.geometry.base_width);
        g_height = static_cast<int>(av.geometry.base_height);
    }
    LOGI("core started: %dx%d @ %.2ffps, %.0fHz audio",
         g_width, g_height, av.timing.fps, av.timing.sample_rate);
    return true;
}

void apple2host_core_run_frame(void) { retro_run(); }

void apple2host_core_stop(void) {
    retro_unload_game();
    retro_deinit();
    apple2host_clear_audio();
}

void apple2host_core_reset(void) { retro_reset(); }

void apple2host_get_geometry(int* width, int* height) {
    if (width) *width = g_width;
    if (height) *height = g_height;
}

int apple2host_fill_audio(int16_t* out, int maxSamples) {
    if (!out || maxSamples <= 0) return 0;
    const size_t want = static_cast<size_t>(maxSamples);
    std::unique_lock<std::mutex> lock(g_audio_mutex);
    // Wait for a whole block so the consumer always writes a full, paced buffer.
    // Bounded wait (~1.5 block-durations at 44100Hz stereo) so a producer stall
    // degrades to a brief silence pad rather than blocking the audio thread.
    const auto budget = std::chrono::microseconds(
        (want * 1'000'000ULL) / (44100ULL * 2ULL) + 8'000ULL);
    g_audio_cv.wait_for(lock, budget, [&] {
        return !g_audio_active || g_audio.size() >= want;
    });
    const size_t have = std::min(g_audio.size(), want);
    if (have > 0) {
        std::memcpy(out, g_audio.data(), have * sizeof(int16_t));
        g_audio.erase(g_audio.begin(), g_audio.begin() + have);
    }
    if (have < want) {
        // Silence-pad the remainder so AudioTrack still gets a full block.
        std::memset(out + have, 0, (want - have) * sizeof(int16_t));
    }
    return static_cast<int>(want);
}

void apple2host_audio_set_active(int active) {
    {
        std::lock_guard<std::mutex> lock(g_audio_mutex);
        g_audio_active = active != 0;
    }
    g_audio_cv.notify_all();  // wake a blocked fill on shutdown
}

void apple2host_clear_audio(void) {
    {
        std::lock_guard<std::mutex> lock(g_audio_mutex);
        g_audio.clear();
    }
    g_audio_cv.notify_all();
}

void apple2host_inject_key(int down, unsigned keycode, uint32_t character, uint16_t mods) {
    if (g_keyboard_cb) g_keyboard_cb(down != 0, keycode, character, mods);
}

void apple2host_set_joystick_button(int port, int id, int pressed) {
    if (port >= 0 && port < kMaxPorts && id >= 0 && id < kJoypadIds) {
        g_buttons[port][id] = pressed ? 1 : 0;
    }
}

void apple2host_set_joystick_axis(int port, int axis, int16_t value) {
    if (port >= 0 && port < kMaxPorts && axis >= 0 && axis < 4) {
        g_axes[port][axis] = value;
    }
}

}  // extern "C"
