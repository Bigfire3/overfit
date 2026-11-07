package app.overfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.SearchInput
import app.overfit.ui.theme.OverfitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Leaving a text field (tap elsewhere, another button, scrolling) takes the focus away: no cursor, no keyboard. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class FocusClearTest {
    @get:Rule
    val compose = createComposeRule()

    private var pressed = 0

    private fun content() {
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) {
                    var text by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().testTag("root")) {
                        SearchInput(text, { text = it }, "Suche", "Leeren")
                        PrimaryButton("Knopf", { pressed++ })
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("list")) {
                            Box(Modifier.fillMaxWidth().height(2000.dp).testTag("empty"))
                        }
                    }
                }
            }
        }
    }

    private fun field() = compose.onNode(hasSetTextAction())

    @Test
    fun tapOnEmptyAreaClearsFocus() {
        content()
        field().performClick()
        field().assertIsFocused()
        compose.onNodeWithTag("empty").performClick()
        field().assertIsNotFocused()
    }

    @Test
    fun tapOnFieldKeepsFocus() {
        content()
        field().performClick()
        field().performClick()
        field().assertIsFocused()
    }

    @Test
    fun tapOnAnotherButtonClearsFocusAndStillPresses() {
        content()
        field().performClick()
        field().assertIsFocused()
        compose.onNodeWithTag("root").let { compose.onNode(androidx.compose.ui.test.hasText("Knopf")).performClick() }
        field().assertIsNotFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, pressed) }
    }

    @Test
    fun scrollingClearsFocus() {
        content()
        field().performClick()
        field().assertIsFocused()
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        field().assertIsNotFocused()
    }
}
