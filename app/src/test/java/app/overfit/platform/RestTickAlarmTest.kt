package app.overfit.platform

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** Planning of the tick alarm next to the rest-end alarm (setting on/off, cancel, ±15 s). */
@RunWith(RobolectricTestRunner::class)
class RestTickAlarmTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val alerts = AndroidRestAlerts(context, android.R.drawable.ic_lock_idle_alarm)

    @Before
    fun exactAllowed() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    private fun alert(endsAt: Long, ticks: Boolean) = RestAlert(
        startedAt = System.currentTimeMillis(), endsAt = endsAt, title = "Pause", overTitle = "Pause vorbei",
        body = "Nächste: Bankdrücken", vibrate = true, plusLabel = "+15 s", skipLabel = "Überspringen",
        channelRunning = "Pausentimer", channelOver = "Pause vorbei", ticks = ticks, ticksBody = "Noch 3 Sekunden",
    )

    /** Trigger time per receiver class. */
    private fun scheduled(): Map<String, Long> = shadowOf(alarms).scheduledAlarms.associate {
        shadowOf(it.operation).savedIntent.component!!.className to it.triggerAtMs
    }

    @Test
    fun ticksOn_secondExactAlarmThreeSecondsBeforeTheEnd() {
        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(alert(end, ticks = true))
        val s = scheduled()
        assertEquals(end, s[RestAlarmReceiver::class.java.name])
        assertEquals(end - 3_000, s[RestTickReceiver::class.java.name])
        val tick = shadowOf(alarms).scheduledAlarms.first { shadowOf(it.operation).savedIntent.component!!.className == RestTickReceiver::class.java.name }
        assertEquals(end - 3_000, shadowOf(tick.operation).savedIntent.getLongExtra("ticksAt", 0))
    }

    @Test
    fun ticksOff_onlyTheEndAlarm() {
        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(alert(end, ticks = true))
        alerts.schedule(alert(end, ticks = false)) // switched off during the rest
        assertEquals(setOf(RestAlarmReceiver::class.java.name), scheduled().keys)
    }

    @Test
    fun tooLittleLeft_noTickAlarm() {
        alerts.schedule(alert(System.currentTimeMillis() + 2_000, ticks = true))
        assertEquals(setOf(RestAlarmReceiver::class.java.name), scheduled().keys)
    }

    @Test
    fun plus15_movesTheTickAlarmAndCancelRemovesBoth() {
        val end = System.currentTimeMillis() + 60_000
        alerts.schedule(alert(end, ticks = true))
        alerts.schedule(alert(end + 15_000, ticks = true))
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(end + 12_000, scheduled()[RestTickReceiver::class.java.name])
        alerts.cancel()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }
}
