package app.overfit

import android.content.Context
import app.overfit.ai.Providers
import app.overfit.data.AppStore
import app.overfit.data.KeyValueStore
import app.overfit.data.SecretStore
import app.overfit.platform.AndroidKeyValueStore
import app.overfit.platform.AndroidSecretStore
import app.overfit.platform.createHttpClient
import app.overfit.platform.deviceDefaults
import app.overfit.ui.AppDependencies

/** Builds the app's dependencies for Android. Storage can be swapped in tests. */
fun androidDependencies(
    context: Context,
    versionName: String,
    kv: KeyValueStore = AndroidKeyValueStore(context),
    secrets: SecretStore = AndroidSecretStore(context),
): AppDependencies = AppDependencies(
    store = AppStore(kv, secrets, deviceDefaults()),
    providers = Providers(createHttpClient()),
    versionName = versionName,
)
