package app.overfit

import android.content.Context
import app.overfit.ai.Providers
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.KeyValueStore
import app.overfit.data.SecretStore
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.buildDatabase
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelApi
import app.overfit.fuel.RequestSigner
import app.overfit.platform.AndroidDeviceKey
import app.overfit.platform.AndroidHaptics
import app.overfit.platform.AndroidPhotoFiles
import app.overfit.platform.AndroidKeyValueStore
import app.overfit.platform.AndroidRestAlerts
import app.overfit.platform.AndroidSecretStore
import app.overfit.platform.createHttpClient
import app.overfit.platform.deviceDefaults
import app.overfit.share.AndroidPlatformShare
import app.overfit.share.ImportInbox
import app.overfit.ui.AppDependencies

/** Builds the app's dependencies for Android. Storage and database can be swapped in tests. */
fun androidDependencies(
    context: Context,
    versionName: String,
    backendUrl: String = "",
    kv: KeyValueStore = AndroidKeyValueStore(context),
    secrets: SecretStore = AndroidSecretStore(context),
    database: OverfitDatabase = buildDatabase(context),
    /** Monochrome status bar icon for the rest timer notifications. */
    restIcon: Int = context.applicationInfo.icon,
): AppDependencies {
    val fuel = FuelAccess(kv, backendUrl) { url ->
        FuelApi(createHttpClient(), url, RequestSigner(AndroidDeviceKey()))
    }
    return AppDependencies(
    store = AppStore(kv, secrets, deviceDefaults()),
    providers = Providers(createHttpClient()) { fuel.service() },
    data = AppData(database, kv, photoFiles = AndroidPhotoFiles(context)),
    versionName = versionName,
    haptics = AndroidHaptics(context),
    restAlerts = AndroidRestAlerts(context, restIcon),
    fuel = fuel,
    share = AndroidPlatformShare(context),
    inbox = ImportInbox(),
    kv = kv,
    distribution = androidDistribution(context),
    )
}

/**
 * Store build = installed by Google Play (or another store app); everything else (APK from GitHub, adb) is sideloaded.
 * The web purchase button follows this (store rules), see `/v1/config` → `webPurchase.channels`.
 */
fun androidDistribution(context: Context): app.overfit.fuel.Distribution {
    val installer = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
    }.getOrNull()
    return if (installer in STORE_INSTALLERS) app.overfit.fuel.Distribution.Store else app.overfit.fuel.Distribution.Sideload
}

private val STORE_INSTALLERS = setOf("com.android.vending", "com.google.android.feedback", "com.amazon.venezia", "com.huawei.appmarket", "com.sec.android.app.samsungapps")
