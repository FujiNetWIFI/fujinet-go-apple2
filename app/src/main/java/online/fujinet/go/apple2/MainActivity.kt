package online.fujinet.go.apple2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import online.fujinet.go.apple2.fujinet.FujiNetWebViewActivity
import online.fujinet.go.apple2.ui.EmulatorScreen
import online.fujinet.go.apple2.ui.theme.FujiNetGoApple2Theme

/**
 * FujiNet Go Apple2 main screen: the Apple II display plus the on-screen
 * keyboard and a control bar. The native layer (AppleWin libretro core + the
 * in-process FujiNet runtime over SmartPort-over-SLIP) is owned by
 * [EmulatorSessionService] (a foreground service) so it keeps running across
 * activity changes (e.g. the FujiNet web admin) and while backgrounded. The
 * session itself is a process singleton; the Power button stops both.
 */
class MainActivity : ComponentActivity() {

    private lateinit var session: SessionController

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == EmulatorSessionService.ACTION_SHUTDOWN) {
            shutdown()
            return
        }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Hold clocks steady over a long emulation session (thermals permitting)
        // rather than letting DVFS oscillate the 60Hz loop off schedule.
        window.setSustainedPerformanceMode(true)
        session = SessionController.get(applicationContext)

        maybeRequestNotificationPermission()
        EmulatorSessionService.start(this)

        setContent {
            FujiNetGoApple2Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    EmulatorScreen(
                        session = session,
                        onOpenFujiNet = ::openFujiNet,
                        onShutdown = ::shutdown,
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == EmulatorSessionService.ACTION_SHUTDOWN) {
            shutdown()
        }
    }

    private fun openFujiNet() {
        startActivity(Intent(this, FujiNetWebViewActivity::class.java))
    }

    /** Stop the emulator + FujiNet and close the app. */
    private fun shutdown() {
        EmulatorSessionService.shutdown(this)
        finishAndRemoveTask()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // No session.stop() here: the foreground service owns the session's lifetime
    // so it survives this activity being finished. Stopping is explicit, via the
    // Power button -> shutdown().
}
