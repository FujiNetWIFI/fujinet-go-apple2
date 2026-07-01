package online.fujinet.go.apple2.ui

import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import online.fujinet.go.apple2.SessionController

// AppleWin's libretro borderless frame buffer (560x384) presented by the host
// (session_runtime.cpp::PresentTo) -- the Apple II's ~4:3 display.
private const val FRAME_RATIO = 560f / 384f

/**
 * Hosts the Apple II video output. The native layer renders AppleWin's XRGB8888
 * frames into a [Surface] (session_runtime.cpp::OnFrame); the surface is obtained
 * from a [TextureView]'s [SurfaceTexture].
 *
 * We use a TextureView, not a SurfaceView, on purpose. A SurfaceView lives in its
 * own compositor layer outside the view hierarchy, and on Android 11 / API 30 that
 * layer was being composited over the whole top of the window -- hiding the
 * FunctionBar toolbar (and even the system status bar) behind a black band, while
 * the controls below it drew fine. A TextureView draws inline as an ordinary view,
 * so it can never occlude siblings above or below it. The native ANativeWindow blit
 * path is unchanged: ANativeWindow_fromSurface() works with a SurfaceTexture-backed
 * Surface just as it did with the SurfaceView's holder surface.
 *
 * The view is sized to the emulator's aspect ratio and centered on black, so the
 * frame is letter-/pillar-boxed (never stretched).
 */
@Composable
fun EmulatorSurface(
    session: SessionController,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val pillarboxed = maxWidth / maxHeight > FRAME_RATIO
        val surfaceModifier = if (pillarboxed) {
            Modifier.fillMaxHeight().aspectRatio(FRAME_RATIO)
        } else {
            Modifier.fillMaxWidth().aspectRatio(FRAME_RATIO)
        }

        // Landscape / TV: the frame is pillar-boxed, leaving black bars either side.
        // Fill them with a fade from a dim tint of the UI accent at the picture edge
        // out to black at the screen edge -- a subtle ambient glow, not a vivid band.
        // Drawn before the surface so the picture always wins any sub-pixel overlap.
        if (pillarboxed) {
            val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            val barWidth = (maxWidth - maxHeight * FRAME_RATIO) / 2
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(barWidth)
                    .background(Brush.horizontalGradient(listOf(Color.Black, accent))),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(barWidth)
                    .background(Brush.horizontalGradient(listOf(accent, Color.Black))),
            )
        }

        AndroidView(
            modifier = surfaceModifier,
            factory = { context ->
                TextureView(context).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null

                        override fun onSurfaceTextureAvailable(
                            texture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            val s = Surface(texture)
                            surface = s
                            s.requestFrameRate60()
                            session.attachSurface(s)
                            session.startIfNeeded()
                        }

                        override fun onSurfaceTextureSizeChanged(
                            texture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            surface?.requestFrameRate60()
                        }

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            session.detachSurface()
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
        )
    }
}

// Tell the compositor this surface produces a fixed 60 fps. On a 120Hz /
// variable-refresh phone this makes the panel present at 60 (or a 60 multiple)
// instead of judder-mapping 60 fps content onto e.g. 90/120Hz.
private fun Surface.requestFrameRate60() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isValid) {
        setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
    }
}
