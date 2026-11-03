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
import app.overfit.platform.AndroidAttestor
import app.overfit.platform.AndroidDeviceKey
import app.overfit.platform.AndroidHaptics
import app.overfit.platform.AndroidKeyValueStore
import app.overfit.platform.AndroidSecretStore
import app.overfit.platform.createHttpClient
import app.overfit.platform.deviceDefaults
import app.overfit.ui.AppDependencies

/** Builds the app's dependencies for Android. Storage and database can be swapped in tests. */
fun androidDependencies(
    context: Context,
    versionName: String,
    backendUrl: String = "",
    kv: KeyValueStore = AndroidKeyValueStore(context),
    secrets: SecretStore = AndroidSecretStore(context),
    database: OverfitDatabase = buildDatabase(context),
): AppDependencies {
    val fuel = FuelAccess(kv, backendUrl) { url ->
        FuelApi(createHttpClient(), url, RequestSigner(AndroidDeviceKey()), AndroidAttestor(context))
    }
    return AppDependencies(
    store = AppStore(kv, secrets, deviceDefaults()),
    providers = Providers(createHttpClient()) { fuel.service() },
    data = AppData(database, kv),
    versionName = versionName,
    haptics = AndroidHaptics(context),
    fuel = fuel,
    )
}
