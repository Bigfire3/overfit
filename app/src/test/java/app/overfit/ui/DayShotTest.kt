package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.DiaryPhoto
import app.overfit.data.WorkoutPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Theme
import coil3.SingletonImageLoader
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Evidence screenshots (360 dp, dark) of a calendar day: trainings, photos, weight. Real (grey) photo files.
 * Runs only with `SCREENSHOT_DIR` (before/after: same class, different directory).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1100dp-xxhdpi")
@OptIn(coil3.annotation.DelicateCoilApi::class)
class DayShotTest {
    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val day = LocalDate(2026, 11, 7)
    private fun ms(h: Int) = day.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + h * 3_600_000L

    /** A small one-coloured grey JPEG, so the previews show up. */
    private fun jpeg(id: String, grey: Int): String {
        val bmp = android.graphics.Bitmap.createBitmap(60, 80, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(grey, grey, grey))
        val f = File(tmp.root, "$id.jpg")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        return f.absolutePath
    }

    private val bank = app.overfit.domain.Exercise("c:bank", "Bankdrücken", app.overfit.domain.ExerciseSource.CUSTOM, null, emptyList(), emptyList(), null, app.overfit.domain.TrackingType.WEIGHT_REPS, 90)
    private val ohp = bank.copy(id = "c:ohp", name = "Schulterdrücken")
    private val dips = bank.copy(id = "c:dips", name = "Dips", trackingType = app.overfit.domain.TrackingType.REPS)

    private fun workout(): app.overfit.domain.Workout {
        var w = app.overfit.domain.Workout.fromTemplate(
            app.overfit.domain.Template(1, "Push", null, listOf(
                app.overfit.domain.TemplateExercise(bank, 90, null, List(3) { app.overfit.domain.PlannedSet(80.0, 8) }),
                app.overfit.domain.TemplateExercise(ohp, 90, null, List(3) { app.overfit.domain.PlannedSet(45.0, 10) }),
                app.overfit.domain.TemplateExercise(dips, 60, null, List(2) { app.overfit.domain.PlannedSet(reps = 12) }),
            )),
            ms(18), id = "s1",
        )
        w.exercises.forEach { e -> e.sets.forEach { w = w.setValues(it.id, it.target).completeSet(it.id, ms(19)) } }
        return w
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Robolectric has no ImageDecoder: decode the JPEGs with BitmapFactory.
        val loader = coil3.ImageLoader.Builder(ctx).components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }.build()
        SingletonImageLoader.setUnsafe(loader)
        // Warm Coil's memory cache, so the previews are there on the first frames.
        File(tmp.root.path).listFiles().orEmpty().forEach { f ->
            val r = kotlinx.coroutines.runBlocking { loader.execute(coil3.request.ImageRequest.Builder(ctx).data(f.absolutePath).build()) }
            println("preload ${f.name}: ${r::class.simpleName} ${(r as? coil3.request.ErrorResult)?.throwable}")
        }
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) {
                    Box(Modifier.width(360.dp).height(1100.dp).background(Theme.colors.background).testTag("shot")) {
                        app.overfit.ui.components.ConfirmArea(Modifier) { content() }
                    }
                }
            }
        }
        // Coil decodes the files off the main thread: give it time.
        repeat(60) { Thread.sleep(50); compose.mainClock.advanceTimeBy(50); compose.waitForIdle() }
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun day() {
        val photos = listOf(
            DiaryPhoto(WorkoutPhoto("a", "s1", "a.jpg", ms(19), jpeg("a", 0x70)), "Push"),
            DiaryPhoto(WorkoutPhoto("b", null, "b.jpg", ms(20), jpeg("b", 0x88)), null),
        )
        val history = listOf(HistoryRow("s1", "Push", ms(18), ms(19), null, 3, 8, 4370.0, 0).copy(sauna = true))
        val weights = listOf(WeightEntry("2026-11-06", 82.6), WeightEntry("2026-11-07", 82.4))
        val w = workout()
        shot("tag") {
            app.overfit.ui.photos.DayScreen(
                day, photos, history, WeightUnit.KG, Language.DE,
                onBack = {}, onOpenPhoto = {}, onOpenWorkout = {}, onAddPhoto = {}, onRemovePhoto = {},
                weights = weights,
                workouts = mapOf("s1" to w),
            )
        }
    }
}
