package app.overfit

import android.app.Application
import app.overfit.platform.HasAppDependencies
import app.overfit.ui.AppDependencies

/**
 * Application-Klasse. Hält die App-Abhängigkeiten (schlanke manuelle DI, siehe docs/DECISIONS.md).
 * UI und Logik liegen plattformübergreifend in :shared.
 */
class OverfitApp : Application(), HasAppDependencies {
    override val dependencies: AppDependencies by lazy {
        androidDependencies(this, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", restIcon = R.drawable.ic_stat_rest)
    }
}
