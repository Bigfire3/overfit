package app.overfit.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.overfit.ui.AppDependencies
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Rest timer on Android (see docs/DECISIONS.md, phase 2):
 * - While the rest runs: a silent, ongoing notification with a system-drawn countdown
 *   (chronometer) and the actions +15 s / Skip. The countdown needs no wakeups.
 * - At the end: an alarm via [AlarmManager.setExactAndAllowWhileIdle] (exact when the user allows
 *   exact alarms, otherwise the closest inexact variant) wakes [RestAlarmReceiver], which posts
 *   the "rest is over" notification – unless the app is visible, then the app itself vibrates.
 * No foreground service and no wakelock.
 *
 * [iconRes] is a monochrome drawable from the app module.
 */
class AndroidRestAlerts(
    context: Context,
    val iconRes: Int,
) : RestAlerts {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    /** Set by the activity: shows the system dialog for the notification permission. */
    var permissionRequester: ((String) -> Unit)? = null

    override fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionRequester?.invoke(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun schedule(alert: RestAlert) {
        ensureChannels(context, alert.channelRunning, alert.channelOver)
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ID_OVER)

        val end = endIntent(context, alert)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.endsAt, end)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.endsAt, end)
        }

        val running = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(iconRes)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(alert.endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((alert.endsAt - System.currentTimeMillis()).coerceAtLeast(1_000))
            .setContentIntent(openApp(context))
            .addAction(0, alert.plusLabel, action(context, ACTION_PLUS))
            .addAction(0, alert.skipLabel, action(context, ACTION_SKIP))
            .build()
        notify(context, ID_RUNNING, running)
    }

    override fun cancel() {
        alarms.cancel(endIntent(context, null))
        NotificationManagerCompat.from(context).run {
            cancel(ID_RUNNING)
            cancel(ID_OVER)
        }
    }

    override fun exactAlarmsAllowed(): Boolean? =
        if (Build.VERSION.SDK_INT >= 31) alarms.canScheduleExactAlarms() else null

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    internal companion object {
        const val CHANNEL_RUNNING = "rest_running"
        const val CHANNEL_OVER = "rest_over"
        const val CHANNEL_OVER_SILENT = "rest_over_silent"
        const val ID_RUNNING = 1001
        const val ID_OVER = 1002
        const val ACTION_PLUS = "app.overfit.REST_PLUS"
        const val ACTION_SKIP = "app.overfit.REST_SKIP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_ICON = "icon"
        private const val REQUEST_END = 1
        private const val REQUEST_PLUS = 2
        private const val REQUEST_SKIP = 3
        private const val REQUEST_OPEN = 4

        fun ensureChannels(context: Context, runningName: String, overName: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, runningName, NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
            // Channel settings cannot change later, so "vibration off" gets its own channel.
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_OVER, overName, NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = AndroidHaptics.REST_END_TIMINGS
                    setSound(null, null)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_OVER_SILENT, overName, NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }

        /** The alarm; [alert] = null only to cancel (extras do not matter for matching). */
        private fun endIntent(context: Context, alert: RestAlert?): PendingIntent {
            val intent = Intent(context, RestAlarmReceiver::class.java)
            if (alert != null) {
                intent.putExtra(EXTRA_TITLE, alert.overTitle)
                intent.putExtra(EXTRA_BODY, alert.body)
                intent.putExtra(EXTRA_VIBRATE, alert.vibrate)
            }
            return PendingIntent.getBroadcast(
                context, REQUEST_END, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            if (action == ACTION_PLUS) REQUEST_PLUS else REQUEST_SKIP,
            Intent(context, RestActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun openApp(context: Context): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return null
            return PendingIntent.getActivity(context, REQUEST_OPEN, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        @SuppressLint("MissingPermission") // checked via areNotificationsEnabled
        fun notify(context: Context, id: Int, notification: android.app.Notification) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            runCatching { manager.notify(id, notification) }
        }
    }
}

/** Whether an activity of the app is in the foreground (then the app signals the rest end itself). */
object AppVisibility {
    @Volatile
    var visible: Boolean = false
}

/** Implemented by the Application so receivers reach the running workout. */
interface HasAppDependencies {
    val dependencies: AppDependencies
}

/** End of a rest: replaces the countdown with the "rest is over" notification. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(AndroidRestAlerts.ID_RUNNING)
        if (AppVisibility.visible) return
        val vibrate = intent.getBooleanExtra(AndroidRestAlerts.EXTRA_VIBRATE, true)
        val smallIcon = ((context.applicationContext as? HasAppDependencies)?.dependencies?.restAlerts as? AndroidRestAlerts)
            ?.iconRes ?: context.applicationInfo.icon
        val notification = NotificationCompat.Builder(
            context,
            if (vibrate) AndroidRestAlerts.CHANNEL_OVER else AndroidRestAlerts.CHANNEL_OVER_SILENT,
        )
            .setSmallIcon(smallIcon)
            .setContentTitle(intent.getStringExtra(AndroidRestAlerts.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(AndroidRestAlerts.EXTRA_BODY))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(AndroidRestAlerts.openApp(context))
            .apply { if (vibrate) setVibrate(AndroidHaptics.REST_END_TIMINGS) }
            .build()
        AndroidRestAlerts.notify(context, AndroidRestAlerts.ID_OVER, notification)
    }
}

/** +15 s / Skip from the countdown notification. Works even if the process had been killed. */
class RestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = (context.applicationContext as? HasAppDependencies)?.dependencies?.workout ?: return
        val pending = goAsync()
        MainScope().launch {
            try {
                controller.awaitLoaded()
                when (intent.action) {
                    AndroidRestAlerts.ACTION_PLUS -> controller.adjustRest(15)
                    AndroidRestAlerts.ACTION_SKIP -> controller.skipRest()
                }
                controller.flush()
            } finally {
                pending.finish()
            }
        }
    }
}
