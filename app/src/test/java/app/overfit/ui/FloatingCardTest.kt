package app.overfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.overfit.domain.Language
import app.overfit.domain.TemplateSummary
import app.overfit.domain.ThemeMode
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.main.WorkoutRow
import app.overfit.ui.theme.OverfitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Workouts tab: every workout is its own floating card, the whole card is the tap target. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class FloatingCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun everyWorkoutIsItsOwnCardAndOpensOnTap() {
        val opened = mutableListOf<Long>()
        compose.setContent {
            OverfitTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) {
                    Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(12f))) {
                        listOf(TemplateSummary(1, "Push", listOf("Bankdrücken")), TemplateSummary(2, "Pull", listOf("Klimmzüge"))).forEach {
                            WorkoutRow(it, "Noch nie gemacht") { opened += it.id }
                        }
                    }
                }
            }
        }
        compose.onAllNodesWithTag("workout-card").assertCountEquals(2)
        compose.onNodeWithText("Pull").assertIsDisplayed()
        compose.onNodeWithText("Pull").performClick()
        compose.onNodeWithText("Push").performClick()
        assertEquals(listOf(2L, 1L), opened)
    }
}
