package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.overfit.data.db.HistoryRow
import app.overfit.domain.DiaryCalendar
import app.overfit.domain.Language
import app.overfit.domain.Muscle
import app.overfit.domain.MusclePeriod
import app.overfit.domain.MuscleSetRow
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.muscles.MuscleMapSheetContent
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.theme.OverfitTheme
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

/**
 * Evidence screenshots (360 dp, dark) of the calendar's muscle sheet: with a week of training and empty. The sheet
 * normally sits in a dialog window; here it is drawn over the calendar with the same dimming. Runs only with `SCREENSHOT_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class MuscleMapShotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 7)
    private fun ms(d: LocalDate) = d.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
    private val history = listOf(
        HistoryRow("s1", "Push", ms(LocalDate(2026, 11, 3)), ms(LocalDate(2026, 11, 3)) + 3_600_000L, null, 4, 14, 5200.0, 0),
        HistoryRow("s2", "Pull", ms(LocalDate(2026, 11, 5)), ms(LocalDate(2026, 11, 5)) + 3_600_000L, null, 4, 13, 4800.0, 0),
    )
    private val week = listOf(
        // Push
        MuscleSetRow(ms(LocalDate(2026, 11, 3)), listOf(Muscle.CHEST), listOf(Muscle.TRICEPS, Muscle.SHOULDERS), 4),
        MuscleSetRow(ms(LocalDate(2026, 11, 3)), listOf(Muscle.SHOULDERS), listOf(Muscle.TRICEPS), 3),
        MuscleSetRow(ms(LocalDate(2026, 11, 3)), listOf(Muscle.TRICEPS), listOf(Muscle.CHEST), 3),
        MuscleSetRow(ms(LocalDate(2026, 11, 3)), listOf(Muscle.CORE), emptyList(), 3),
        // Pull
        MuscleSetRow(ms(LocalDate(2026, 11, 5)), listOf(Muscle.LATS), listOf(Muscle.BICEPS, Muscle.UPPER_BACK), 4),
        MuscleSetRow(ms(LocalDate(2026, 11, 5)), listOf(Muscle.UPPER_BACK), listOf(Muscle.BICEPS, Muscle.FOREARMS), 3),
        MuscleSetRow(ms(LocalDate(2026, 11, 5)), listOf(Muscle.BICEPS), listOf(Muscle.FOREARMS), 3),
        MuscleSetRow(ms(LocalDate(2026, 11, 5)), listOf(Muscle.LOWER_BACK), listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), 3),
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) {
                    Box(Modifier.width(360.dp).height(780.dp).background(Theme.colors.background).testTag("shot")) { content() }
                }
            }
        }
        compose.waitForIdle()
        val out = File(dir!!, "muskel-heatmap").also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Composable
    private fun SheetOverCalendar(rows: List<MuscleSetRow>, history: List<HistoryRow>) {
        PhotosScreen(emptyList(), history, Language.DE, today, {}, { _, _ -> }, onMuscles = {})
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.BottomCenter) {
            MuscleMapSheetContent(rows, MusclePeriod.WEEK, today, DiaryCalendar.weekStart(Language.DE), {}, {}, Modifier.heightIn(max = 718.dp))
        }
    }

    @Test fun calendarTitle() = shot("00-kalender-titel-dunkel-360") {
        PhotosScreen(emptyList(), history, Language.DE, today, {}, { _, _ -> }, onMuscles = {})
    }

    @Test fun sheetWithData() = shot("01-sheet-woche-dunkel-360") { SheetOverCalendar(week, history) }

    @Test fun sheetEmpty() = shot("02-sheet-leer-dunkel-360") { SheetOverCalendar(emptyList(), emptyList()) }
}
