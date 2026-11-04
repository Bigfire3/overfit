package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Language
import app.overfit.domain.ThemeMode
import app.overfit.domain.TrackingType
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.ExerciseHero
import app.overfit.ui.components.overfitImageLoader
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import app.overfit.ui.components.Title
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Evidence screenshots of the exercise detail image (start | end pose) on a 360 dp phone, light and dark, for a mix
 * of standing, hanging, lying, hold and stretch exercises. Only runs when `SCREENSHOT_DIR` is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xxhdpi")
class ExerciseHeroShotTest {
    @get:Rule
    val compose = createComposeRule()

    private val slugs = listOf(
        "reverse-lunge" to "Reverse Lunge", "bench-press" to "Bench Press", "pull-up" to "Pull-up", "squat" to "Squat",
        "plank" to "Plank", "bicep-curl" to "Bicep Curl", "deadlift" to "Deadlift", "hamstring-stretch" to "Hamstring Stretch",
        "push-up" to "Push-up", "weighted-crunch" to "Weighted Crunch",
    )

    private fun shot(theme: ThemeMode, label: String) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        val t = stringsFor(Language.EN)
        // Same loader as the app (with the SVG decoder), set explicitly so the test does not depend on the Application.
        SingletonImageLoader.setUnsafe(overfitImageLoader(ApplicationProvider.getApplicationContext()))
        fun exercise(slug: String, name: String) =
            Exercise("wg:$slug", name, ExerciseSource.WORKOUT_GUIDE, "catalog/wg/$slug.svg", emptyList(), emptyList(), null, TrackingType.WEIGHT_REPS, null)
        var current by mutableStateOf(exercise(slugs[0].first, slugs[0].second))
        compose.setContent {
            OverfitTheme(theme) {
                CompositionLocalProvider(LocalStrings provides t) {
                    Column(Modifier.width(360.dp).testTag("shot").background(Theme.colors.background).padding(Space.l)) {
                        ExerciseHero(current, Modifier.testTag("hero"))
                        Spacer(Modifier.height(Space.l))
                        Title(current.name)
                    }
                }
            }
        }
        for ((slug, name) in slugs) {
            current = exercise(slug, name)
            // Coil decodes the SVGs off the main thread (slow at xxhdpi under Robolectric): wait until lines show up.
            waitForInk(if (slug in singles) 1 else 2)
            File(dir!!).mkdirs()
            File(dir, "$slug-$label.png").outputStream().use {
                compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private val singles = setOf("plank", "hamstring-stretch")

    /** Pumps until the image area (above the title) holds [cells] drawn poses: enough foreground pixels per cell. */
    private fun waitForInk(cells: Int) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50); compose.mainClock.advanceTimeBy(50); compose.waitForIdle()
            val bmp = compose.onNodeWithTag("hero").captureToImage().asAndroidBitmap()
            val bg = bmp.getPixel(bmp.width / 2, 2) and 0xff // panel fill at the top edge
            var ink = 0
            for (y in 0 until bmp.height step 3) for (x in 0 until bmp.width step 3) {
                if (kotlin.math.abs((bmp.getPixel(x, y) and 0xff) - bg) > 120) ink++
            }
            if (ink > 150 * cells) { repeat(5) { compose.mainClock.advanceTimeBy(50); compose.waitForIdle() }; return }
        }
        error("images did not load")
    }

    @Test fun light() = shot(ThemeMode.LIGHT, "hell")
    @Test fun dark() = shot(ThemeMode.DARK, "dunkel")
}
