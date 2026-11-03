package app.overfit

import androidx.compose.ui.window.ComposeUIViewController
import app.overfit.ai.Providers
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.db.buildDatabase
import app.overfit.platform.IosKeyValueStore
import app.overfit.platform.KeychainSecretStore
import app.overfit.platform.createHttpClient
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
    val kv = IosKeyValueStore()
    val deps = AppDependencies(
        store = AppStore(kv, KeychainSecretStore(), deviceDefaults()),
        providers = Providers(createHttpClient()),
        data = AppData(buildDatabase(), kv),
        versionName = "$version ($build)",
    )
    return ComposeUIViewController { OverfitApp(deps) }
}
