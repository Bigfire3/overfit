package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.domain.RestTimer
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.ui.workout.REST_BAR_TAG
import app.overfit.ui.workout.REST_SKIP_LABEL_TAG
import app.overfit.ui.workout.RestBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The rest timer of the running workout (the island above the main button) on a 360 dp phone: nothing is cut off,
 * in German and English, with a short and a long countdown, at font scale 1.0 and 1.3; every control is a 48 dp
 * target and does what it says. With `SCREENSHOT_DIR` set it also writes pictures of every case.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class RestBarTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Case(val language: Language, val fontScale: Float, val totalSec: Int, val elapsedSec: Int) {
        val name get() = "pause-${language.name.lowercase()}-$fontScale-${totalSec}s-nach-${elapsedSec}s"
    }

    private var case by mutableStateOf(Case(Language.DE, 1.0f, 150, 0))
    private val adjusted = mutableListOf<Int>()
    private var skipped = 0
    private var shownNext = 0

    private fun setContent() = compose.setContent {
        val current = case
        val t = stringsFor(current.language)
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, current.fontScale)) {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides t) {
                    // Same width as in the workout: 360 dp screen, 24 dp gutter on both sides.
                    Column(Modifier.width(360.dp).background(Theme.colors.background).padding(horizontal = Space.l, vertical = Space.s).testTag("shot")) {
                        val now = androidx.compose.runtime.remember(current) { mutableLongStateOf(current.elapsedSec * 1000L) }
                        RestBar(
                            rest = RestTimer(startedAt = 0, endsAt = current.totalSec * 1000L, afterSetId = "s1"),
                            now = now,
                            nextLine = t.nextUp("Bench Press · ${t.setNumber(2)} · 60 kg × 8"),
                            onAdjust = { adjusted += it },
                            onSkip = { skipped++ },
                            onShowNext = { shownNext++ },
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Space.s))
                        PrimaryButton(t.logNextSet, onClick = {}, caption = "Bench Press · ${t.setNumber(2)} · 60 kg × 8")
                    }
                }
            }
        }
    }

    private fun skipLabelLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(REST_SKIP_LABEL_TAG, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    @Test
    fun nothingIsCutOff_inBothLanguages_shortAndLongCountdown_normalAndLargeFont() {
        setContent()
        val dir = System.getenv("SCREENSHOT_DIR")?.let { File(it).apply { mkdirs() } }
        val cases = buildList {
            for (language in listOf(Language.DE, Language.EN)) for (scale in listOf(1.0f, 1.3f)) {
                add(Case(language, scale, totalSec = 150, elapsedSec = 60))
                // A rest of ten minutes and more needs a fifth character.
                add(Case(language, scale, totalSec = 900, elapsedSec = 100))
            }
        }
        for (c in cases) {
            case = c
            compose.waitForIdle()
            val layout = skipLabelLayout()
            assertFalse("${c.name}: \"${stringsFor(c.language).restSkip}\" is cut off", layout.hasVisualOverflow || layout.isLineEllipsized(0))
            // The label stays readable: never smaller than 12 sp (the lower end of every shrinking button label).
            val t = stringsFor(c.language)
            compose.onNodeWithText(t.restSkip).assertExists()
            compose.onNodeWithContentDescription(t.restPlus).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithContentDescription(t.restMinus).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText(t.restSkip).assertHeightIsAtLeast(48.dp)
            if (dir != null) {
                File(dir, "${c.name}.png").outputStream().use {
                    compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    @Test
    fun everyControlDoesWhatItSays() {
        setContent()
        compose.waitForIdle()
        // 150 s rest, nothing elapsed: the countdown stands at 2:30 and is named with what comes next.
        compose.onNodeWithTag(REST_BAR_TAG).assertExists()
        compose.onNode(hasContentDescription("Pause, 2:30, Nächste: Bench Press · Satz 2 · 60 kg × 8")).performClick()
        assertEquals(1, shownNext)
        compose.onNodeWithContentDescription("+15 s").performClick()
        compose.onNodeWithContentDescription("−15 s").performClick()
        assertEquals(listOf(15, -15), adjusted)
        compose.onNodeWithText("Überspringen").performClick()
        assertEquals(1, skipped)
    }
}
