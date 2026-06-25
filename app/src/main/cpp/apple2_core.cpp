#include <jni.h>

#include <string>

#include "apple2_host.h"
#include "session_runtime.h"

namespace {
std::string JStr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeStartSession(
        JNIEnv* env, jobject /*thiz*/,
        jstring runtimeRoot, jstring configPath, jstring sdPath, jstring dataPath) {
    SessionRuntime::Get().StartSession(
            JStr(env, runtimeRoot), JStr(env, configPath),
            JStr(env, sdPath), JStr(env, dataPath));
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeStopSession(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    SessionRuntime::Get().StopSession();
}

JNIEXPORT jboolean JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeIsRunning(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return SessionRuntime::Get().IsRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeAttachSurface(
        JNIEnv* env, jobject /*thiz*/, jobject surface) {
    SessionRuntime::Get().AttachSurface(env, surface);
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeDetachSurface(
        JNIEnv* env, jobject /*thiz*/) {
    SessionRuntime::Get().DetachSurface(env);
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeRequestReset(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean cold) {
    SessionRuntime::Get().RequestReset(cold == JNI_TRUE);
}

// Sets a libretro core-option override (e.g. "applewin_machine",
// "applewin_slot7") before the session starts; the core reads it at load.
JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeSetCoreOption(
        JNIEnv* env, jobject /*thiz*/, jstring key, jstring value) {
    apple2host_set_variable(JStr(env, key).c_str(), JStr(env, value).c_str());
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeInjectKey(
        JNIEnv* /*env*/, jobject /*thiz*/,
        jboolean down, jint keycode, jint character, jint mods) {
    apple2host_inject_key(down ? 1 : 0, static_cast<unsigned>(keycode),
                          static_cast<uint32_t>(character),
                          static_cast<uint16_t>(mods));
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeSetJoystickButton(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint id, jboolean pressed) {
    apple2host_set_joystick_button(port, id, pressed ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeSetJoystickAxis(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint axis, jint value) {
    apple2host_set_joystick_axis(port, axis, static_cast<int16_t>(value));
}

// Blocks until out.length interleaved stereo signed-16 samples (44100 Hz) are
// ready, fills the whole block (silence-padding on underrun), returns the count.
JNIEXPORT jint JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeFillAudio(
        JNIEnv* env, jobject /*thiz*/, jshortArray out) {
    if (out == nullptr) return 0;
    const jsize n = env->GetArrayLength(out);
    if (n <= 0) return 0;
    jshort* buf = env->GetShortArrayElements(out, nullptr);
    if (buf == nullptr) return 0;
    const int written = apple2host_fill_audio(reinterpret_cast<int16_t*>(buf),
                                              static_cast<int>(n));
    env->ReleaseShortArrayElements(out, buf, 0);
    return written;
}

JNIEXPORT void JNICALL
Java_online_fujinet_go_apple2_core_EmulatorNative_nativeAudioSetActive(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean active) {
    apple2host_audio_set_active(active ? 1 : 0);
}

}  // extern "C"
