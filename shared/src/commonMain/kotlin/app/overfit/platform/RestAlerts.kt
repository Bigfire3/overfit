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

    override fun requestPermission() { permissionRequests++ }
    override fun schedule(alert: RestAlert) { scheduled += alert; pending = alert }
    override fun cancel() { cancels++; pending = null }
}
