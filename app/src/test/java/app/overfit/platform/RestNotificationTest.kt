package app.overfit.platform

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The countdown notification cannot be swiped away for good while the rest runs. */
@RunWith(RobolectricTestRunner::class)
class RestNotificationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val alerts = AndroidRestAlerts(context, android.R.drawable.ic_lock_idle_alarm)

    @Before
    fun allowNotifications() = shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun alert(endsAt: Long) = RestAlert(
        startedAt = System.currentTimeMillis(), endsAt = endsAt, title = "Pause", overTitle = "Pause vorbei",
        body = "Nächste: Bankdrücken", vibrate = true, plusLabel = "+15 s", skipLabel = "Überspringen",
        channelRunning = "Pausentimer", channelOver = "Pause vorbei",
    )

    private fun countdown(): Notification? =
        shadowOf(nm).allNotifications.firstOrNull { it.extras.getString(Notification.EXTRA_TITLE) == "Pause" }

    /** What the system does on a swipe: removes it and sends the delete intent. */
    private fun swipe(n: Notification) {
        nm.cancelAll()
        RestDismissedReceiver().onReceive(context, shadowOf(n.deleteIntent).savedIntent)
    }

    @Test
    fun swipedAwayWhileTheRestRuns_comesBackRightAway() {
        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(alert(end))
        val first = countdown()!!
        assertTrue(first.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(first.deleteIntent)

        swipe(first)
        val again = countdown()
        assertNotNull("posted again", again)
        assertEquals(end, again!!.`when`)
        assertEquals("Nächste: Bankdrücken", again.extras.getString(Notification.EXTRA_TEXT))
        // And again: as often as the user swipes.
        swipe(again)
        assertNotNull(countdown())
    }

    @Test
    fun afterTheRestOrWorkoutEnds_itStaysGone() {
        alerts.schedule(alert(System.currentTimeMillis() + 60_000))
        val n = countdown()!!
        alerts.cancel() // skip, workout finished or discarded
        assertEquals(null, countdown())
        // A late delete intent of an ended rest does nothing.
        alerts.schedule(alert(System.currentTimeMillis() + 500))
        swipe(countdown()!!)
        assertEquals(null, countdown())
        assertNotNull(n)
    }
}
