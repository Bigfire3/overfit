package app.overfit

import app.overfit.ui.components.CONFIRM_ARMED_MILLIS
import app.overfit.ui.components.CONFIRM_ARMED_MILLIS_UI_TEST
import platform.Foundation.NSProcessInfo

/**
 * Launch arguments for the XCUITests in iosApp/OverfitUITests (never set in a normal launch):
 * - `-overfitUITest`: separate UserDefaults suite, database and Keychain entry, wiped at launch
 * - `-overfitKeepData`: keep the UI-test data (relaunch tests)
 * - `-overfitSkipOnboarding`: profile "Fabi", onboarding done
 *
 * In UI-test mode confirm buttons also stay armed longer ([confirmArmedMillis]).
 */
internal class LaunchOptions(args: List<String>) {
    val uiTest = "-overfitUITest" in args
    val keepData = "-overfitKeepData" in args
    val skipOnboarding = "-overfitSkipOnboarding" in args
    /** Armed window of the two-tap buttons: 3 s in the app, longer under XCUITest (see [CONFIRM_ARMED_MILLIS_UI_TEST]). */
    val confirmArmedMillis = if (uiTest) CONFIRM_ARMED_MILLIS_UI_TEST else CONFIRM_ARMED_MILLIS

    companion object {
        fun current() = LaunchOptions(NSProcessInfo.processInfo.arguments.map { it.toString() })
        const val UI_TEST_SUITE = "app.overfit.uitest"
    }
}
