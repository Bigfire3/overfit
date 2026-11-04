package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.overfit.data.DiaryPhoto
import app.overfit.data.WorkoutPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.TemplateSummary
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Title
import app.overfit.ui.main.WorkoutRow
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Evidence screenshots (360 dp, light/dark): workouts as cards, calendar tab as grid and list. Runs only with `SCREENSHOT_DIR`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class CalendarListShotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 3)
    private fun ms(d: LocalDate) = d.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
    private fun session(id: String, d: LocalDate, name: String) = HistoryRow(id, name, ms(d), ms(d) + 3_600_000L, null, 3, 9, 4200.0, 0)
    private val photos = listOf(DiaryPhoto(WorkoutPhoto("p1", "s1", "p1.jpg", ms(LocalDate(2026, 11, 2)), "/nonexistent/p1.jpg"), "Push"))
    private val history = listOf(
        session("s1", LocalDate(2026, 11, 2), "Push"),
        session("s2", LocalDate(2026, 10, 28), "Pull").copy(sauna = true),
        session("s0", LocalDate(2026, 10, 20), "Legs"),
    )

    private fun shot(language: Language, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
                    Box(Modifier.width(360.dp).height(640.dp).background(Theme.colors.background).testTag("shot")) { content() }
                }
            }
        }
        compose.waitForIdle()
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val workouts = listOf(
        TemplateSummary(1, "Push", listOf("Bankdrücken", "Schulterdrücken", "Dips")),
        TemplateSummary(2, "Pull", listOf("Klimmzüge", "Rudern")),
        TemplateSummary(3, "Legs", listOf("Kniebeuge", "Beinpresse", "Wadenheben")),
    )

    @androidx.compose.runtime.Composable
    private fun WorkoutsTab(language: Language) {
        Screen(scroll = true) {
            Title(stringsFor(language).navStart)
            // WORKOUT_LIST
            Column(Modifier.fillMaxWidth().padding(top = Space.l), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.m)) {
                workouts.forEach { WorkoutRow(it, "Vor 3 Tagen") {} }
            }
        }
    }

    @Test fun workoutsDark() = shot(Language.DE, "workouts-dunkel") { WorkoutsTab(Language.DE) }
    @Test fun calendarDark() = shot(Language.DE, "kalender-dunkel") { PhotosScreen(photos, history, Language.DE, today, {}, { _, _ -> }) }
    @Test fun listDark() = shot(Language.DE, "kalender-liste-dunkel") { PhotosScreen(photos, history, Language.DE, today, {}, { _, _ -> }, listView = true) }
}
