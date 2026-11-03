package app.overfit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.assertIsDisplayed
import app.overfit.data.DiaryPhoto
import app.overfit.data.WorkoutPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.ThemeMode
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.share.NoopPlatformShare
import app.overfit.ui.photos.PhotoViewerScreen
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.theme.OverfitTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BeReal-style diary: calendar grid per month and the full-screen viewer with day-to-day swipes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DiaryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 3)
    private fun ms(d: LocalDate) = d.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L

    private fun photo(id: String, d: LocalDate, session: String, name: String, offset: Long = 0) =
        DiaryPhoto(WorkoutPhoto(id, session, "$id.jpg", ms(d) + offset, "/nonexistent/$id.jpg"), name)

    private fun session(id: String, d: LocalDate, name: String) =
        HistoryRow(id, name, ms(d), ms(d) + 3_600_000L, null, 3, 9, 4200.0, 0)

    // 2 Nov: two photos of "Push"; 28 Oct: one photo "Pull"; 20 Oct: training without photo.
    private val photos = listOf(
        photo("p3", LocalDate(2026, 11, 2), "s1", "Push", offset = 60_000),
        photo("p2", LocalDate(2026, 11, 2), "s1", "Push"),
        photo("p1", LocalDate(2026, 10, 28), "s2", "Pull"),
    )
    private val history = listOf(
        session("s1", LocalDate(2026, 11, 2), "Push"),
        session("s2", LocalDate(2026, 10, 28), "Pull"),
        session("s0", LocalDate(2026, 10, 20), "Legs"),
    )

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        OverfitTheme(ThemeMode.LIGHT) { CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) { content() } }
    }

    @Test fun gridShowsMonthsStatsAndOpensDayWithPhoto() {
        var opened: String? = null
        var workout: String? = null
        show {
            PhotosScreen(photos, history, Language.DE, today, onOpen = { opened = it }, onWorkout = { workout = it }, onCompare = { _, _ -> })
        }
        compose.onNodeWithText("Foto-Tagebuch").assertIsDisplayed()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithText("3 Trainings").assertIsDisplayed()
        compose.onNodeWithText("3 Fotos").assertIsDisplayed()
        compose.onNodeWithText("Mo").assertIsDisplayed()
        // Day with two photos opens the viewer at its first (oldest) photo; the badge shows 2.
        compose.onNodeWithContentDescription("Tag 2, 2 Fotos").performClick()
        assertEquals("p2", opened)
        // Training without photo opens the workout.
        compose.onNodeWithContentDescription("Tag 20, Training").performScrollTo().performClick()
        assertEquals("s0", workout)
    }

    @Test fun emptyStateExplainsPhotoAfterWorkout() {
        show { PhotosScreen(emptyList(), emptyList(), Language.DE, today, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("Beim Beenden eines Trainings kannst du ein Foto machen. Es bleibt privat in der App.").assertIsDisplayed()
    }

    @Test fun viewerSwipesBetweenPhotoDaysAndDeletesWithConfirmation() {
        var deleted: String? = null
        var workout: String? = null
        show {
            PhotoViewerScreen(
                photos, history, photoId = "p1", language = Language.DE, unit = WeightUnit.KG,
                platform = NoopPlatformShare(), loadSummary = { null }, loadJpeg = { null },
                onBack = {}, onWorkout = { workout = it }, onDelete = { deleted = it },
            )
        }
        // Starts on the older day (28 Oct, "Pull"); swiping left moves to the next photo day.
        compose.onNodeWithText("Pull").assertIsDisplayed()
        compose.onNodeWithText("Dauer 60:00 · Volumen 4.200 kg", substring = true).assertIsDisplayed()
                compose.onNodeWithText("Training ansehen").performClick()
        assertEquals("s2", workout)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Push").assertIsDisplayed()
        // Two photos on that day: page dots/description of the vertical pager.
        compose.onNodeWithContentDescription("Foto 1 von 2").assertIsDisplayed()
        compose.onNodeWithText("Foto löschen").performClick()
        assertEquals(null, deleted)
        compose.onNodeWithText("Wirklich löschen? Nochmal tippen.").performClick()
        assertEquals("p2", deleted)
    }
}
