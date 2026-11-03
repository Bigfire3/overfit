package app.overfit.platform

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock

/**
 * Rest end on iOS: a local notification scheduled for the end time. iOS delivers it while the app
 * is in the background or the screen is locked; while the app is open it is not shown (no
 * delegate presents it), the app vibrates itself. With vibration on, the default sound is used,
 * because iOS only vibrates for notifications that have a sound (silent mode: vibration only).
 */
class IosRestAlerts : RestAlerts {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun requestPermission() {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun schedule(alert: RestAlert) {
        val seconds = (alert.endsAt - Clock.System.now().toEpochMilliseconds()) / 1000.0
        center.removeDeliveredNotificationsWithIdentifiers(listOf(ID))
        if (seconds <= 0) {
            center.removePendingNotificationRequestsWithIdentifiers(listOf(ID))
            return
        }
        val content = UNMutableNotificationContent().apply {
            setTitle(alert.overTitle)
            setBody(alert.body)
            if (alert.vibrate) setSound(UNNotificationSound.defaultSound)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        // Same identifier: replaces a pending request (±15 s).
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(ID, content, trigger), null)
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(ID))
    }

    companion object {
        const val ID = "app.overfit.rest"
    }
}
