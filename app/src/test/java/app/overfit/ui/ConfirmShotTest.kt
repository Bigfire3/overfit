package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.ConfirmArea
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.HeaderDeleteButton
import app.overfit.ui.components.HeaderIconButton
import app.overfit.ui.components.Icon
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Title
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Evidence screenshots (360 dp, light/dark) of the two-tap confirm buttons, normal and armed. Runs only with `SCREENSHOT_DIR`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class ConfirmShotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, arm: (() -> Unit)? = null) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        val t = stringsFor(Language.DE)
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides t) {
                    Box(Modifier.width(360.dp).height(640.dp).background(Theme.colors.background).testTag("shot")) {
                        ConfirmArea(Modifier.fillMaxSize()) {
                            Screen(scroll = false, header = {
                                BackHeader(t.back, {}) {
                                    HeaderIconButton(Icon.Share, t.share.shareTemplate, {})
                                    HeaderDeleteButton(t.deleteTemplate, {})
                                }
                            }) {
                                Title("Push")
                                Spacer(Modifier.height(Space.l))
                                ConfirmButton(t.finishWorkout, {}, Modifier, compact = true, kind = app.overfit.ui.components.ConfirmKind.Complete)
                                Spacer(Modifier.height(Space.l))
                                ConfirmButton(t.discardWorkout, {})
                                Spacer(Modifier.height(Space.l))
                                ConfirmButton(t.reset, {})
                                Spacer(Modifier.height(Space.l))
                                ConfirmButton(t.photos.importReplace, {}, primary = true)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        arm?.invoke()
        compose.waitForIdle()
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val t = stringsFor(Language.DE)
    private fun armBin() { compose.onNodeWithContentDescription(t.deleteTemplate).performClick() }
    private fun armDiscard() { compose.onNodeWithText(t.discardWorkout).performClick() }
    private fun armFinish() { compose.onNodeWithText(t.finishWorkout).performClick() }
    private fun armRestore() { compose.onNodeWithText(t.photos.importReplace).performClick() }

    @Test fun normalDark() = shot("normal-dunkel")
    @Test fun binDark() = shot("muelleimer-scharf-dunkel", ::armBin)
    @Test fun discardDark() = shot("verwerfen-scharf-dunkel", ::armDiscard)
    @Test fun finishDark() = shot("beenden-scharf-dunkel", ::armFinish)
    @Test fun restoreDark() = shot("backup-scharf-dunkel", ::armRestore)

    @Test fun storyHalfBar() {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverfitTheme {
                Box(Modifier.width(360.dp).height(640.dp).background(Theme.colors.background).testTag("shot")) {
                    app.overfit.ui.coach.CoachStoryScreen(onClose = {}, onTarget = {})
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(app.overfit.ui.coach.STORY_AUTO_MS * 3 / 2 + 100)
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "story-halber-balken-dunkel.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
