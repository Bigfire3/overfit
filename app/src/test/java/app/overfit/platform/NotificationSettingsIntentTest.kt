package app.overfit.platform

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** "Notifications: allowed" in the profile opens the app's system notification settings. */
@RunWith(RobolectricTestRunner::class)
class NotificationSettingsIntentTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun opensAppNotificationSettings() {
        AndroidRestAlerts(context, android.R.drawable.ic_lock_idle_alarm).openNotificationSettings()
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
