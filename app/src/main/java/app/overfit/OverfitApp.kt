package app.overfit

import android.app.Application
import android.content.Context
import app.overfit.ui.components.overfitImageLoader
import coil3.ImageLoader
import coil3.SingletonImageLoader
import app.overfit.platform.HasAppDependencies
import app.overfit.ui.AppDependencies

/**
 * Application-Klasse. Hält die App-Abhängigkeiten (schlanke manuelle DI, siehe docs/DECISIONS.md).
 * UI und Logik liegen plattformübergreifend in :shared.
 */
class OverfitApp : Application(), HasAppDependencies, SingletonImageLoader.Factory {
    /** Coil asks the Application for its loader the first time an image is drawn (app and Robolectric tests alike). */
    override fun newImageLoader(context: Context): ImageLoader = overfitImageLoader(context)

    override val dependencies: AppDependencies by lazy {
        androidDependencies(
            this,
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            BuildConfig.OVERFIT_BACKEND_URL,
            restIcon = R.drawable.ic_stat_rest,
        )
    }
}
