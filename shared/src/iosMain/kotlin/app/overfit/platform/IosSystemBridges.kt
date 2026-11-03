package app.overfit.platform

import app.overfit.domain.ThemeMode
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
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

    fun install(onRestNotification: () -> Unit) {
        delegate = Delegate(onRestNotification).also { UNUserNotificationCenter.currentNotificationCenter().delegate = it }
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
 * The app theme (Light/Dark/System) also sets the iOS interface style of the window: status bar
 * text, keyboard, share sheet and pickers then match the app instead of the system setting.
 */
object IosAppearance {
    fun follow(themes: kotlinx.coroutines.flow.Flow<ThemeMode>) {
        MainScope().launch { themes.distinctUntilChanged().collect { apply(it) } }
    }

    private fun apply(mode: ThemeMode) {
        val style = when (mode) {
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        }
        current = style
        windows().forEach { it.overrideUserInterfaceStyle = style }
    }

    /** Last style; also applied when the window appears after launch (see MainViewController). */
    var current: UIUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        private set

    fun applyToWindows() = windows().forEach { it.overrideUserInterfaceStyle = current }

    private fun windows(): List<UIWindow> =
        UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().flatMap { scene ->
            scene.windows.filterIsInstance<UIWindow>()
        }
}
