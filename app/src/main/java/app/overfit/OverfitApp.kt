package app.overfit

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import app.overfit.data.ExerciseRepository
import app.overfit.data.TemplateRepository
import app.overfit.data.catalog.CatalogSeeder
import app.overfit.data.db.OverfitDatabase
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application-Klasse. Hält den schlanken manuellen DI-Container ([AppContainer]),
 * siehe docs/DECISIONS.md, und konfiguriert Coil.
 */
class OverfitApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            runCatching { container.catalogSeeder.seedIfNeeded() }
                .onFailure { Log.e("Overfit", "Katalog-Import fehlgeschlagen", it) }
        }
    }

    /** WorkManager wird bei Bedarf initialisiert (Initializer im Manifest entfernt). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttp }))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}

/** Manueller DI-Container. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: OverfitDatabase = OverfitDatabase.build(context)
    val catalogSeeder = CatalogSeeder(context, database)
    val exerciseRepository = ExerciseRepository(database)
    val templateRepository = TemplateRepository(database)
    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
