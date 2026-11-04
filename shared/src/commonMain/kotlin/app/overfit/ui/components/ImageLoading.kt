package app.overfit.ui.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.svg.SvgDecoder

/**
 * The app's image loader (SVG exercise drawings, photos, crossfade). Coil keeps one per process:
 * Android hands it out through the Application ([SingletonImageLoader.Factory]), iOS calls
 * [installOverfitImageLoader] before the first screen. Never set it from inside a composable:
 * once Coil created its default loader (e.g. another screen drew an image first), that throws.
 */
fun overfitImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()

/** Registers [overfitImageLoader] as Coil's singleton (iOS start). Safe to call more than once. */
fun installOverfitImageLoader() {
    SingletonImageLoader.setSafe { overfitImageLoader(it) }
}
