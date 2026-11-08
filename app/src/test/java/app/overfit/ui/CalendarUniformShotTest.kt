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
 * Evidence screenshots (360 dp, dark) of the calendar markers and the list, with real (grey) photo files.
 * Runs only with `SCREENSHOT_DIR` (before/after: same class, different directory).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi")
@OptIn(coil3.annotation.DelicateCoilApi::class)
class CalendarUniformShotTest {
    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 7)
    private fun ms(d: LocalDate) = d.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
    private fun session(id: String, d: LocalDate, name: String, sauna: Boolean = false) =
        HistoryRow(id, name, ms(d), ms(d) + 3_600_000L, null, 3, 9, 4200.0, 0).copy(sauna = sauna)

    /** A small one-coloured grey JPEG, so the previews show up. */
    private fun jpeg(id: String, grey: Int): String {
        val bmp = android.graphics.Bitmap.createBitmap(60, 80, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(grey, grey, grey))
        val f = File(tmp.root, "$id.jpg")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        return f.absolutePath
    }

    private fun photo(id: String, d: LocalDate, session: String?, name: String?, grey: Int, offset: Long = 0) =
        DiaryPhoto(WorkoutPhoto(id, session, "$id.jpg", ms(d) + offset, jpeg(id, grey)), name)

    private fun d(day: Int) = LocalDate(2026, 11, day)

    // 2: training only · 3: training + sauna · 4: photo only · 5: photo + training + sauna · 6: weight only ·
    // 7 (today): training + weight · 1: three photos + training · 30 Oct: training + weight.
    private val history = listOf(
        session("s7", d(7), "Push"),
        session("s5", d(5), "Legs", sauna = true),
        session("s3", d(3), "Pull", sauna = true),
        session("s2", d(2), "Push"),
        session("s1", d(1), "Upper"),
        session("s0", LocalDate(2026, 10, 30), "Legs"),
    )
    private fun photos() = listOf(
        photo("p5", d(5), "s5", "Legs", 0x70),
        photo("p4", d(4), null, null, 0x88),
        photo("p1c", d(1), "s1", "Upper", 0x60, offset = 120_000),
        photo("p1b", d(1), "s1", "Upper", 0x68, offset = 60_000),
        photo("p1a", d(1), "s1", "Upper", 0x78),
    )
    private val weights = listOf(
        WeightEntry("2026-10-30", 83.1),
        WeightEntry("2026-11-06", 82.6),
        WeightEntry("2026-11-07", 82.4),
    )

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
                    Box(Modifier.width(360.dp).height(740.dp).background(Theme.colors.background).testTag("shot")) {
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

    @Test fun grid() {
        val p = photos()
        shot("raster") { PhotosScreen(p, history, Language.DE, today, {}, { _, _ -> }, weights = weights, onMuscles = {}) }
    }

    @Test fun list() {
        val p = photos()
        shot("liste") { PhotosScreen(p, history, Language.DE, today, {}, { _, _ -> }, listView = true, weights = weights, onMuscles = {}) }
    }
}
