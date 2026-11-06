package app.overfit.platform

/**
 * Everything the platform needs to show a running rest and to alert at its end, already in the
 * user's language. Times are epoch milliseconds.
 */
data class RestAlert(
    val startedAt: Long,
    val endsAt: Long,
    /** "Rest" – title of the running countdown. */
    val title: String,
    /** "Rest is over" – title of the alert at the end. */
    val overTitle: String,
    /** "Next: Lateral raise · Set 3 · 30 kg × 6" or a generic line. */
    val body: String,
    val vibrate: Boolean,
    val plusLabel: String,
    val skipLabel: String,
    /** Name of the notification channel of the running workout (Android). */
    val channel: String,
    /** Same, without vibration (setting "Vibration" off). */
    val channelSilent: String,
    /** Ticks at 3, 2 and 1 s before [endsAt] (setting "Countdown ticks"). */
    val ticks: Boolean = false,
    /** "3 seconds left" – text of the tick notification where the platform needs one (iOS). */
    val ticksBody: String = "",
    /** The rest just started after a set (transition set → rest): announce it once (heads-up, vibration). */
    val announce: Boolean = false,
    /** What the workout notification shows when this rest is over (posted by the end alarm). */
    val after: WorkoutNotice? = null,
)

/**
 * The running workout outside a rest: the set to do now (Android: the ongoing, green workout
 * notification; during a rest it turns red and shows the [RestAlert]).
 */
data class WorkoutNotice(
    /** "Lateral raise", or the workout name when all sets are done. */
    val title: String,
    /** "Set 2/4 · 30 kg × 6" or "All sets done". */
    val body: String,
    /** Start of the workout (epoch ms): the notification counts the training time from here. */
    val startedAt: Long,
    /** Label of the "Set done" action; null when no set is open. */
    val doneLabel: String? = null,
    val vibrate: Boolean = true,
    val channel: String = "",
    val channelSilent: String = "",
    /** Small extra line, e.g. "Rest is over" when the end alarm switches to this notice. */
    val note: String? = null,
)

/**
 * System side of the rest timer, so the end is signalled while the app is in the background or
 * the screen is locked: Android posts a countdown notification and fires an exact alarm (no
 * wakelock), iOS schedules a local notification. Calling [schedule] again replaces the previous
 * alert; [cancel] removes everything (skip, workout finished or discarded).
 */
interface RestAlerts {
    /** Asks for the notification permission where needed (Android 13+, iOS). Non-blocking. */
    fun requestPermission()

    fun schedule(alert: RestAlert)

    /** Stops the rest alarms (rest skipped or ended early). The workout notification stays. */
    fun cancel()

    /**
     * The workout runs and no rest: shows [notice] (Android: replaces the rest countdown in the
     * same ongoing notification). [announce] = transition rest → set, alert once; otherwise a
     * silent update.
     */
    fun showWorkout(notice: WorkoutNotice, announce: Boolean = false) = Unit

    /** Workout finished or discarded: rest alarms and the workout notification go away. */
    fun endWorkout() = cancel()

    /** Whether alerts arrive to the second; null where the platform needs no extra permission. */
    fun exactAlarmsAllowed(): Boolean? = null

    /** Opens the system setting for exact alarms (Android 12+). */
    fun openExactAlarmSettings() = Unit

    /** Whether the app may post notifications; null where unknown (tests). */
    suspend fun notificationsAllowed(): Boolean? = null

    /**
     * "Allow" in the settings: the system dialog where it can still appear, otherwise the app's
     * notification settings (permission denied before).
     */
    fun allowNotifications() = Unit

    /**
     * Whether the app itself has to play the ticks right now. Android plays them from its tick
     * alarm (foreground and background alike, so they never play twice) and answers false; iOS
     * answers true while the app is active, because it does not present its tick notification then.
     */
    fun appPlaysTicks(): Boolean = false

    /** Plays the three ticks now (the sound starts with the first one). */
    fun playTicks() = Unit
}

object NoRestAlerts : RestAlerts {
    override fun requestPermission() = Unit
    override fun schedule(alert: RestAlert) = Unit
    override fun cancel() = Unit
}

/** Records calls (tests). */
class RecordingRestAlerts : RestAlerts {
    var permissionRequests = 0
    val scheduled = mutableListOf<RestAlert>()
    var cancels = 0
    /** The alert currently pending, or null after [cancel]. */
    var pending: RestAlert? = null
        private set

    /** What [notificationsAllowed] answers. */
    var allowed: Boolean? = null
    var allowRequests = 0

    override suspend fun notificationsAllowed() = allowed
    override fun allowNotifications() { allowRequests++ }

    /** What [appPlaysTicks] answers. */
    var appPlays = false
    var ticksPlayed = 0

    override fun requestPermission() { permissionRequests++ }
    override fun appPlaysTicks() = appPlays
    override fun playTicks() { ticksPlayed++ }
    override fun schedule(alert: RestAlert) { scheduled += alert; pending = alert }
    override fun cancel() { cancels++; pending = null }

    val notices = mutableListOf<Pair<WorkoutNotice, Boolean>>()
    var ends = 0
    override fun showWorkout(notice: WorkoutNotice, announce: Boolean) { notices += notice to announce }
    override fun endWorkout() { ends++; cancel() }
}
