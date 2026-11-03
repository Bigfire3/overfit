package app.overfit

import android.app.Application
import app.overfit.ui.AppDependencies

/**
 * Application-Klasse. Hält die App-Abhängigkeiten (schlanke manuelle DI, siehe docs/DECISIONS.md).
 * UI und Logik liegen plattformübergreifend in :shared.
 */
class OverfitApp : Application() {
    val dependencies: AppDependencies by lazy {
        androidDependencies(this, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", BuildConfig.OVERFIT_BACKEND_URL)
    }
}
