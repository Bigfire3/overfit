package app.overfit.platform

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The workout notification: there during the whole workout, green with the set, red with the rest
 * countdown, announced at each transition, back right away after a swipe, held by
 * [TrainingService] and gone when the workout ends.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutNotificationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val alerts = AndroidRestAlerts(context, android.R.drawable.ic_lock_idle_alarm)

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidRestAlerts.resetForTest()
        AppVisibility.visible = false
    }

    @After
    fun tearDown() {
        AndroidRestAlerts.resetForTest()
        AppVisibility.visible = false
    }

    private val started = 1_000L

    private fun notice(title: String = "Bankdrücken", body: String = "Satz 2/4 · 80 kg × 5", note: String? = null) = WorkoutNotice(
        title = title, body = body, startedAt = started, doneLabel = "Satz fertig", vibrate = true,
        channel = "Laufendes Training", channelSilent = "Laufendes Training (ohne Vibration)", note = note,
    )

    private fun rest(endsAt: Long, announce: Boolean = false) = RestAlert(
        startedAt = System.currentTimeMillis(), endsAt = endsAt, title = "Pause", overTitle = "Pause vorbei",
        body = "Nächste: Bankdrücken · Satz 3 · 80 kg × 5", vibrate = true, plusLabel = "+15 s", skipLabel = "Überspringen",
        channel = "Laufendes Training", channelSilent = "Laufendes Training (ohne Vibration)",
        announce = announce, after = notice(body = "Satz 3/4 · 80 kg × 5", note = "Pause vorbei"),
    )

    private fun shown(): Notification? = shadowOf(nm).getNotification(AndroidRestAlerts.ID_TRAINING)

    private fun title(n: Notification?) = n?.extras?.getString(Notification.EXTRA_TITLE)
    private fun text(n: Notification) = n.extras.getString(Notification.EXTRA_TEXT)
    private fun actions(n: Notification) = n.actions.orEmpty().map { it.title.toString() }

    /** Announced: alerts once (no "only alert once", not silenced by the group). */
    private fun announced(n: Notification) =
        n.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0 && n.groupAlertBehavior == Notification.GROUP_ALERT_ALL

    private fun silent(n: Notification) =
        n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && n.groupAlertBehavior == Notification.GROUP_ALERT_SUMMARY

    /** What the system does on a swipe: removes it and sends the delete intent. */
    private fun swipe(n: Notification) {
        nm.cancel(AndroidRestAlerts.ID_TRAINING)
        RestDismissedReceiver().onReceive(context, shadowOf(n.deleteIntent).savedIntent)
    }

    @Test
    fun duringASet_greenWithTheSetAndSetDone() {
        alerts.showWorkout(notice())
        val n = shown()!!
        assertEquals("Bankdrücken", title(n))
        assertEquals("Satz 2/4 · 80 kg × 5", text(n))
        assertEquals(AndroidRestAlerts.COLOR_SET, n.color)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Satz fertig"), actions(n))
        // Training time counts up from the start.
        assertEquals(started, n.`when`)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(silent(n)) // starting a workout does not pop up
        assertEquals(AndroidRestAlerts.CHANNEL_WORKOUT, n.channelId)
    }

    @Test
    fun duringARest_redCountdownWithPlusAndSkip() {
        val end = System.currentTimeMillis() + 60_000
        alerts.showWorkout(notice())
        alerts.schedule(rest(end))
        val n = shown()!!
        assertEquals("Pause", title(n))
        assertEquals("Nächste: Bankdrücken · Satz 3 · 80 kg × 5", text(n))
        assertEquals(AndroidRestAlerts.COLOR_REST, n.color)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(end, n.`when`)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(listOf("+15 s", "Überspringen"), actions(n))
        // One notification for the whole workout, not one per rest.
        assertEquals(1, shadowOf(nm).allNotifications.size)
    }

    @Test
    fun transitions_announceOnce_otherUpdatesStaySilent() {
        val end = System.currentTimeMillis() + 60_000
        alerts.showWorkout(notice())
        alerts.schedule(rest(end, announce = true)) // set checked off
        assertTrue(announced(shown()!!))
        alerts.schedule(rest(end + 15_000)) // +15 s
        assertTrue(silent(shown()!!))
        alerts.showWorkout(notice(), announce = true) // rest → set
        assertTrue(announced(shown()!!))
        alerts.showWorkout(notice(body = "Satz 2/4 · 82,5 kg × 5")) // value changed
        assertTrue(silent(shown()!!))
    }

    @Test
    fun appVisible_transitionsStaySilent() {
        AppVisibility.visible = true
        alerts.schedule(rest(System.currentTimeMillis() + 60_000, announce = true))
        assertTrue(silent(shown()!!))
    }

    @Test
    fun vibrationOff_silentChannel() {
        alerts.showWorkout(notice().copy(vibrate = false), announce = true)
        assertEquals(AndroidRestAlerts.CHANNEL_WORKOUT_SILENT, shown()!!.channelId)
    }

    @Test
    fun swipedAway_comesBackRightAway_inSetAndRest() {
        alerts.showWorkout(notice())
        swipe(shown()!!)
        assertEquals("Bankdrücken", title(shown()))

        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(rest(end))
        val first = shown()!!
        assertNotNull(first.deleteIntent)
        swipe(first)
        val again = shown()!!
        assertEquals("Pause", title(again))
        assertEquals(end, again.`when`)
        assertTrue("a swipe is no transition", silent(again))
        // As often as the user swipes.
        swipe(again)
        assertNotNull(shown())
    }

    @Test
    fun swipedAtTheVeryEndOfARest_comesBackAsTheNextSet() {
        alerts.schedule(rest(System.currentTimeMillis() + 500))
        swipe(shown()!!)
        assertEquals("Bankdrücken", title(shown()))
        assertEquals("Satz 3/4 · 80 kg × 5", text(shown()!!))
    }

    @Test
    fun restAlarm_switchesToTheNextSetAndAnnouncesIt() {
        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(rest(end, announce = true))
        RestAlarmReceiver().onReceive(context, alarmIntent())
        val n = shown()!!
        assertEquals("Bankdrücken", title(n))
        assertEquals("Satz 3/4 · 80 kg × 5", text(n))
        assertEquals("Pause vorbei", n.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals(AndroidRestAlerts.COLOR_SET, n.color)
        assertTrue(announced(n))
        assertEquals(listOf("Satz fertig"), actions(n))
    }

    @Test
    fun restAlarm_afterAProcessKill_usesItsExtras() {
        alerts.schedule(rest(System.currentTimeMillis() + 60_000))
        val intent = alarmIntent()
        nm.cancelAll()
        AndroidRestAlerts.resetForTest() // new process: nothing known in memory
        RestAlarmReceiver().onReceive(context, intent)
        assertEquals("Satz 3/4 · 80 kg × 5", text(shown()!!))
    }

    @Test
    fun staleRestAlarm_changesNothing() {
        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(rest(end))
        val old = Intent(alarmIntent()) // copy: the PendingIntent itself gets the new extras
        alerts.schedule(rest(end + 15_000)) // +15 s: newer rest
        RestAlarmReceiver().onReceive(context, old)
        assertEquals("Pause", title(shown()))
        alerts.endWorkout()
        RestAlarmReceiver().onReceive(context, old)
        assertNull(shown())
    }

    @Test
    fun workoutEnds_notificationGoesAndStaysGone_serviceStops() {
        alerts.showWorkout(notice())
        val n = shown()!!
        startServiceAsAndroidWould()
        assertTrue(TrainingService.running)
        alerts.endWorkout()
        assertNull(shown())
        assertFalse(TrainingService.running)
        assertEquals(TrainingService::class.java.name, shadowOf(context).nextStoppedService?.component?.className)
        // A late delete intent of the ended workout does nothing.
        RestDismissedReceiver().onReceive(context, shadowOf(n.deleteIntent).savedIntent)
        assertNull(shown())
    }

    @Test
    fun service_holdsTheWorkoutNotificationInTheForeground() {
        alerts.showWorkout(notice())
        val service = startServiceAsAndroidWould()
        val fg = shadowOf(service).lastForegroundNotification
        assertEquals("Bankdrücken", title(fg))
        assertEquals(AndroidRestAlerts.ID_TRAINING, shadowOf(service).lastForegroundNotificationId)
        // Started once; later updates go to the same notification.
        alerts.schedule(rest(System.currentTimeMillis() + 60_000))
        assertNull(shadowOf(context).nextStartedService)
        assertEquals("Pause", title(shown()))
    }

    @Test
    fun service_workoutEndedBeforeItStarted_stopsItself() {
        alerts.showWorkout(notice())
        val intent = shadowOf(context).nextStartedService
        alerts.endWorkout()
        val service = Robolectric.buildService(TrainingService::class.java, intent).create().startCommand(0, 1).get()
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(TrainingService.running)
    }

    @Test
    fun announcedTransition_serviceTakesItOverSilently_noDoubleAlert() {
        alerts.schedule(rest(System.currentTimeMillis() + 60_000, announce = true))
        assertTrue(announced(shown()!!))
        val service = startServiceAsAndroidWould()
        assertTrue(silent(shadowOf(service).lastForegroundNotification))
    }

    @Test
    fun restAlarm_afterTheAppAlreadySwitched_keepsTheFreshValues() {
        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(rest(end))
        val intent = alarmIntent()
        // The running app ended the rest itself and the user changed the weight since.
        alerts.showWorkout(notice(body = "Satz 3/4 · 82,5 kg × 5"))
        RestAlarmReceiver().onReceive(context, intent)
        val n = shown()!!
        assertEquals("Satz 3/4 · 82,5 kg × 5", text(n))
        assertEquals("Pause vorbei", n.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertTrue(announced(n))
    }

    @Test
    fun notificationsBlocked_noService() {
        shadowOf(nm).setNotificationsEnabled(false)
        alerts.showWorkout(notice())
        assertNull(shadowOf(context).nextStartedService)
    }

    /** The service start [AndroidRestAlerts] asked for, as Android would run it. */
    private fun startServiceAsAndroidWould(): TrainingService {
        val intent = shadowOf(context).nextStartedService
        assertEquals(TrainingService::class.java.name, intent?.component?.className)
        return Robolectric.buildService(TrainingService::class.java, intent).create().startCommand(0, 1).get()
    }

    /** The rest-end alarm's broadcast as scheduled. */
    private fun alarmIntent(): Intent {
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        val alarm = shadowOf(am).scheduledAlarms.first {
            shadowOf(it.operation).savedIntent.component!!.className == RestAlarmReceiver::class.java.name
        }
        return shadowOf(alarm.operation).savedIntent
    }
}
