package app.overfit.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Waits until [condition] on app state or data holds. A bare state check inside `waitUntil` does not pump
 * the main looper under Robolectric, so work posted to `Dispatchers.Main` (WorkoutController, viewModelScope,
 * Room results) never runs and the wait times out. Fetching the semantics tree first syncs the UI and the
 * looper on every poll, the same way text-based waits do.
 */
fun ComposeTestRule.waitUntilState(timeoutMillis: Long = 20_000, condition: () -> Boolean) = waitUntil(timeoutMillis) {
    onAllNodes(isRoot()).fetchSemanticsNodes()
    condition()
}

/** Waits until the Training tab (start screen) is shown, recognised by its "new workout" button. */
fun ComposeTestRule.waitForTrainingTab(newWorkoutLabel: String = "Neues Workout", timeoutMillis: Long = 20_000) = waitUntil(timeoutMillis) {
    onAllNodes(androidx.compose.ui.test.hasContentDescription(newWorkoutLabel)).fetchSemanticsNodes().isNotEmpty()
}

/** Today in the system zone: tests that tap a calendar day use it, so the day of a just-finished training is not "in the future". */
fun realToday(): kotlinx.datetime.LocalDate =
    kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date

/**
 * Calendar tab → tap the day [dayOfMonth] (content description "Tag 3" or "Tag 3, …") → day view.
 * Waits until the day view shows its "Trainings" section (section titles are upper case).
 */
fun ComposeTestRule.openCalendarDay(dayOfMonth: Int, dayWord: String = "Tag", sectionTitle: String = "Trainings") {
    waitUntil(20_000) { onAllNodes(hasTestTag("tab:PHOTOS")).fetchSemanticsNodes().isNotEmpty() }
    onNode(hasTestTag("tab:PHOTOS")).performClick()
    val matcher = SemanticsMatcher("day $dayOfMonth") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it == "$dayWord $dayOfMonth" || it.startsWith("$dayWord $dayOfMonth,") }
    }
    waitUntil(20_000) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    // The tap blocker of a screen change (AppScaffold, "nav-transition") must be gone, or the tap on the day is swallowed.
    waitUntil(20_000) { onAllNodes(hasTestTag("nav-transition")).fetchSemanticsNodes().isEmpty() }
    // Newest month first: the current month holds the first match.
    onAllNodes(matcher).onFirst().performScrollTo().performClick()
    waitUntil(20_000) { onAllNodes(hasText(sectionTitle, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
}
