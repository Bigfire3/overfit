package app.overfit

import android.app.Application

/**
 * Application-Klasse. Hält den schlanken manuellen DI-Container ([AppContainer]),
 * siehe docs/DECISIONS.md.
 */
class OverfitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manueller DI-Container. Repositories, Datenbank usw. kommen in späteren Phasen hinzu. */
class AppContainer(@Suppress("unused") private val app: Application)
