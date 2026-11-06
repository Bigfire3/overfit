package app.overfit.platform

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service for the duration of a workout (type `specialUse`, see docs/DECISIONS.md
 * "Trainings-Benachrichtigung"). It does no work of its own: it holds the workout notification
 * of [AndroidRestAlerts], which makes it ongoing for real (not swipeable on the lock screen and
 * before Android 14, colorized), and keeps the process alive so the delete intent and the
 * notification actions are handled right away.
 *
 * Started when the workout notification is first posted (app in the foreground, a notification
 * action or the exact rest alarm, all allowed to start one); if Android refuses the start, the
 * same notification stays as a normal one. Not sticky: after a process kill the workout restores
 * it the next time the process starts (app, alarm, action).
 */
class TrainingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        starting = false
        val n = AndroidRestAlerts.notification
        if (n == null || AndroidRestAlerts.current == null) {
            // Workout ended while the start was on its way: Android still wants startForeground.
            runCatching { startForeground(placeholder()) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (runCatching { startForeground(n) }.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        starting = false
        super.onDestroy()
    }

    private fun startForeground(n: Notification) = ServiceCompat.startForeground(
        this,
        AndroidRestAlerts.ID_TRAINING,
        n,
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
    )

    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, AndroidRestAlerts.CHANNEL_WORKOUT_SILENT)
            .setSmallIcon(applicationInfo.icon)
            .setSilent(true)
            .build()

    companion object {
        @Volatile
        var running = false
            private set

        @Volatile
        var starting = false
            private set

        fun resetForTest() {
            running = false
            starting = false
        }

        /** Starts the service unless it runs or is starting. Refused (background start): stays a normal notification. */
        fun ensureRunning(context: Context) {
            if (running || starting) return
            starting = true
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TrainingService::class.java)) }
                .onFailure { starting = false }
        }

        /** Workout over. While starting, [onStartCommand] sees the ended workout and stops itself. */
        fun stop(context: Context) {
            if (running) runCatching { context.stopService(Intent(context, TrainingService::class.java)) }
            running = false
        }
    }
}
