package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.Confirm
import app.overfit.ui.components.ConfirmDialogContent
import app.overfit.ui.components.DialogCard
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

/**
 * Evidence screenshots of the confirmation popups (finish, discard, delete workout) on a 360 dp phone over a
 * dimmed background. Only runs when `SCREENSHOT_DIR` is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class DialogShotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(language: Language, name: String) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        val t = stringsFor(language)
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides t) {
                    Column(
                        Modifier.width(360.dp).background(Theme.colors.background).background(Color.Black.copy(alpha = 0.55f))
                            .padding(Space.l).testTag("shot"),
                        verticalArrangement = Arrangement.spacedBy(Space.l),
                    ) {
                        DialogCard(t.finishConfirm) { ConfirmDialogContent(t.finishWorkout, {}, {}, kind = Confirm.Positive) }
                        DialogCard(t.discardConfirm) { ConfirmDialogContent(t.discard, {}, {}) }
                        DialogCard(t.deleteWorkoutConfirm) { ConfirmDialogContent(t.delete, {}, {}, message = t.deletedKeepsHistory) }
                    }
                }
            }
        }
        compose.waitForIdle()
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun dark_de() = shot(Language.DE, "popup-dunkel-de")
    @Test fun dark_en() = shot(Language.EN, "popup-dunkel-en")
}
