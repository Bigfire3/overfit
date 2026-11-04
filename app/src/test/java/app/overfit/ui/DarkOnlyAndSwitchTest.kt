package app.overfit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.overfit.ui.components.SWITCH_KNOB_TAG
import app.overfit.ui.components.SwitchRow
import app.overfit.ui.theme.DarkColors
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Nur Dunkelmodus" and the plain on/off switches (knob left = off, right = on). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DarkOnlyAndSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    /** System setting "light" (notnight): the app still uses the dark palette and paints a black background. */
    @Test @Config(qualifiers = "notnight")
    fun systemLight_appIsStillDark() {
        var colors: app.overfit.ui.theme.Colors? = null
        compose.setContent {
            OverfitTheme {
                colors = Theme.colors
                androidx.compose.foundation.layout.Box(Modifier.size(40.dp).testTag("bg").background(Theme.colors.background))
            }
        }
        compose.waitForIdle()
        assertEquals(DarkColors, colors)
        val bmp = compose.onNodeWithTag("bg").captureToImage().asAndroidBitmap()
        assertEquals(android.graphics.Color.BLACK, bmp.getPixel(bmp.width / 2, bmp.height / 2))
    }

    private fun content(rtl: Boolean = false) {
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    var on by remember { mutableStateOf(false) }
                    Column(Modifier.width(360.dp)) { SwitchRow("Vibration", on, Modifier.testTag("row")) { on = it } }
                }
            }
        }
    }

    /** Knob centre relative to the row's switch track: < 0.5 = start half, > 0.5 = end half (in screen x). */
    private fun knobX(): Float {
        val knob = compose.onNodeWithTag(SWITCH_KNOB_TAG, useUnmergedTree = true).getBoundsInRoot()
        return ((knob.left + knob.right) / 2).value
    }

    @Test
    fun switch_knobLeftWhenOff_rightWhenOn_noText() {
        content()
        val row = compose.onNodeWithTag("row")
        row.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        val off = knobX()
        row.performClick()
        compose.waitForIdle()
        row.assertIsOn()
        val on = knobX()
        assertTrue("knob moves right when switched on ($off → $on)", on > off + 10)
        // Only the label, no "An/Aus" text.
        compose.onNodeWithText("Vibration").assertExists()
        listOf("An", "Aus", "On", "Off").forEach { compose.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0) }
    }

    @Test
    fun switch_inRtl_isMirrored() {
        content(rtl = true)
        val off = knobX()
        compose.onNodeWithTag("row").performClick()
        compose.waitForIdle()
        val on = knobX()
        assertTrue("RTL: knob moves left when switched on ($off → $on)", on < off - 10)
    }

    /** Evidence screenshot (360 dp): one switch off, one on, in a panel. Only with `SCREENSHOT_DIR`. */
    @Test @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun shot_offAndOn() {
        val dir = System.getenv("SCREENSHOT_DIR") ?: return
        compose.setContent {
            OverfitTheme {
                androidx.compose.foundation.layout.Box(Modifier.size(360.dp, 240.dp).background(Theme.colors.background).testTag("shot")) {
                    Column(Modifier.padding(24.dp)) {
                        app.overfit.ui.components.Panel {
                            SwitchRow("Vibration (aus)", false) {}
                            SwitchRow("Ticken (an)", true) {}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val out = java.io.File(dir).also { it.mkdirs() }
        java.io.File(out, "schalter-aus-an.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
