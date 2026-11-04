package app.overfit

import platform.Foundation.NSProcessInfo

/**
 * Launch arguments for the XCUITests in iosApp/OverfitUITests (never set in a normal launch):
 * - `-overfitUITest`: separate UserDefaults suite, database and Keychain entry, wiped at launch
 * - `-overfitKeepData`: keep the UI-test data (relaunch tests)
 * - `-overfitSkipOnboarding`: profile "Fabi", onboarding done
 */
internal class LaunchOptions(args: List<String>) {
    val uiTest = "-overfitUITest" in args
    val keepData = "-overfitKeepData" in args
    val skipOnboarding = "-overfitSkipOnboarding" in args

    companion object {
        fun current() = LaunchOptions(NSProcessInfo.processInfo.arguments.map { it.toString() })
        const val UI_TEST_SUITE = "app.overfit.uitest"
    }
}
