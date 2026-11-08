package app.overfit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import app.overfit.data.DiaryPhoto
import app.overfit.data.WorkoutPhoto
import app.overfit.data.db.HistoryRow
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.photos.DAY_MARKERS_TAG
import app.overfit.ui.photos.DIARY_GRID_TAG
import app.overfit.ui.photos.DIARY_HEADER_TAG
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.photos.SAUNA_MARKER_TAG
import app.overfit.ui.photos.TODAY_BUTTON_TAG
import app.overfit.ui.photos.TODAY_MARKER_TAG
import app.overfit.ui.photos.WEIGHT_MARKER_TAG
import app.overfit.ui.photos.WORKOUT_MARKER_TAG
import app.overfit.ui.theme.OverfitTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** Calendar tab: all day markers share one size and line up, the header is today / muscles / ⋮ with a menu. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class CalendarUniformTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 7)
    private fun d(day: Int) = LocalDate(2026, 11, day)
    private fun ms(d: LocalDate) = d.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
    private fun session(id: String, d: LocalDate, sauna: Boolean = false) =
        HistoryRow(id, "Push", ms(d), ms(d) + 3_600_000L, null, 3, 9, 4200.0, 0).copy(sauna = sauna)
    private fun photo(id: String, d: LocalDate, session: String?) =
        DiaryPhoto(WorkoutPhoto(id, session, "$id.jpg", ms(d), "/nonexistent/$id.jpg"), null)

    // Week 2–8 Nov: 2 training without photo, 3 training with photo, 4 training + sauna, 5 weight, 6 photo only,
    // 7 (today) training + sauna + weight.
    private val history = listOf(session("s7", d(7), sauna = true), session("s4", d(4), sauna = true), session("s3", d(3)), session("s2", d(2)))
    private val photos = listOf(photo("p6", d(6), null), photo("p3", d(3), "s3"))
    private val weights = listOf(WeightEntry("2026-11-05", 82.6), WeightEntry("2026-11-07", 82.4))

    private fun show(photos: List<DiaryPhoto> = this.photos, listView: Boolean = false) = compose.setContent {
        OverfitTheme {
            CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) {
                app.overfit.ui.components.ConfirmArea(androidx.compose.ui.Modifier) {
                    PhotosScreen(photos, history, Language.DE, today, {}, { _, _ -> }, listView = listView, weights = weights, onMuscles = {})
                }
            }
        }
    }

    private val density get() = compose.density.density
    private fun Float.dp() = this / density
    private fun bounds(m: SemanticsMatcher) = compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    /** The calendar cell of [day] (description "Tag 5" or "Tag 5, …"), first match = November (the month on top). */
    private fun cell(day: Int) = SemanticsMatcher("day $day") { n ->
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == "Tag $day" || it.startsWith("Tag $day,") } == true
    }

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue("$what: expected $expected dp, was $actual dp", abs(expected - actual) <= 0.5f)

    @Test fun allMarkersHaveOneSizeAndLineUpInTheWeek() {
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(DAY_MARKERS_TAG, useUnmergedTree = true).fetchSemanticsNodes().size >= 7 }
        val markerTag = SemanticsMatcher("marker") {
            it.config.getOrNull(SemanticsProperties.TestTag) in setOf(WORKOUT_MARKER_TAG, SAUNA_MARKER_TAG, WEIGHT_MARKER_TAG)
        }
        val all = bounds(markerTag)
        assertTrue("markers: ${all.size}", all.size >= 3 + 4 + 2 + 2)
        all.forEach {
            assertNear(8f, it.width.dp(), "marker width")
            assertNear(8f, it.height.dp(), "marker height")
        }
        // The legend (outside the grid) uses the very same markers: dot, ring, dash.
        assertEquals(3, bounds(markerTag and !hasAnyAncestor(hasTestTag(DIARY_GRID_TAG))).size)

        // One marker row per day of the week 2–8 Nov, all at the same height, each centred in its cell.
        val rowCentersY = (2..8).map { day ->
            val cellBox = compose.onAllNodes(cell(day), useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
            val rowMatcher = hasTestTag(DAY_MARKERS_TAG) and hasParent(cell(day))
            val row = compose.onAllNodes(rowMatcher, useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
            assertNear(cellBox.center.x.dp(), row.center.x.dp(), "row centre x, day $day")
            val marks = bounds(markerTag and hasAnyAncestor(rowMatcher))
            if (marks.isNotEmpty()) {
                val union = marks.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
                assertNear(cellBox.center.x.dp(), union.center.x.dp(), "markers centre x, day $day")
                assertNear(row.center.y.dp(), union.center.y.dp(), "markers centre y, day $day")
            }
            row.center.y.dp()
        }
        rowCentersY.forEach { assertNear(rowCentersY[0], it, "row centre y") }
        // Order and count on today: workout, sauna, weight.
        val todayRow = hasTestTag(DAY_MARKERS_TAG) and hasParent(cell(7))
        val xs = listOf(WORKOUT_MARKER_TAG, SAUNA_MARKER_TAG, WEIGHT_MARKER_TAG).map { bounds(hasTestTag(it) and hasAnyAncestor(todayRow)).single().center.x }
        assertEquals(xs.sorted(), xs)
        // Today: the filled day circle.
        val circle = bounds(hasTestTag(TODAY_MARKER_TAG)).single()
        assertNear(22f, circle.width.dp(), "today circle")
    }

    @Test fun headerIconsShareOneSizeAndLine() {
        show()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        // Away from today: today, muscles, ⋮.
        compose.onNodeWithTag(DIARY_GRID_TAG).performScrollToIndex(0)
        compose.onNodeWithTag(TODAY_BUTTON_TAG).assertIsDisplayed()
        val icons = bounds(hasClickAction() and hasAnyAncestor(hasTestTag(DIARY_HEADER_TAG)))
        assertEquals(3, icons.size)
        icons.forEach {
            assertNear(48f, it.width.dp(), "icon width")
            assertNear(48f, it.height.dp(), "icon height")
            assertNear(icons[0].center.y.dp(), it.center.y.dp(), "icon centre y")
        }
    }

    @Test fun menuSwitchesToTheList() {
        show()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Als Liste anzeigen").performClick()
        compose.onNodeWithTag("diary-list").assertIsDisplayed()
        // In the list the menu offers the calendar, comparing is a calendar thing.
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Als Kalender anzeigen").assertIsDisplayed()
        compose.onAllNodesWithText("Fotos vergleichen").assertCountEquals(0)
    }

    @Test fun menuStartsComparingWithTwoPhotos() {
        show()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Fotos vergleichen").performClick()
        compose.onNodeWithText("Abbrechen").assertIsDisplayed()
        compose.onNodeWithText("Wähle zwei Fotos zum Vergleichen.").assertIsDisplayed()
        // Pick both photos: the compare button becomes active; cancel ends the mode.
        compose.onAllNodes(cell(6)).onFirst().performClick()
        compose.onAllNodes(cell(3)).onFirst().performClick()
        compose.onAllNodesWithText("Wähle zwei Fotos zum Vergleichen.").assertCountEquals(0)
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithContentDescription("Weitere Optionen").assertIsDisplayed()
    }

    @Test fun noCompareEntryWithOnePhoto() {
        show(photos = photos.take(1))
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Als Liste anzeigen").assertIsDisplayed()
        compose.onAllNodesWithText("Fotos vergleichen").assertCountEquals(0)
    }
}
