package app.overfit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.photos.DIARY_GRID_TAG
import app.overfit.ui.photos.DayScreen
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.photos.TODAY_BUTTON_TAG
import app.overfit.ui.photos.WEIGHT_CARD_TAG
import app.overfit.ui.photos.WEIGHT_FIELD_TAG
import app.overfit.ui.photos.WEIGHT_MARKER_TAG
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

/** Body weight in the calendar tab, future months and the "Today" button. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class WeightCalendarTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 7)
    private fun ms(d: LocalDate) = d.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
    private val history = listOf(
        HistoryRow("s1", "Push", ms(LocalDate(2026, 11, 2)), ms(LocalDate(2026, 11, 2)) + 3_600_000L, null, 3, 9, 4200.0, 0),
        HistoryRow("s0", "Legs", ms(LocalDate(2026, 6, 2)), ms(LocalDate(2026, 6, 2)) + 3_600_000L, null, 3, 9, 4200.0, 0),
    )
    private val weights = listOf(
        WeightEntry("2026-10-24", 83.4),
        WeightEntry("2026-10-31", 83.0),
        WeightEntry("2026-11-07", 82.4),
    )

    private fun show(language: Language = Language.DE, content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        OverfitTheme { CompositionLocalProvider(LocalStrings provides stringsFor(language)) { app.overfit.ui.components.ConfirmArea(androidx.compose.ui.Modifier) { content() } } }
    }

    @Test fun cardShowsLatestWeightWeekChangeAndOpensToday() {
        var taps = 0
        show { PhotosScreen(emptyList(), history, Language.DE, today, {}, { _, _ -> }, weights = weights, onWeightToday = { taps++ }) }
        compose.onNodeWithContentDescription("Körpergewicht 82,4 kg, Heute, \u22120,6 kg zur Vorwoche", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("weight-sparkline", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(WEIGHT_CARD_TAG).performClick()
        assertEquals(1, taps)
    }

    @Test fun emptyCardInvitesToLog() {
        show(Language.EN) { PhotosScreen(emptyList(), emptyList(), Language.EN, today, {}, { _, _ -> }, weights = emptyList()) }
        compose.onNodeWithText("Log your weight").assertIsDisplayed()
    }

    @Test fun weightDaysGetADashAndTheLegendNamesIt() {
        show { PhotosScreen(emptyList(), history, Language.DE, today, {}, { _, _ -> }, weights = weights) }
        compose.onNodeWithText("Gewicht").assertIsDisplayed()
        // Three weight days; 7 Nov is in the visible month and carries the dash.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(WEIGHT_MARKER_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Tag 7, Gewicht 82,4 kg").assertExists()
    }

    @Test fun listShowsTheWeightPerDay() {
        show { PhotosScreen(emptyList(), history, Language.DE, today, {}, { _, _ -> }, listView = true, weights = weights) }
        // 7 Nov, 31 Oct and 24 Oct have only a weight: title "Körpergewicht", value below.
        compose.onAllNodesWithText("Körpergewicht").assertCountEquals(3)
        compose.onNodeWithText("82,4 kg").assertIsDisplayed()
        compose.onNodeWithText("Push").assertIsDisplayed()
    }

    @Test fun gridStartsOnTodayScrollsTwelveMonthsAheadAndTodayJumpsBack() {
        show { PhotosScreen(emptyList(), history, Language.DE, today, {}, { _, _ -> }, weights = weights) }
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onAllNodesWithTag(TODAY_BUTTON_TAG).assertCountEquals(0) // already there
        // Future days are not tappable, today is.
        compose.onNodeWithContentDescription("Tag 20").assertHasNoClickAction()
        compose.onNodeWithContentDescription("Tag 7, Gewicht 82,4 kg").assertHasClickAction()

        compose.onNodeWithTag(DIARY_GRID_TAG).performScrollToIndex(0)
        compose.onNodeWithText("November 2027").assertIsDisplayed()
        compose.onNodeWithTag(TODAY_BUTTON_TAG).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("November 2026").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(TODAY_BUTTON_TAG).fetchSemanticsNodes().isEmpty() }

        // Into the past works as before; "Today" comes back from there too.
        compose.onNodeWithTag(DIARY_GRID_TAG).performScrollToIndex(16)
        compose.onNodeWithText("Juni 2026").assertIsDisplayed()
        compose.onNodeWithTag(TODAY_BUTTON_TAG).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(TODAY_BUTTON_TAG).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("November 2026").assertIsDisplayed()
    }

    @Test fun todayInTheListJumpsToTheTop() {
        val many = (0 until 40).map { WeightEntry(today.minus(DatePeriod(days = it * 3)).toString(), 80.0 + it * 0.1) }
        show { PhotosScreen(emptyList(), emptyList(), Language.DE, today, {}, { _, _ -> }, listView = true, weights = many) }
        compose.onAllNodesWithTag(TODAY_BUTTON_TAG).assertCountEquals(0)
        compose.onNodeWithTag("diary-list").performScrollToIndex(3)
        compose.onNodeWithTag(TODAY_BUTTON_TAG).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(TODAY_BUTTON_TAG).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("November 2026").assertIsDisplayed()
    }

    @Test fun dayViewEntersReplacesAndDeletesTheWeight() {
        val log = mutableListOf<String>()
        show {
            var list by remember { mutableStateOf(listOf(WeightEntry("2026-10-31", 83.0))) }
            DayScreen(
                date = today, photos = emptyList(), history = emptyList(), unit = WeightUnit.KG, language = Language.DE,
                onBack = {}, onOpenPhoto = {}, onOpenWorkout = {}, onAddPhoto = {}, onRemovePhoto = {},
                weights = list,
                onSetWeight = { kg -> log += "set $kg"; list = list.filter { it.date != "2026-11-07" } + WeightEntry("2026-11-07", kg) },
                onDeleteWeight = { log += "delete"; list = list.filter { it.date != "2026-11-07" } },
            )
        }
        // Previous value as grey placeholder, no trash without an entry.
        compose.onNodeWithText("83").assertExists()
        compose.onAllNodesWithContentDescription("Gewicht löschen").assertCountEquals(0)
        compose.onNodeWithTag(WEIGHT_FIELD_TAG).performTextInput("82,4")
        assertEquals("set 82.4", log.last())
        // A new value replaces the entry of the day.
        compose.onNodeWithTag(WEIGHT_FIELD_TAG).performTextReplacement("81,9")
        assertEquals("set 81.9", log.last())
        assertEquals(listOf("set 82.4", "set 81.9"), log)

        // Delete: first tap arms (red), second deletes.
        compose.onNodeWithContentDescription("Gewicht löschen").performClick()
        assertEquals(false, log.contains("delete"))
        compose.onNodeWithContentDescription("Gewicht löschen", substring = true).performClick()
        compose.waitForIdle()
        assertEquals("delete", log.last())
        compose.onAllNodesWithContentDescription("Gewicht löschen").assertCountEquals(0)
    }

    @Test fun poundsAreNotOverwrittenWhileTyping() {
        val log = mutableListOf<Double>()
        show {
            var list by remember { mutableStateOf(emptyList<WeightEntry>()) }
            DayScreen(
                date = today, photos = emptyList(), history = emptyList(), unit = WeightUnit.LB, language = Language.DE,
                onBack = {}, onOpenPhoto = {}, onOpenWorkout = {}, onAddPhoto = {}, onRemovePhoto = {},
                weights = list,
                // Writes the value back like the database flow does.
                onSetWeight = { kg -> log += kg; list = listOf(WeightEntry("2026-11-07", kg)) },
            )
        }
        val field = compose.onNodeWithTag(WEIGHT_FIELD_TAG)
        field.performClick()
        "180,5".forEachIndexed { i, _ -> field.performTextReplacement("180,5".substring(0, i + 1)); compose.waitForIdle() }
        field.assertTextEquals("180,5")
        assertEquals(81.87, log.last(), 0.001)
        // Leaving the field shows the stored value in lb again: unchanged.
        compose.onNodeWithText("Körpergewicht".uppercase()).performClick()
        compose.waitForIdle()
        field.assertTextEquals("180,5")
    }

    @Test fun headerShowsTodayMusclesAndMenuOnly() {
        fun photo(id: String, d: LocalDate) = app.overfit.data.DiaryPhoto(app.overfit.data.WorkoutPhoto(id, null, "$id.jpg", ms(d), "/nonexistent/$id.jpg"), null)
        val photos = listOf(photo("a", LocalDate(2026, 11, 2)), photo("b", LocalDate(2026, 10, 2)))
        show { PhotosScreen(photos, history, Language.DE, today, {}, { _, _ -> }, weights = weights, onMuscles = {}) }
        // No separate compare or view icon any more: both live in the ⋮ menu, also while "today" shows.
        compose.onNodeWithTag(DIARY_GRID_TAG).performScrollToIndex(0)
        compose.onNodeWithTag(TODAY_BUTTON_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription(stringsFor(Language.DE).muscles.open).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Vergleichen").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Als Liste anzeigen").assertCountEquals(0)
        compose.onNodeWithContentDescription("Weitere Optionen").assertIsDisplayed().performClick()
        compose.onNodeWithText("Fotos vergleichen").assertIsDisplayed()
        compose.onNodeWithText("Als Liste anzeigen").assertIsDisplayed()
    }
}

