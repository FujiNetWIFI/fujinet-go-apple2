package online.fujinet.go.apple2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps the Apple II emulator and the in-process FujiNet runtime alive as a
 * foreground service, so they keep running when the user opens the FujiNet web
 * admin or backgrounds the app. The ongoing notification carries a Shutdown
 * action that routes through [MainActivity] so the UI is torn down too.
 */
class EmulatorSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), foregroundType())
        SessionController.get(applicationContext).startIfNeeded()
    }

    private fun foregroundType(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHUTDOWN) {
            SessionController.get(applicationContext).stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            // The AppleWin core keeps the emulated machine (RAM/CPU) and other
            // libretro state in C++ globals that retro_deinit/retro_init don't
            // reset, so it can't be re-initialised cleanly in this process: a
            // relaunch would resume the old machine (stale screen, no cold boot)
            // and could key through a stale core callback (crash). "Power off"
            // therefore ends the process so the next launch cold-boots into
            // FujiNet CONFIG in a pristine process. Killing our own pid is safe
            // here: stopSelf() above + START_NOT_STICKY mean the system won't
            // resurrect the (now stopped) foreground service.
            android.os.Process.killProcess(android.os.Process.myPid())
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val shutdown = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_SHUTDOWN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FujiNet Go Apple2")
            .setContentText("Apple II emulator and FujiNet are running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Shutdown", shutdown)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Emulator runtime",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    companion object {
        const val ACTION_SHUTDOWN = "online.fujinet.go.apple2.action.SHUTDOWN"
        private const val CHANNEL_ID = "emulator_runtime"
        private const val NOTIF_ID = 1001

        fun start(context: android.content.Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, EmulatorSessionService::class.java),
            )
        }

        fun shutdown(context: android.content.Context) {
            context.startService(
                Intent(context, EmulatorSessionService::class.java).setAction(ACTION_SHUTDOWN),
            )
        }
    }
}
