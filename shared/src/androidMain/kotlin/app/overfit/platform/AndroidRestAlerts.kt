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
 * Workout notification and rest timer on Android (see docs/DECISIONS.md, phase 2 and
 * "Trainings-Benachrichtigung"):
 * - During the whole workout one ongoing notification, held by [TrainingService] (foreground
 *   service): green with the set to do now and "Set done", red during a rest with a system-drawn
 *   countdown (chronometer) and +15 s / Skip. The countdown needs no wakeups. Swiped away
 *   (possible since Android 14), its delete intent posts it again right away.
 * - Transitions set → rest and rest → set announce themselves once (heads-up, vibration by
 *   setting), all other updates are silent. Not while the app is visible (it vibrates itself).
 * - At the end of a rest: an alarm via [AlarmManager.setExactAndAllowWhileIdle] (exact when the
 *   user allows exact alarms, otherwise the closest inexact variant) wakes [RestAlarmReceiver],
 *   which switches the notification to the next set.
 * - Ticks (setting "Countdown ticks"): a second exact alarm at end − 3 s wakes [RestTickReceiver],
 *   which plays one short sound with all three ticks on the media stream and ducks other audio
 *   for that moment. It plays in the foreground too, so the app never plays them a second time.
 * No wakelock of our own (the alarm's wakelock covers the 2 s sound).
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
        setAlarm(alert.endsAt, endIntent(context, alert))
        // ±15 s while the ticks play: they no longer fit the new end.
        if (RestTickPlayer.playingFor.let { it != null && it != alert.endsAt }) RestTickPlayer.stop()
        val ticksAt = RestTicks.startAt(alert, System.currentTimeMillis())
        if (ticksAt != null) setAlarm(ticksAt, ticksIntent(context, ticksAt)) else alarms.cancel(ticksIntent(context, null))

        post(context, Shown(iconRes, rest = alert), announce = alert.announce)
    }

    override fun showWorkout(notice: WorkoutNotice, announce: Boolean) {
        post(context, Shown(iconRes, notice = notice), announce)
    }

    override val workoutNotification: Boolean get() = true

    override fun endWorkout() {
        cancel()
        end(context)
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
    }

    override suspend fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** When the dialog was last asked for from the settings (see [onPermissionResult]). */
    private var askedAt = 0L

    override fun allowNotifications() {
        val requester = permissionRequester
        if (Build.VERSION.SDK_INT >= 33 && requester != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedAt = android.os.SystemClock.elapsedRealtime()
            requester(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    /**
     * Result of the permission dialog. Denied twice before, Android shows no dialog and answers
     * "denied" right away: then the settings open instead, so "Allow" always leads somewhere.
     */
    fun onPermissionResult(granted: Boolean) {
        val asked = askedAt
        askedAt = 0L
        if (!granted && asked != 0L && android.os.SystemClock.elapsedRealtime() - asked < NO_DIALOG_MS) openNotificationSettings()
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(details) }
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

    /** What the workout notification shows: a rest ([rest]) or a set ([notice]). */
    data class Shown(val icon: Int, val rest: RestAlert? = null, val notice: WorkoutNotice? = null)

    companion object {
        const val CHANNEL_WORKOUT = "workout"
        const val CHANNEL_WORKOUT_SILENT = "workout_silent"
        /** Channels before the workout notification (one for the countdown, two for the end). */
        private val OLD_CHANNELS = listOf("rest_running", "rest_over", "rest_over_silent")
        /** A "denied" faster than this came without a dialog. */
        private const val NO_DIALOG_MS = 400L
        const val ID_TRAINING = 1001
        const val ACTION_PLUS = "app.overfit.REST_PLUS"
        const val ACTION_SKIP = "app.overfit.REST_SKIP"
        const val ACTION_DONE = "app.overfit.SET_DONE"
        const val EXTRA_ICON = "icon"
        const val EXTRA_TICKS_AT = "ticksAt"
        const val EXTRA_ENDS_AT = "endsAt"
        private const val REQUEST_END = 1
        private const val REQUEST_PLUS = 2
        private const val REQUEST_SKIP = 3
        private const val REQUEST_OPEN = 4
        private const val REQUEST_TICKS = 5
        private const val REQUEST_DISMISSED = 6
        private const val REQUEST_DONE = 7
        /** A rest this close to its end is not shown again after a swipe; the next set is. */
        private const val REPOST_MIN_LEFT_MS = 1_000L
        private const val GROUP = "app.overfit.workout"

        /** Rest red and set green (filled background: colorized foreground-service notification). */
        const val COLOR_REST = 0xFFD32F2F.toInt() // = Colors.armedFill
        const val COLOR_SET = 0xFF2E7D32.toInt()

        /** What the notification shows now; null without a workout (or not known yet in this process). */
        @Volatile
        var current: Shown? = null
            private set

        /** Whether this process has shown or ended the workout notification (else [current] says nothing). */
        @Volatile
        var known = false
            private set

        /** The notification last built, for [TrainingService.startForeground]. */
        @Volatile
        var notification: android.app.Notification? = null
            private set

        /** Tests: forget the state of earlier tests in the same process. */
        fun resetForTest() {
            current = null
            known = false
            notification = null
            channelNames = null
            TrainingService.resetForTest()
        }

        /** Channel names created in this process (language switch: names are updated). */
        private var channelNames: Pair<String, String>? = null

        fun ensureChannels(context: Context, name: String, silentName: String) {
            if (channelNames == name to silentName) return
            channelNames = name to silentName
            val nm = context.getSystemService(NotificationManager::class.java)
            OLD_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
            // High importance so transitions can pop up; quiet updates are silenced per post. No
            // sound (the ticks and the music stay in charge). Channel settings cannot change
            // later, so "vibration off" gets its own channel.
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_WORKOUT, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    setShowBadge(false)
                    enableVibration(true)
                    vibrationPattern = AndroidHaptics.REST_END_TIMINGS
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_WORKOUT_SILENT, silentName, NotificationManager.IMPORTANCE_HIGH).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
        }

        /**
         * Shows [shown] in the workout notification and makes sure [TrainingService] holds it.
         * [announce] alerts once (heads-up, vibration by channel) unless the app is visible.
         */
        fun post(context: Context, shown: Shown, announce: Boolean) {
            current = shown
            known = true
            val alert = announce && !AppVisibility.visible
            val n = build(context, shown, alert)
            // The service re-posts it with startForeground: silently, or a transition alerts twice.
            notification = if (alert) build(context, shown, alert = false) else n
            notify(context, ID_TRAINING, n)
            // Notifications blocked: no service either (it would run without anything to show).
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) TrainingService.ensureRunning(context)
        }

        /** Workout over: the service stops and the notification goes. */
        fun end(context: Context) {
            current = null
            known = true
            notification = null
            TrainingService.stop(context)
            TrainingService.resetFailure()
            NotificationManagerCompat.from(context).cancel(ID_TRAINING)
        }

        private fun build(context: Context, shown: Shown, alert: Boolean): android.app.Notification {
            val rest = shown.rest
            val notice = shown.notice
            val vibrate = rest?.vibrate ?: notice?.vibrate ?: true
            ensureChannels(
                context,
                rest?.channel ?: notice?.channel.orEmpty(),
                rest?.channelSilent ?: notice?.channelSilent.orEmpty(),
            )
            val dismissed = PendingIntent.getBroadcast(
                context,
                REQUEST_DISMISSED,
                Intent(context, RestDismissedReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val b = NotificationCompat.Builder(context, if (vibrate) CHANNEL_WORKOUT else CHANNEL_WORKOUT_SILENT)
                .setSmallIcon(shown.icon.takeIf { it != 0 } ?: context.applicationInfo.icon)
                .setOngoing(true)
                .setColorized(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setContentIntent(openApp(context))
                .setDeleteIntent(dismissed)
                // Quiet updates: in a group without summary, GROUP_ALERT_SUMMARY silences this
                // notification (no heads-up, no vibration). Transitions alert again.
                .setGroup(GROUP)
                .setGroupAlertBehavior(if (alert) NotificationCompat.GROUP_ALERT_ALL else NotificationCompat.GROUP_ALERT_SUMMARY)
                .setOnlyAlertOnce(!alert)
            if (alert && vibrate) b.setVibrate(AndroidHaptics.REST_END_TIMINGS)
            if (rest != null) {
                b.setColor(COLOR_REST)
                    .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                    .setContentTitle(rest.title)
                    .setContentText(rest.body)
                    .setWhen(rest.endsAt)
                    .setChronometerCountDown(true)
                    .addAction(0, rest.plusLabel, action(context, ACTION_PLUS))
                    .addAction(0, rest.skipLabel, action(context, ACTION_SKIP))
            } else if (notice != null) {
                b.setColor(COLOR_SET)
                    .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                    .setContentTitle(notice.title)
                    .setContentText(notice.body)
                    .setWhen(notice.startedAt)
                    .setChronometerCountDown(false)
                notice.note?.let { b.setSubText(it) }
                notice.doneLabel?.let { b.addAction(0, it, action(context, ACTION_DONE)) }
            }
            return b.build()
        }

        /** The alarm; [alert] = null only to cancel (extras do not matter for matching). */
        private fun endIntent(context: Context, alert: RestAlert?): PendingIntent {
            val intent = Intent(context, RestAlarmReceiver::class.java)
            if (alert != null) {
                intent.putExtra(EXTRA_ENDS_AT, alert.endsAt)
                alert.after?.let { intent.putExtras(it.toBundle()) }
            }
            return PendingIntent.getBroadcast(
                context, REQUEST_END, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * The rest whose alarm fired is over: switch to the next set and announce it. A stale
         * alarm (workout ended, a newer rest) changes nothing. Did the app already switch to the
         * set (rest over in the running process, values changed since), its notice is the fresh
         * one. In a fresh process (killed during the rest) the notice comes from the alarm's extras.
         */
        fun onRestEnded(context: Context, endsAt: Long, after: WorkoutNotice?, icon: Int) {
            val shown = current
            if (known && (shown == null || (shown.rest != null && shown.rest.endsAt != endsAt))) return
            val notice = shown?.notice?.let { it.copy(note = after?.note ?: it.note) } ?: after ?: shown?.rest?.after ?: return
            post(context, Shown(shown?.icon ?: icon, notice = notice), announce = true)
        }

        /**
         * Swiped away: shown again right away while the workout runs. A rest about to end comes
         * back as the next set. In a fresh process the workout itself says what to show.
         */
        fun onDismissed(context: Context, fromWorkout: () -> Unit) {
            if (!known) return fromWorkout()
            val shown = current ?: return
            val rest = shown.rest
            if (rest != null && rest.endsAt - System.currentTimeMillis() < REPOST_MIN_LEFT_MS) {
                rest.after?.let { post(context, Shown(shown.icon, notice = it), announce = false) }
                return
            }
            post(context, shown, announce = false)
        }

        /** The tick alarm; [at] = null only to cancel. */
        fun ticksIntent(context: Context, at: Long?): PendingIntent {
            val intent = Intent(context, RestTickReceiver::class.java)
            if (at != null) intent.putExtra(EXTRA_TICKS_AT, at)
            return PendingIntent.getBroadcast(
                context, REQUEST_TICKS, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            when (action) {
                ACTION_PLUS -> REQUEST_PLUS
                ACTION_SKIP -> REQUEST_SKIP
                else -> REQUEST_DONE
            },
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

private const val N_TITLE = "notice.title"
private const val N_BODY = "notice.body"
private const val N_STARTED = "notice.startedAt"
private const val N_DONE = "notice.done"
private const val N_VIBRATE = "notice.vibrate"
private const val N_CHANNEL = "notice.channel"
private const val N_CHANNEL_SILENT = "notice.channelSilent"
private const val N_NOTE = "notice.note"

/** For alarm extras: the notice survives a process kill. */
internal fun WorkoutNotice.toBundle() = android.os.Bundle().apply {
    putString(N_TITLE, title)
    putString(N_BODY, body)
    putLong(N_STARTED, startedAt)
    putString(N_DONE, doneLabel)
    putBoolean(N_VIBRATE, vibrate)
    putString(N_CHANNEL, channel)
    putString(N_CHANNEL_SILENT, channelSilent)
    putString(N_NOTE, note)
}

internal fun workoutNotice(extras: android.os.Bundle?): WorkoutNotice? {
    val title = extras?.getString(N_TITLE) ?: return null
    return WorkoutNotice(
        title = title,
        body = extras.getString(N_BODY).orEmpty(),
        startedAt = extras.getLong(N_STARTED),
        doneLabel = extras.getString(N_DONE),
        vibrate = extras.getBoolean(N_VIBRATE, true),
        channel = extras.getString(N_CHANNEL).orEmpty(),
        channelSilent = extras.getString(N_CHANNEL_SILENT).orEmpty(),
        note = extras.getString(N_NOTE),
    )
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

/** End of a rest: the workout notification switches to the next set and announces it. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val icon = ((context.applicationContext as? HasAppDependencies)?.dependencies?.restAlerts as? AndroidRestAlerts)
            ?.iconRes ?: context.applicationInfo.icon
        AndroidRestAlerts.onRestEnded(
            context.applicationContext,
            intent.getLongExtra(AndroidRestAlerts.EXTRA_ENDS_AT, 0L),
            workoutNotice(intent.extras),
            icon,
        )
    }
}

/**
 * Delete intent of the workout notification (user swiped it away, possible since Android 14 even
 * for foreground-service notifications): posts it again, see [AndroidRestAlerts.onDismissed].
 */
class RestDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        AndroidRestAlerts.onDismissed(app) {
            // Fresh process: the stored workout decides (nothing if it has ended meanwhile).
            val controller = (app as? HasAppDependencies)?.dependencies?.workout ?: return@onDismissed
            val pending = goAsync()
            MainScope().launch {
                try {
                    controller.awaitLoaded()
                    controller.repostNotice()
                } finally {
                    pending.finish()
                }
            }
        }
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

/** +15 s / Skip / Set done from the workout notification. Works even if the process had been killed. */
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
                    AndroidRestAlerts.ACTION_DONE -> controller.logNextSet()
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
