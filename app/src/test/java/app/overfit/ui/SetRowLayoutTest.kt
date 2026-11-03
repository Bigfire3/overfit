package app.overfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width as modWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.overfit.domain.Language
import app.overfit.domain.ThemeMode
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.SetField
import app.overfit.ui.components.SetRowSurface
import app.overfit.ui.components.Text
import app.overfit.ui.theme.OverfitTheme
import androidx.compose.foundation.layout.size
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Set row on a narrow phone (360 dp): one calm line, values fully readable, no wrapped placeholder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SetRowLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun check(language: Language, weight: String, reps: String, time: String?) {
        val t = stringsFor(language)
        compose.setContent {
            OverfitTheme(ThemeMode.DARK) {
                androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides t) {
                    Box(Modifier.modWidth(360.dp)) {
                        SetRowSurface("x", {}, Modifier.testTag("row")) {
                            Text("1", Modifier.modWidth(24.dp))
                            SetField(weight, {}, "kg", "w", Modifier.weight(1f), placeholder = "–")
                            if (time == null) SetField(reps, {}, t.repsUnit, "r", Modifier.weight(1f), placeholder = "–")
                            else SetField(time, {}, t.secondsUnit, "s", Modifier.weight(1f), placeholder = "–")
                            Box(Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("w").assertTextContains(weight)
        compose.onNodeWithTag("row").getBoundsInRoot().let {
            assertTrue("row too tall: ${it.height}", it.height <= 72.dp)
            assertTrue("row too wide: ${it.width}", it.width <= 360.dp)
        }
        // The value field is wide enough for "102.5" (about 5 digits at title size).
        assertTrue(compose.onNodeWithContentDescription("w").getBoundsInRoot().width >= 52.dp)
    }

    @Test fun german_weightAndReps() = check(Language.DE, "102,5", "12", null)
    @Test fun english_weightAndReps() = check(Language.EN, "100", "8", null)
    @Test fun english_weightAndTime() = check(Language.EN, "102.5", "", "45")
    @Test fun german_emptyFieldsPlaceholderStaysOneLine() = check(Language.DE, "", "", null)
}
