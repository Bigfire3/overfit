package app.overfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import app.overfit.domain.Language
import app.overfit.domain.SetValues
import app.overfit.domain.ThemeMode
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.WorkoutSet
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.workout.WorkoutSetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Keyboard "Next" walks weight -> reps -> next set, "Done" on the last field and checking off a set remove the cursor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SetFieldFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private val t = stringsFor(Language.DE)
    private var toggled = 0

    private fun content() {
        compose.setContent {
            OverfitTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalStrings provides t) {
                    val first = remember { FocusRequester() }
                    val second = remember { FocusRequester() }
                    var sets by remember { mutableStateOf(listOf(WorkoutSet("a"), WorkoutSet("b"))) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sets.forEachIndexed { i, set ->
                            WorkoutSetRow(
                                number = i + 1,
                                type = TrackingType.WEIGHT_REPS,
                                unit = WeightUnit.KG,
                                language = Language.DE,
                                set = set,
                                onValues = { v -> sets = sets.map { if (it.id == set.id) it.copy(actual = v) else it } },
                                onToggle = { toggled++; sets = sets.map { if (it.id == set.id) it.copy(completed = !it.completed) else it } },
                                onRemove = {},
                                focusRequester = if (i == 0) first else second,
                                next = if (i == 0) second else null,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun node(description: String) = compose.onNodeWithContentDescription(description)
    private fun nothingFocused() {
        val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        assertTrue("a field still has the cursor: ${focused.map { it.config }}", focused.isEmpty())
    }

    @Test
    fun nextWalksThroughSets_doneOnLastFieldClearsFocus() {
        content()
        node(t.weightOfSet(1)).performClick()
        node(t.weightOfSet(1)).assertIsFocused().performTextInput("1")
        node(t.weightOfSet(1)).performImeAction()
        node(t.repsOfSet(1)).assertIsFocused().performTextInput("11")
        node(t.repsOfSet(1)).performImeAction()
        node(t.weightOfSet(2)).assertIsFocused()
        node(t.weightOfSet(2)).performImeAction()
        node(t.repsOfSet(2)).assertIsFocused()
        node(t.repsOfSet(2)).performImeAction()
        node(t.repsOfSet(2)).assertIsNotFocused()
        nothingFocused()
    }

    @Test
    fun checkingOffASetClearsFocus() {
        content()
        node(t.repsOfSet(1)).performClick()
        node(t.repsOfSet(1)).assertIsFocused().performTextInput("8")
        node(t.completeSet(1)).performClick()
        compose.waitForIdle()
        assertEquals(1, toggled)
        nothingFocused()
    }
}
