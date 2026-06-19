package online.fujinet.go.apple2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import online.fujinet.go.apple2.core.EmulatorNative
import kotlin.concurrent.thread

/**
 * Streams AppleWin's libretro audio to an AudioTrack. The libretro core pushes
 * interleaved stereo 44100 Hz samples into a native ring buffer each frame; a
 * feeder thread drains it via [EmulatorNative.nativeRenderAudio] and writes the
 * samples out.
 */
class AudioOutput {
    private companion object {
        const val SAMPLE_RATE = 44100
        const val BLOCK_FRAMES = 1024            // stereo frames per pull
        const val BLOCK_SAMPLES = BLOCK_FRAMES * 2
        const val TAG = "FujiApple2Audio"
    }

    @Volatile private var running = false
    private var feeder: Thread? = null
    private var track: AudioTrack? = null

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BLOCK_SAMPLES * 2 * 4)

        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        ).also { it.play() }

        running = true
        feeder = thread(name = "apple2-audio") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ShortArray(BLOCK_SAMPLES)
            while (running) {
                try {
                    val n = EmulatorNative.nativeRenderAudio(buffer)
                    if (n > 0) {
                        track?.write(buffer, 0, n, AudioTrack.WRITE_BLOCKING)
                    } else {
                        // Nothing queued yet; avoid a busy spin.
                        Thread.sleep(2)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "audio feeder error", t)
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        feeder?.join(500)
        feeder = null
        track?.run { stop(); release() }
        track = null
    }
}
