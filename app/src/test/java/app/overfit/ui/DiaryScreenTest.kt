package app.overfit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithTag
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
import app.overfit.ui.photos.DiaryViewPref
import app.overfit.data.InMemoryKeyValueStore
import androidx.compose.runtime.remember
import app.overfit.ui.photos.DayScreen
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import app.overfit.ui.photos.SAUNA_MARKER_TAG
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
        OverfitTheme(ThemeMode.LIGHT) { CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) { app.overfit.ui.components.ConfirmArea(androidx.compose.ui.Modifier) { content() } } }
    }

    @Test fun gridShowsMonthsStatsAndOpensTappedDay() {
        val days = mutableListOf<LocalDate>()
        show {
            PhotosScreen(photos, history, Language.DE, today, onDay = { days += it }, onCompare = { _, _ -> })
        }
        compose.onNodeWithText("Kalender").assertIsDisplayed()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithText("3 Trainings").assertIsDisplayed()
        compose.onNodeWithText("3 Fotos").assertIsDisplayed()
        compose.onNodeWithText("Mo").assertIsDisplayed()
        // Every day up to today opens the day view: with photos, training only, and empty (to add a photo).
        compose.onNodeWithContentDescription("Tag 2, 2 Fotos").performClick()
        // October has a day 3 and 4 too; November (the newest month) comes first.
        compose.onAllNodesWithContentDescription("Tag 3").onFirst().performClick()
        compose.onNodeWithContentDescription("Tag 20, Training").performScrollTo().performClick()
        assertEquals(listOf(LocalDate(2026, 11, 2), LocalDate(2026, 11, 3), LocalDate(2026, 10, 20)), days)
        // Days after today cannot be tapped.
        compose.onAllNodesWithContentDescription("Tag 4").onFirst().assertHasNoClickAction()
    }

    /** Like the app's route: the remembered choice comes from the key-value store and is written back on each switch. */
    private fun showWithPref(kv: InMemoryKeyValueStore, days: MutableList<LocalDate> = mutableListOf()) = show {
        val pref = remember { DiaryViewPref(kv) }
        PhotosScreen(
            photos, history, Language.DE, today, onDay = { days += it }, onCompare = { _, _ -> },
            listView = pref.list, onListViewChange = { pref.list = it },
        )
    }

    @Test fun iconSwitchesBetweenCalendarAndListAndBack() {
        showWithPref(InMemoryKeyValueStore())
        // Calendar first; the icon offers the list.
        compose.onNodeWithText("Mo").assertIsDisplayed()
        compose.onAllNodesWithTag("diary-list").assertCountEquals(0)
        compose.onNodeWithContentDescription("Als Liste anzeigen").performClick()
        compose.onNodeWithTag("diary-list").assertIsDisplayed()
        compose.onAllNodesWithTag("diary-list-row").assertCountEquals(3)
        // Newest first, grouped by month, with workout name and short numbers.
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithText("Push").assertIsDisplayed()
        compose.onAllNodesWithText("1:00:00 · 9 Sätze · 4.200 kg", substring = true).assertCountEquals(3)
        // The icon now offers the calendar.
        compose.onNodeWithContentDescription("Als Kalender anzeigen").performClick()
        compose.onNodeWithText("Mo").assertIsDisplayed()
        compose.onAllNodesWithTag("diary-list").assertCountEquals(0)
    }

    @Test fun chosenViewIsRemembered() {
        val kv = InMemoryKeyValueStore()
        showWithPref(kv)
        compose.onNodeWithContentDescription("Als Liste anzeigen").performClick()
        assertEquals(true, DiaryViewPref(kv).list)
        // A fresh screen (new start) comes up in the list.
        val kv2 = InMemoryKeyValueStore().apply { put("diary.view", "list") }
        assertEquals(true, DiaryViewPref(kv2).list)
        compose.onNodeWithContentDescription("Als Kalender anzeigen").performClick()
        assertEquals(false, DiaryViewPref(kv).list)
    }

    @Test fun tapInTheListOpensTheDay() {
        val days = mutableListOf<LocalDate>()
        showWithPref(InMemoryKeyValueStore().apply { put("diary.view", "list") }, days)
        compose.onNodeWithText("Push").performClick()
        compose.onNodeWithText("Legs").performScrollTo().performClick()
        assertEquals(listOf(LocalDate(2026, 11, 2), LocalDate(2026, 10, 20)), days)
    }

    @Test fun listShowsPhotoOnlyDaysAndSumsTwoWorkoutsOfOneDay() {
        val two = listOf(session("a", LocalDate(2026, 11, 2), "Push"), session("b", LocalDate(2026, 11, 2), "Core"))
        show { PhotosScreen(listOf(photo("p9", LocalDate(2026, 10, 5), "x", "x")), two, Language.DE, today, {}, { _, _ -> }, listView = true) }
        compose.onAllNodesWithTag("diary-list-row").assertCountEquals(2)
        compose.onNodeWithText("Push, Core").assertIsDisplayed()
        compose.onNodeWithText("2:00:00 · 18 Sätze · 8.400 kg", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("1 Foto").assertCountEquals(2)
    }

    @Test fun emptyListIsFriendly() {
        show { PhotosScreen(emptyList(), emptyList(), Language.DE, today, {}, { _, _ -> }, listView = true) }
        compose.onNodeWithText("Noch nichts in deiner Liste.").assertIsDisplayed()
        compose.onAllNodesWithTag("diary-list-row").assertCountEquals(0)
    }

    @Test fun emptyStateExplainsTheDayTap() {
        show { PhotosScreen(emptyList(), emptyList(), Language.DE, today, {}, { _, _ -> }) }
        compose.onNodeWithText("Tippe auf einen Tag, um Fotos hinzuzufügen oder dein Training anzusehen. Fotos bleiben privat in der App.").assertIsDisplayed()
        // The calendar is there without a single photo.
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithText("Mo").assertIsDisplayed()
    }

    @Test fun dayViewListsTrainingsAndPhotosAndRemovesAPhotoWithConfirmation() {
        var workout: String? = null
        var opened: String? = null
        var removed: String? = null
        show {
            DayScreen(
                LocalDate(2026, 11, 2), photos, history, WeightUnit.KG, Language.DE,
                onBack = {}, onOpenPhoto = { opened = it }, onOpenWorkout = { workout = it },
                onAddPhoto = {}, onRemovePhoto = { removed = it },
            )
        }
        compose.onNodeWithText("TRAININGS").assertIsDisplayed()
        compose.onNodeWithText("Push").performClick()
        assertEquals("s1", workout)
        // Two photos of that day, oldest first; a tap opens the viewer.
        compose.onNodeWithContentDescription("Foto 1").performScrollTo().performClick()
        assertEquals("p2", opened)
        // Remove, two taps: the first arms the "x" (red, screen readers hear "zum Bestätigen erneut tippen"),
        // a tap elsewhere disarms it and keeps the photo.
        compose.onAllNodesWithContentDescription("Foto entfernen")[1].performScrollTo().performClick()
        compose.onNodeWithContentDescription("Foto entfernen – zum Bestätigen erneut tippen").assertExists()
        compose.onNodeWithText("TRAININGS").performClick()
        compose.onAllNodesWithTag("confirm:armed").assertCountEquals(0)
        assertEquals(null, removed)
        compose.onAllNodesWithContentDescription("Foto entfernen")[1].performScrollTo().performClick()
        compose.onNodeWithTag("confirm:armed").performClick()
        assertEquals("p3", removed)
        compose.onNodeWithText("Aus Fotos wählen").assertExists()
    }

    @Test fun dayWithoutTrainingStillOffersPhotos() {
        show {
            DayScreen(
                LocalDate(2026, 11, 3), photos, history, WeightUnit.KG, Language.DE,
                onBack = {}, onOpenPhoto = {}, onOpenWorkout = {}, onAddPhoto = {}, onRemovePhoto = {},
            )
        }
        compose.onNodeWithText("An diesem Tag kein Training.").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Fotos an diesem Tag.").assertIsDisplayed()
        compose.onNodeWithText("Aus Fotos wählen").assertIsDisplayed()
    }

    @Test fun calendarShowsTrainingDaysWithoutAnyPhoto() {
        show { PhotosScreen(emptyList(), listOf(session("s1", LocalDate(2026, 11, 2), "Push")), Language.DE, today, {}, { _, _ -> }) }
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tag 2, Training").assertIsDisplayed()
    }

    @Test fun saunaDaysGetARingAndALegend() {
        val sauna = listOf(
            session("s1", LocalDate(2026, 11, 2), "Push").copy(sauna = true), // with photo
            session("s0", LocalDate(2026, 10, 30), "Legs").copy(sauna = true), // training only
            session("s9", LocalDate(2026, 11, 1), "Pull"), // no sauna
        )
        show { PhotosScreen(photos.take(2), sauna, Language.DE, today, {}, { _, _ -> }) }
        val ring = hasAnyDescendant(hasTestTag(SAUNA_MARKER_TAG))
        // Legend: filled dot = workout, ring = sauna.
        compose.onNodeWithText("Sauna").assertIsDisplayed()
        // Photo day: ring next to the workout dot; training-only day: ring below the dot; no sauna: no ring.
        compose.onNode(hasContentDescription("Tag 2, 2 Fotos, Sauna") and ring, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Tag 1, Training") and !ring, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Tag 30, Training, Sauna") and ring, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun noLegendWithoutSauna() {
        show { PhotosScreen(emptyList(), listOf(session("s1", LocalDate(2026, 11, 2), "Push")), Language.DE, today, {}, { _, _ -> }) }
        compose.onAllNodesWithTag(SAUNA_MARKER_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Sauna").assertCountEquals(0)
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
        compose.onNodeWithContentDescription("Foto löschen").performClick() // bin icon top right
        assertEquals(null, deleted)
        compose.onNodeWithTag("confirm:armed").performClick()
        assertEquals("p2", deleted)
    }

    @Test fun viewerOfAPhotoWithoutTrainingHasNoWorkoutLink() {
        val alone = listOf(DiaryPhoto(WorkoutPhoto("p9", null, "p9.jpg", ms(LocalDate(2026, 11, 1)), "/nonexistent/p9.jpg"), null))
        show {
            PhotoViewerScreen(
                alone, history, photoId = "p9", language = Language.DE, unit = WeightUnit.KG,
                platform = NoopPlatformShare(), loadSummary = { null }, loadJpeg = { null },
                onBack = {}, onWorkout = {}, onDelete = {},
            )
        }
        compose.onNodeWithText("1. Nov. 2026", substring = true).assertExists()
        compose.onAllNodesWithText("Training ansehen").assertCountEquals(0)
    }
}
