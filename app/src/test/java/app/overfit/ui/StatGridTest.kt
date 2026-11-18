package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Stat
import app.overfit.ui.components.StatGrid
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
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
 * Number tiles ([StatGrid]) on a 360 dp phone at font scale 1.0 and 1.3: a long label ("Bestes geschätztes 1RM")
 * wraps to a second line instead of being cut, and the two tiles of a row stay equally tall. A number that counts up
 * keeps the size fitted to its final value on the way there. With `SCREENSHOT_DIR` it also writes pictures, plus the
 * main workout button with a long exercise name (its second line keeps the set and the values).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class StatGridTest {
    @get:Rule
    val compose = createComposeRule()

    private var fontScale by mutableStateOf(1.0f)
    private var language by mutableStateOf(Language.DE)
    private val progress = mutableFloatStateOf(0f)

    private fun setContent() = compose.setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            OverfitTheme {
                val t = stringsFor(language)
                CompositionLocalProvider(LocalStrings provides t) {
                    // Same width as on a screen: 360 dp, 24 dp gutter on both sides.
                    Column(Modifier.width(360.dp).background(Theme.colors.background).padding(horizontal = Space.l, vertical = Space.s).testTag("shot")) {
                        val h = t.history
                        StatGrid(
                            listOf(
                                Stat(h.maxWeight, "142,5 kg"),
                                Stat(h.bestE1rm, "166,3 kg"),
                                Stat(h.maxReps, "12 ${t.repsUnit}"),
                                // Counts up to a long value: the size is fitted to the end, not to "0 kg".
                                Stat(h.volume, "12.345 kg", counting = { if (progress.floatValue < 1f) "${(12345 * progress.floatValue).toInt()} kg" else "12.345 kg" }),
                            ),
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Space.m))
                        PrimaryButton(
                            t.logNextSet, onClick = {},
                            caption = "Incline Dumbbell Press", captionEnd = "${t.setNumber(2)} · 22,5 kg × 12",
                        )
                    }
                }
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    @Test
    fun labelsWrapInsteadOfBeingCut_tilesOfARowStayEquallyTall() {
        setContent()
        val dir = System.getenv("SCREENSHOT_DIR")?.let { File(it).apply { mkdirs() } }
        for (l in listOf(Language.DE, Language.EN)) for (scale in listOf(1.0f, 1.3f)) {
            language = l
            fontScale = scale
            progress.floatValue = 1f
            compose.waitForIdle()
            val h = stringsFor(l).history
            if (dir != null) {
                File(dir, "kacheln-${l.name.lowercase()}-$scale.png").outputStream().use {
                    compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            for (label in listOf(h.maxWeight, h.bestE1rm, h.maxReps, h.volume)) {
                val layout = layoutOf(label)
                assertFalse("$l $scale: \"$label\" is cut off", layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) < label.length)
            }
            // The tile is the merged node around its label.
            fun tile(label: String) = compose.onNode(hasText(label, substring = true)).getBoundsInRoot().let { it.bottom - it.top }
            assertEquals("$l $scale: first row", tile(h.maxWeight), tile(h.bestE1rm))
            assertEquals("$l $scale: second row", tile(h.maxReps), tile(h.volume))
        }
    }

    @Test
    fun countingNumberKeepsTheSizeOfItsFinalValue() {
        language = Language.DE
        fontScale = 1.3f
        setContent()
        progress.floatValue = 1f
        compose.waitForIdle()
        val final = layoutOf("12.345 kg").layoutInput.style.fontSize
        progress.floatValue = 0.01f
        compose.waitForIdle()
        val counting = layoutOf("123 kg").layoutInput.style.fontSize
        assertEquals(final, counting)
    }
}
