package app.overfit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.click
import app.overfit.ui.coach.CoachStoryScreen
import app.overfit.ui.coach.STORY_AUTO_MS
import app.overfit.ui.theme.OverfitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The current story bar fills from left to right over the auto-advance time; then the next page starts empty. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class CoachStoryProgressTest {
    @get:Rule
    val compose = createComposeRule()


    private fun fraction(i: Int): Float {
        val track = compose.onNodeWithTag("story:bar:$i").getUnclippedBoundsInRoot()
        val fill = compose.onNodeWithTag("story:fill:$i").getUnclippedBoundsInRoot()
        return (fill.right - fill.left).value / (track.right - track.left).value
    }

    private fun launch() {
        compose.mainClock.autoAdvance = false
        compose.setContent { OverfitTheme { CoachStoryScreen(onClose = {}, onTarget = {}) } }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun barRunsAndNextPageStartsAfterTheTime() {
        launch()
        compose.onNodeWithTag("story:title").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(STORY_AUTO_MS / 2)
        assertEquals(0.5f, fraction(0), 0.05f)
        assertEquals(0f, fraction(1), 0.001f)
        compose.mainClock.advanceTimeBy(STORY_AUTO_MS / 2 + 200)
        // Page 2: first bar full, second running again from the left.
        assertEquals(1f, fraction(0), 0.001f)
        assertEquals(0f, fraction(1), 0.1f)
        compose.mainClock.advanceTimeBy(STORY_AUTO_MS / 2)
        assertEquals(0.5f, fraction(1), 0.1f)
        assertEquals(0f, fraction(2), 0.001f)
    }

    @Test
    fun tapRestartsTheBar() {
        launch()
        compose.mainClock.advanceTimeBy(STORY_AUTO_MS / 2)
        compose.onNodeWithTag("story").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.85f, height * 0.5f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1f, fraction(0), 0.001f)
        assertEquals(0.02f, fraction(1), 0.05f)
    }

    @Test
    fun lastPageRunsThroughThenCloses() {
        var closed = false
        compose.mainClock.autoAdvance = false
        compose.setContent { OverfitTheme { CoachStoryScreen(onClose = { closed = true }, onTarget = {}) } }
        compose.mainClock.advanceTimeByFrame()
        repeat(3) { compose.mainClock.advanceTimeBy(STORY_AUTO_MS + 200) }
        // Last page starts empty, is half full after half the time and has not closed yet.
        assertEquals(0f, fraction(3), 0.1f)
        compose.mainClock.advanceTimeBy(STORY_AUTO_MS / 2)
        assertEquals(0.5f, fraction(3), 0.1f)
        assertEquals(false, closed)
        compose.mainClock.advanceTimeBy(STORY_AUTO_MS / 2 + 300)
        assertEquals(true, closed)
    }

    @Test
    fun noVisiblePageCounter() {
        launch()
        compose.onAllNodesWithText("Seite 1 von 4").assertCountEquals(0)
    }
}
