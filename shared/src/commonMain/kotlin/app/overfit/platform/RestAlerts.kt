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
    val channelRunning: String,
    val channelOver: String,
    /** Ticks at 3, 2 and 1 s before [endsAt] (setting "Countdown ticks"). */
    val ticks: Boolean = false,
    /** "3 seconds left" – text of the tick notification where the platform needs one (iOS). */
    val ticksBody: String = "",
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

    fun cancel()

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
}
