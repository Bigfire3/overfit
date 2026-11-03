package app.overfit

import app.overfit.domain.ThemeMode
import platform.Foundation.NSProcessInfo

/**
 * Launch arguments for the XCUITests in iosApp/OverfitUITests (never set in a normal launch):
 * - `-overfitUITest`: separate UserDefaults suite, database and Keychain entry, wiped at launch
 * - `-overfitKeepData`: keep the UI-test data (relaunch tests)
 * - `-overfitTheme light|dark`: preset the app theme
 * - `-overfitSkipOnboarding`: profile "Fabi", onboarding done
 */
internal class LaunchOptions(args: List<String>) {
    val uiTest = "-overfitUITest" in args
    val keepData = "-overfitKeepData" in args
    val skipOnboarding = "-overfitSkipOnboarding" in args
    val theme: ThemeMode? = args.indexOf("-overfitTheme").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.let {
        when (it) { "dark" -> ThemeMode.DARK; "light" -> ThemeMode.LIGHT; else -> null }
    }

    companion object {
        fun current() = LaunchOptions(NSProcessInfo.processInfo.arguments.map { it.toString() })
        const val UI_TEST_SUITE = "app.overfit.uitest"
    }
}
