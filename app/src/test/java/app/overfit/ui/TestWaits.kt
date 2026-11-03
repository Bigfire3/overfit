package app.overfit.ui

import androidx.compose.ui.test.isRoot
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
