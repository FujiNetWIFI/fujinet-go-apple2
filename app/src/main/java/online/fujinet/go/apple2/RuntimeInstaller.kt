package online.fujinet.go.apple2

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * Stages the bundled FujiNet runtime tree from APK assets into a writable
 * runtime directory the native FujiNet layer can chdir into and mutate.
 *
 * Assets are produced by tools/fujinet/build-fujinet.sh:
 *   assets/fujinet/{fnconfig.ini, data/, SD/}
 *
 * Unlike fujinet-go-adam, no emulator ROMs are staged: the Apple II system ROMs
 * are compiled into libapple2core.so via AppleWin's embedded `apple2roms` target.
 */
class RuntimeInstaller(private val context: Context) {

    data class Paths(
        val runtimeRoot: String,
        val configPath: String,
        val sdPath: String,
        val dataPath: String,
    )

    fun install(force: Boolean = false): Paths {
        val root = File(context.filesDir, "fujinet")

        if (force || !File(root, "fnconfig.ini").exists()) {
            copyAssetDir("fujinet", root)
        }

        return Paths(
            runtimeRoot = root.absolutePath,
            configPath = File(root, "fnconfig.ini").absolutePath,
            sdPath = File(root, "SD").absolutePath,
            dataPath = File(root, "data").absolutePath,
        )
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val assets: AssetManager = context.assets
        val entries = assets.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            // It's a file, not a directory.
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (entry in entries) {
            copyAssetDir("$assetPath/$entry", File(dest, entry))
        }
    }
}
