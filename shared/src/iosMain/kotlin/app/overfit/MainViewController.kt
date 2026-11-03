package app.overfit

import androidx.compose.ui.window.ComposeUIViewController
import app.overfit.ai.Providers
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.db.buildDatabase
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelApi
import app.overfit.fuel.RequestSigner
import app.overfit.platform.IosAttestor
import app.overfit.platform.IosDeviceKey
import app.overfit.platform.IosHaptics
import app.overfit.platform.IosPhotoFiles
import app.overfit.platform.IosKeyValueStore
import app.overfit.platform.IosRestAlerts
import app.overfit.platform.IosAppearance
import app.overfit.platform.IosNotificationTaps
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.map
import app.overfit.platform.KeychainSecretStore
import app.overfit.platform.createHttpClient
import app.overfit.share.IosIncoming
import app.overfit.share.IosPlatformShare
import app.overfit.platform.deviceDefaults
import app.overfit.ui.AppDependencies
import app.overfit.ui.OverfitApp
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import app.overfit.data.db.OverfitDatabase
import platform.UIKit.UIViewController

/** Entry point for the iOS app (called from Swift). */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
    val build = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"
    // Set at build time (xcodebuild OVERFIT_BACKEND_URL=… → Info.plist); empty = Fuel/Spot not configured.
    val backendUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("OverfitBackendUrl") as? String ?: ""
    val launch = LaunchOptions.current()
    val kv = if (launch.uiTest) {
        if (!launch.keepData) NSUserDefaults.standardUserDefaults.removePersistentDomainForName(LaunchOptions.UI_TEST_SUITE)
        IosKeyValueStore(NSUserDefaults(suiteName = LaunchOptions.UI_TEST_SUITE))
    } else {
        IosKeyValueStore()
    }
    val fuelAccess = FuelAccess(kv, backendUrl) { url -> FuelApi(createHttpClient(), url, RequestSigner(IosDeviceKey()), IosAttestor()) }
    val deps = AppDependencies(
        store = AppStore(kv, KeychainSecretStore(service = if (launch.uiTest) LaunchOptions.UI_TEST_SUITE else "app.overfit"), deviceDefaults()),
        providers = Providers(createHttpClient()) { fuelAccess.service() },
        data = AppData(
            if (launch.uiTest) buildDatabase("uitest-" + OverfitDatabase.NAME, fresh = !launch.keepData) else buildDatabase(),
            kv,
            photoFiles = IosPhotoFiles(),
        ),
        versionName = "$version ($build)",
        haptics = IosHaptics(),
        restAlerts = IosRestAlerts(),
        fuel = fuelAccess,
        share = IosPlatformShare(),
        inbox = IosIncoming.inbox,
        kv = kv,
    )
    if (launch.uiTest) {
        launch.theme?.let(deps.store::setTheme)
        if (launch.skipOnboarding && !launch.keepData) {
            deps.store.setName("Fabi")
            deps.store.completeOnboarding()
        }
    }
    IosNotificationTaps.onRestNotification { deps.requestOpenWorkout() }
    IosAppearance.follow(deps.store.state.map { it.settings.theme })
    return ComposeUIViewController {
        // The window exists once Compose is shown; apply the theme's interface style to it.
        LaunchedEffect(Unit) { IosAppearance.applyToWindows() }
        OverfitApp(deps)
    }
}
