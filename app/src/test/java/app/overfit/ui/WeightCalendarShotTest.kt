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
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import app.overfit.data.DiaryPhoto
import app.overfit.data.WorkoutPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.photos.DIARY_GRID_TAG
import app.overfit.ui.photos.DayScreen
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Theme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Evidence screenshots (360 dp, dark) of the body-weight log and the calendar. Runs only with `SCREENSHOT_DIR`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi")
class WeightCalendarShotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 7)
    private fun ms(d: LocalDate) = d.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
    private fun session(id: String, d: LocalDate, name: String) = HistoryRow(id, name, ms(d), ms(d) + 3_600_000L, null, 3, 9, 4200.0, 0)
    private val photos = listOf(DiaryPhoto(WorkoutPhoto("p1", "s1", "p1.jpg", ms(LocalDate(2026, 11, 2)), "/nonexistent/p1.jpg"), "Push"))
    private val history = listOf(
        session("s1", LocalDate(2026, 11, 2), "Push"),
        session("s3", LocalDate(2026, 11, 5), "Pull"),
        session("s2", LocalDate(2026, 10, 28), "Pull").copy(sauna = true),
        session("s0", LocalDate(2026, 10, 20), "Legs"),
    )
    // Eight weeks, roughly every third day, slowly going down.
    private val weights = (0 until 19).map { i ->
        WeightEntry(today.minus(DatePeriod(days = i * 3)).toString(), 82.4 + i * 0.12 + (if (i % 2 == 0) 0.0 else 0.3))
    }

    private fun shot(name: String, language: Language = Language.DE, after: () -> Unit = {}, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
                    Box(Modifier.width(360.dp).height(740.dp).background(Theme.colors.background).testTag("shot")) {
                        app.overfit.ui.components.ConfirmArea(Modifier) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
        after()
        compose.waitForIdle()
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun calendar() = shot("kalender-gewicht-dunkel") {
        PhotosScreen(photos, history, Language.DE, today, {}, { _, _ -> }, weights = weights)
    }

    @Test fun calendarFuture() = shot("kalender-zukunft-heute-dunkel", after = { compose.onNodeWithTag(DIARY_GRID_TAG).performScrollToIndex(11) }) {
        PhotosScreen(photos, history, Language.DE, today, {}, { _, _ -> }, weights = weights)
    }

    @Test fun calendarEmptyWeightEnglish() = shot("kalender-ohne-gewicht-en-dunkel", Language.EN) {
        PhotosScreen(photos, history, Language.EN, today, {}, { _, _ -> }, weights = emptyList())
    }

    @Test fun list() = shot("kalender-liste-gewicht-dunkel") {
        PhotosScreen(photos, history, Language.DE, today, {}, { _, _ -> }, listView = true, weights = weights)
    }

    @Test fun day() = shot("tag-gewicht-dunkel") {
        DayScreen(
            date = today, photos = emptyList(), history = emptyList(), unit = WeightUnit.KG, language = Language.DE,
            onBack = {}, onOpenPhoto = {}, onOpenWorkout = {}, onAddPhoto = {}, onRemovePhoto = {},
            weights = weights,
        )
    }

    @Test fun dayEmpty() = shot("tag-gewicht-leer-dunkel") {
        DayScreen(
            date = today, photos = emptyList(), history = emptyList(), unit = WeightUnit.KG, language = Language.DE,
            onBack = {}, onOpenPhoto = {}, onOpenWorkout = {}, onAddPhoto = {}, onRemovePhoto = {},
            weights = weights.drop(1),
        )
    }
}
