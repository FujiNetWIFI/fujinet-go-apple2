#include "session_runtime.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <dlfcn.h>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>

#include "apple2_host.h"

// FujiNet runtime dlopen wrapper (fujinet_android.cpp).
extern "C" {
bool        FujiNetAndroid_StartRuntime(const char* runtimeRootPath,
                                        const char* configPath,
                                        const char* sdPath,
                                        const char* dataPath,
                                        int listenPort);
void        FujiNetAndroid_StopRuntime();
const char* FujiNetAndroid_LastErrorMessage();
bool        FujiNetAndroid_IsRuntimeRunning();
}

#define LOG_TAG "Apple2Session"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr auto kFramePeriod = std::chrono::nanoseconds(1'000'000'000 / 60);
constexpr int64_t kFrameTargetNs = 1'000'000'000LL / 60;

void frame_sink_trampoline(const uint32_t* xrgb, int w, int h, void* ud) {
    static_cast<SessionRuntime*>(ud)->OnFrame(xrgb, w, h);
}

// ADPF (Android Dynamic Performance Framework) "performance hint" session. This
// tells the SoC scheduler/governor the emulator thread's per-frame CPU work and
// its frame deadline, so it keeps clocks up for the 60Hz loop instead of letting
// DVFS race-to-idle drop the thread off schedule (the root cause of audio
// underruns / video judder). It is what vendor "game" governors (e.g. MediaTek
// GameTime, Qualcomm) consume. API 33+; dlsym'd so the app still runs below 33.
class PerfHint {
public:
    void start(int64_t targetNs) {
        auto getManager = reinterpret_cast<void* (*)()>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_getManager"));
        create_ = reinterpret_cast<void* (*)(void*, const int32_t*, size_t, int64_t)>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_createSession"));
        report_ = reinterpret_cast<void (*)(void*, int64_t)>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_reportActualWorkDuration"));
        close_ = reinterpret_cast<void (*)(void*)>(
            dlsym(RTLD_DEFAULT, "APerformanceHint_closeSession"));
        if (!getManager || !create_ || !report_) {
            LOGW("ADPF unavailable (APerformanceHint symbols missing; pre-API-33)");
            return;
        }
        void* mgr = getManager();
        if (!mgr) {
            LOGW("ADPF unavailable (no performance hint manager on this device)");
            return;
        }
        const int32_t tid = static_cast<int32_t>(syscall(SYS_gettid));
        session_ = create_(mgr, &tid, 1, targetNs);
        if (session_) {
            LOGI("ADPF performance hint session active (target %lldns)",
                 static_cast<long long>(targetNs));
        } else {
            LOGW("ADPF createSession returned null");
        }
    }
    void report(int64_t actualNs) const {
        if (session_ && report_) report_(session_, actualNs);
    }
    void stop() {
        if (session_ && close_) close_(session_);
        session_ = nullptr;
    }
private:
    void* session_ = nullptr;
    void* (*create_)(void*, const int32_t*, size_t, int64_t) = nullptr;
    void (*report_)(void*, int64_t) = nullptr;
    void (*close_)(void*) = nullptr;
};
}  // namespace

SessionRuntime& SessionRuntime::Get() {
    static SessionRuntime instance;
    return instance;
}

void SessionRuntime::StartSession(const std::string& runtime_root,
                                  const std::string& config_path,
                                  const std::string& sd_path,
                                  const std::string& data_path) {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (running_.load()) {
        LOGW("StartSession ignored; session already running");
        return;
    }

    runtime_root_ = runtime_root;
    config_path_ = config_path;
    sd_path_ = sd_path;
    data_path_ = data_path;

    // AppleWin resolves its settings dir from $HOME ($HOME/.config/applewin),
    // which Android does not set. Point it at the app's writable runtime root.
    if (!runtime_root_.empty()) {
        setenv("HOME", runtime_root_.c_str(), 1);
    }

    apple2host_set_frame_sink(&frame_sink_trampoline, this);

    // The machine type and slot cards (incl. the FujiNet SmartPort-over-SLIP
    // card, which starts AppleWin's Listener on kSlipPort) are selected by the
    // Kotlin layer via nativeSetCoreOption(...) -> apple2host_set_variable before
    // this call; apple2host's SET_VARIABLES handler preserves those overrides
    // when the core reads its variables during retro_load_game.

    emu_should_run_.store(true);
    running_.store(true);
    render_running_.store(true);
    render_thread_ = std::thread(&SessionRuntime::RenderThreadMain, this);
    emulator_thread_ = std::thread(&SessionRuntime::EmulatorThreadMain, this);

    // Start the in-process FujiNet runtime. iwm_slip retries the SLIP connection
    // every 100ms until AppleWin's CT_SmartPortOverSlip Listener is up, so this
    // ordering relative to the emulator thread's core start is forgiving.
    if (!runtime_root_.empty() && !config_path_.empty() && !sd_path_.empty()) {
        if (!FujiNetAndroid_StartRuntime(runtime_root_.c_str(), config_path_.c_str(),
                                         sd_path_.c_str(), data_path_.c_str(), kSlipPort)) {
            const char* err = FujiNetAndroid_LastErrorMessage();
            LOGE("FujiNet runtime failed to start: %s", err ? err : "(unknown)");
            // Continue: the Apple II still boots, just without the FujiNet drive.
        }
    } else {
        LOGW("FujiNet paths not provided; starting emulator without FujiNet");
    }

    LOGI("Session started (SLIP %d)", kSlipPort);
}

void SessionRuntime::EmulatorThreadMain() {
    pthread_setname_np(pthread_self(), "apple2-emu");
    // Raise priority so the 60Hz frame schedule isn't preempted by UI work
    // (THREAD_PRIORITY_URGENT_DISPLAY = -8), but stay below the audio feeder.
    setpriority(PRIO_PROCESS, 0, -8);

    if (!apple2host_core_start()) {
        LOGE("Emulator core failed to start");
        running_.store(false);
        return;
    }

    PerfHint perf;
    perf.start(kFrameTargetNs);

    auto next = std::chrono::steady_clock::now();
    while (emu_should_run_.load()) {
        const auto work_start = std::chrono::steady_clock::now();
        // Apply a pending reset on the emulator thread (the core is not
        // thread-safe; RequestReset is called from the JNI/UI thread).
        if (reset_requested_.exchange(false)) {
            apple2host_core_reset();
        }
        apple2host_core_run_frame();
        // Report the CPU work this frame actually took (excludes the sleep), so
        // the governor sizes clocks to the real per-frame load.
        const auto work_end = std::chrono::steady_clock::now();
        perf.report(std::chrono::duration_cast<std::chrono::nanoseconds>(
            work_end - work_start).count());

        next += kFramePeriod;
        const auto now = std::chrono::steady_clock::now();
        if (next > now) {
            std::this_thread::sleep_for(next - now);
        } else if (now - next > std::chrono::milliseconds(100)) {
            // Fell far behind (e.g. after a stall); resync rather than spin.
            next = now;
        }
    }

    perf.stop();
    apple2host_core_stop();
    running_.store(false);
    LOGI("Emulator thread exited");
}

void SessionRuntime::StopSession() {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (!running_.load() && !emulator_thread_.joinable()) {
        return;
    }

    emu_should_run_.store(false);
    if (emulator_thread_.joinable()) {
        emulator_thread_.join();
    }

    render_running_.store(false);
    SignalRepaint();
    if (render_thread_.joinable()) {
        render_thread_.join();
    }

    FujiNetAndroid_StopRuntime();

    running_.store(false);
    apple2host_set_frame_sink(nullptr, nullptr);
    LOGI("Session stopped");
}

void SessionRuntime::AttachSurface(JNIEnv* env, jobject surface) {
    std::lock_guard<std::mutex> lock(surface_mutex_);
    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
    if (surface) {
        window_ = ANativeWindow_fromSurface(env, surface);
        LOGI("AttachSurface: window=%p", static_cast<void*>(window_));
    }
    SignalRepaint();
}

void SessionRuntime::DetachSurface(JNIEnv* /*env*/) {
    std::lock_guard<std::mutex> lock(surface_mutex_);
    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
}

void SessionRuntime::OnFrame(const uint32_t* xrgb8888, int width, int height) {
    if (!xrgb8888 || width <= 0 || height <= 0) return;
    const size_t pixels = static_cast<size_t>(width) * height;
    {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        if (last_frame_.size() != pixels) last_frame_.resize(pixels);
        std::memcpy(last_frame_.data(), xrgb8888, pixels * sizeof(uint32_t));
        last_frame_w_ = width;
        last_frame_h_ = height;
        frame_dirty_ = true;
    }
    frame_cv_.notify_one();
}

void SessionRuntime::SignalRepaint() {
    {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        frame_dirty_ = true;
    }
    frame_cv_.notify_one();
}

void SessionRuntime::RenderThreadMain() {
    pthread_setname_np(pthread_self(), "apple2-render");
    std::vector<uint32_t> scratch;
    int w = 0, h = 0;
    while (render_running_.load()) {
        {
            std::unique_lock<std::mutex> lock(frame_mutex_);
            frame_cv_.wait(lock, [this] { return frame_dirty_ || !render_running_.load(); });
            if (!render_running_.load()) break;
            frame_dirty_ = false;
            if (last_frame_.empty()) continue;
            scratch = last_frame_;
            w = last_frame_w_;
            h = last_frame_h_;
        }
        ANativeWindow* w_local = nullptr;
        {
            std::lock_guard<std::mutex> lock(surface_mutex_);
            if (window_) {
                w_local = window_;
                ANativeWindow_acquire(w_local);
            }
        }
        if (w_local) {
            PresentTo(w_local, scratch.data(), w, h);
            ANativeWindow_release(w_local);
        }
    }
}

void SessionRuntime::PresentTo(ANativeWindow* w, const uint32_t* xrgb8888, int width, int height) {
    if (!w || !xrgb8888) return;

    ANativeWindow_setBuffersGeometry(w, width, height, WINDOW_FORMAT_RGBA_8888);

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(w, &buffer, nullptr) != 0) {
        return;
    }
    const int copy_w = buffer.width < width ? buffer.width : width;
    const int copy_h = buffer.height < height ? buffer.height : height;
    auto* dst = static_cast<uint32_t*>(buffer.bits);
    for (int y = 0; y < copy_h; ++y) {
        const uint32_t* src_row = xrgb8888 + static_cast<size_t>(y) * width;
        uint32_t* dst_row = dst + static_cast<size_t>(y) * buffer.stride;
        for (int x = 0; x < copy_w; ++x) {
            // libretro XRGB8888 (0x00RRGGBB) -> Android RGBA_8888 (mem R,G,B,A =
            // 0xAABBGGRR): swap R/B and force opaque alpha.
            const uint32_t p = src_row[x];
            dst_row[x] = 0xFF000000u
                       | ((p & 0x000000FFu) << 16)
                       | (p & 0x0000FF00u)
                       | ((p & 0x00FF0000u) >> 16);
        }
    }
    ANativeWindow_unlockAndPost(w);
}

void SessionRuntime::RequestReset() { reset_requested_.store(true); }
