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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
 * - Ticks (setting "Countdown ticks"): a second exact alarm at end − 3 s wakes [RestTickReceiver],
 *   which plays one short sound with all three ticks on the media stream and ducks other audio
 *   for that moment. It plays in the foreground too, so the app never plays them a second time.
 * No foreground service and no wakelock of our own (the alarm's wakelock covers the 2 s sound).
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

        setAlarm(alert.endsAt, endIntent(context, alert))
        // ±15 s while the ticks play: they no longer fit the new end.
        if (RestTickPlayer.playingFor.let { it != null && it != alert.endsAt }) RestTickPlayer.stop()
        val ticksAt = RestTicks.startAt(alert, System.currentTimeMillis())
        if (ticksAt != null) setAlarm(ticksAt, ticksIntent(context, ticksAt)) else alarms.cancel(ticksIntent(context, null))

        postRunning(context, runningExtras(alert, iconRes))
    }

    private fun setAlarm(at: Long, intent: PendingIntent) {
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override fun cancel() {
        alarms.cancel(endIntent(context, null))
        alarms.cancel(ticksIntent(context, null))
        RestTickPlayer.stop()
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
        const val EXTRA_TICKS_AT = "ticksAt"
        private const val REQUEST_END = 1
        private const val REQUEST_PLUS = 2
        private const val REQUEST_SKIP = 3
        private const val REQUEST_OPEN = 4
        private const val REQUEST_TICKS = 5

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

        private const val EXTRA_RUN_TITLE = "runTitle"
        private const val EXTRA_RUN_BODY = "runBody"
        private const val EXTRA_ENDS_AT = "endsAt"
        private const val EXTRA_PLUS = "plus"
        private const val EXTRA_SKIP = "skip"
        private const val REQUEST_DISMISSED = 6
        /** A swipe this close to the end is left alone (the rest is over anyway). */
        private const val REPOST_MIN_LEFT_MS = 1_000L

        /** Everything needed to (re)build the countdown notification, also after a process kill. */
        fun runningExtras(alert: RestAlert, iconRes: Int) = android.os.Bundle().apply {
            putString(EXTRA_RUN_TITLE, alert.title)
            putString(EXTRA_RUN_BODY, alert.body)
            putLong(EXTRA_ENDS_AT, alert.endsAt)
            putString(EXTRA_PLUS, alert.plusLabel)
            putString(EXTRA_SKIP, alert.skipLabel)
            putInt(EXTRA_ICON, iconRes)
        }

        /**
         * Posts the countdown. Ongoing, and since Android 14 lets users swipe even ongoing
         * notifications away, its delete intent ([RestDismissedReceiver]) posts it again while
         * the rest runs. Programmatic cancels (skip, end, finish) and the timeout at the end do
         * not repost (checked via the end time).
         */
        fun postRunning(context: Context, extras: android.os.Bundle) {
            val endsAt = extras.getLong(EXTRA_ENDS_AT)
            val dismissed = PendingIntent.getBroadcast(
                context,
                REQUEST_DISMISSED,
                Intent(context, RestDismissedReceiver::class.java).putExtras(extras),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val running = NotificationCompat.Builder(context, CHANNEL_RUNNING)
                .setSmallIcon(extras.getInt(EXTRA_ICON).takeIf { it != 0 } ?: context.applicationInfo.icon)
                .setContentTitle(extras.getString(EXTRA_RUN_TITLE))
                .setContentText(extras.getString(EXTRA_RUN_BODY))
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(endsAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter((endsAt - System.currentTimeMillis()).coerceAtLeast(1_000))
                .setContentIntent(openApp(context))
                .setDeleteIntent(dismissed)
                .addAction(0, extras.getString(EXTRA_PLUS), action(context, ACTION_PLUS))
                .addAction(0, extras.getString(EXTRA_SKIP), action(context, ACTION_SKIP))
                .build()
            notify(context, ID_RUNNING, running)
        }

        /** Swiped away: back while the rest still runs. */
        fun onRunningDismissed(context: Context, extras: android.os.Bundle?) {
            val endsAt = extras?.getLong(EXTRA_ENDS_AT) ?: return
            if (endsAt - System.currentTimeMillis() < REPOST_MIN_LEFT_MS) return
            postRunning(context, extras)
        }

        /** The tick alarm; [at] = null only to cancel. */
        internal fun ticksIntent(context: Context, at: Long?): PendingIntent {
            val intent = Intent(context, RestTickReceiver::class.java)
            if (at != null) intent.putExtra(EXTRA_TICKS_AT, at)
            return PendingIntent.getBroadcast(
                context, REQUEST_TICKS, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?.putExtra(EXTRA_OPEN_WORKOUT, true) ?: return null
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

/** Delete intent of the countdown notification (user swiped it away), see [AndroidRestAlerts.postRunning]. */
class RestDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AndroidRestAlerts.onRunningDismissed(context.applicationContext, intent.extras)
    }
}

/**
 * The ticks before a rest ends. Skipped when the alarm comes late (no exact-alarm permission,
 * the ticks would no longer match the countdown). Keeps the broadcast open while the sound plays,
 * so the alarm's wakelock keeps the CPU awake for these 2 seconds with the screen off.
 */
class RestTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getLongExtra(AndroidRestAlerts.EXTRA_TICKS_AT, 0L)
        if (!RestTicks.onTime(at, System.currentTimeMillis())) return
        val pending = goAsync()
        RestTickPlayer.play(context.applicationContext, at + RestTicks.LEAD_MS) { pending.finish() }
    }
}

/**
 * Plays [RestTicks.pcm] once. Media stream (audible in silent mode, follows the media volume like
 * the music), usage "navigation guidance" so other audio is ducked for a moment instead of paused
 * (transient focus, may duck). Main thread only.
 */
internal object RestTickPlayer {
    private val pcm by lazy { RestTicks.pcm() }
    private var track: AudioTrack? = null
    private var finish: (() -> Unit)? = null
    /** End of the rest whose ticks are playing, null when silent. */
    var playingFor: Long? = null
        private set
    private var focus: AudioFocusRequest? = null
    private var audio: AudioManager? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val done = Runnable { stop() }

    fun play(context: Context, restEndsAt: Long, onDone: () -> Unit) {
        stop()
        finish = onDone
        playingFor = restEndsAt
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            audio = am
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
            focus = request
            am.requestAudioFocus(request)
            val t = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RestTicks.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track = t
            t.write(pcm, 0, pcm.size)
            t.play()
        }.onFailure { stop(); return }
        handler.postDelayed(done, RestTicks.durationMs + 150)
    }

    /** Stops a playing tick sound (rest skipped or changed) and gives the audio focus back. */
    fun stop() {
        handler.removeCallbacks(done)
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        focus?.let { f -> audio?.abandonAudioFocusRequest(f) }
        focus = null
        playingFor = null
        finish?.invoke()
        finish = null
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

/** Set on the notification tap intents: MainActivity then shows the maximized workout. */
const val EXTRA_OPEN_WORKOUT = "app.overfit.OPEN_WORKOUT"
