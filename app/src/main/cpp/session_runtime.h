#pragma once

#include <jni.h>
#include <android/native_window.h>

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

// Orchestrates one Apple II session: AppleWin's libretro core (driven one frame
// per retro_run() on a worker thread) plus -- from Phase 4 -- the in-process
// FujiNet runtime, joined over SmartPort-over-SLIP on loopback TCP 1985 (the
// emulator's SmartPort card listens, FujiNet connects in).
class SessionRuntime {
public:
    static SessionRuntime& Get();

    // FujiNet runtime root/config/SD/data paths (used from Phase 4; the core
    // itself boots from embedded ROMs and needs no paths).
    void StartSession(const std::string& runtime_root,
                      const std::string& config_path,
                      const std::string& sd_path,
                      const std::string& data_path);
    void StopSession();
    bool IsRunning() const { return running_.load(); }

    void AttachSurface(JNIEnv* env, jobject surface);
    void DetachSurface(JNIEnv* env);

    void RequestReset();

    // Called (on the emulator thread) by the host's video_refresh sink.
    void OnFrame(const uint32_t* xrgb8888, int width, int height);

private:
    SessionRuntime() = default;
    SessionRuntime(const SessionRuntime&) = delete;
    SessionRuntime& operator=(const SessionRuntime&) = delete;

    void EmulatorThreadMain();
    void RenderThreadMain();
    void PresentTo(ANativeWindow* w, const uint32_t* xrgb8888, int width, int height);
    void SignalRepaint();

    // SmartPort-over-SLIP loopback port (AppleWin's CT_SmartPortOverSlip
    // Listener default); FujiNet connects here.
    static constexpr int kSlipPort = 1985;

    mutable std::mutex surface_mutex_;
    ANativeWindow* window_ = nullptr;

    std::mutex frame_mutex_;
    std::condition_variable frame_cv_;
    bool frame_dirty_ = false;
    std::vector<uint32_t> last_frame_;
    int last_frame_w_ = 0;
    int last_frame_h_ = 0;
    std::thread render_thread_;
    std::atomic<bool> render_running_{false};

    std::mutex lifecycle_mutex_;
    std::thread emulator_thread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> emu_should_run_{false};

    std::string runtime_root_;
    std::string config_path_;
    std::string sd_path_;
    std::string data_path_;
};
