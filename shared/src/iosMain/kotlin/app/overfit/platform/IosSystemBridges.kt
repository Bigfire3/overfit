package app.overfit.platform

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Tapping the rest notification opens the maximized workout (like Android's notification intent).
 * UNUserNotificationCenter holds its delegate weakly, so this object keeps it alive.
 */
object IosNotificationTaps {
    private var delegate: Delegate? = null
    private var handler: (() -> Unit)? = null
    private var pending = false

    /** Called from Swift at app launch, so a tap that launched the app is not lost. */
    fun install() {
        if (delegate == null) delegate = Delegate { tapped() }.also { UNUserNotificationCenter.currentNotificationCenter().delegate = it }
    }

    fun onRestNotification(open: () -> Unit) {
        install()
        handler = open
        if (pending) { pending = false; open() }
    }

    private fun tapped() {
        val open = handler
        if (open != null) open() else pending = true
    }

    private class Delegate(val onTap: () -> Unit) : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit,
        ) {
            if (didReceiveNotificationResponse.notification.request.identifier == IosRestAlerts.ID) onTap()
            withCompletionHandler()
        }
    }
}

/**
 * The app is always dark: the iOS windows are forced to the dark interface style, so status bar text,
 * keyboard, share sheet and pickers are dark too, whatever the system setting (Info.plist also sets
 * `UIUserInterfaceStyle = Dark`, this covers windows created later, e.g. the share sheet's).
 */
object IosAppearance {
    private val current: UIUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark

    fun applyToWindows() = windows().forEach { it.overrideUserInterfaceStyle = current }

    private fun windows(): List<UIWindow> =
        UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().flatMap { scene ->
            scene.windows.filterIsInstance<UIWindow>()
        }
}
