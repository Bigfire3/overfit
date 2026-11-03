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
import app.overfit.platform.KeychainSecretStore
import app.overfit.platform.createHttpClient
import app.overfit.share.IosIncoming
import app.overfit.share.IosPlatformShare
import app.overfit.platform.deviceDefaults
import app.overfit.ui.AppDependencies
import app.overfit.ui.OverfitApp
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/** Entry point for the iOS app (called from Swift). */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
    val build = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"
    // Set at build time (xcodebuild OVERFIT_BACKEND_URL=… → Info.plist); empty = Fuel/Spot not configured.
    val backendUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("OverfitBackendUrl") as? String ?: ""
    val kv = IosKeyValueStore()
    val fuelAccess = FuelAccess(kv, backendUrl) { url -> FuelApi(createHttpClient(), url, RequestSigner(IosDeviceKey()), IosAttestor()) }
    val deps = AppDependencies(
        store = AppStore(kv, KeychainSecretStore(), deviceDefaults()),
        providers = Providers(createHttpClient()) { fuelAccess.service() },
        data = AppData(buildDatabase(), kv, photoFiles = IosPhotoFiles()),
        versionName = "$version ($build)",
        haptics = IosHaptics(),
        restAlerts = IosRestAlerts(),
        fuel = fuelAccess,
        share = IosPlatformShare(),
        inbox = IosIncoming.inbox,
        kv = kv,
        // iOS builds come from the App Store / TestFlight: web purchase only where the server allows `store`.
        distribution = app.overfit.fuel.Distribution.Store,
    )
    return ComposeUIViewController { OverfitApp(deps) }
}
