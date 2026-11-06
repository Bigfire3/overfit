package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.ConfirmArea
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.FloatingCard
import app.overfit.ui.components.Icon
import app.overfit.ui.components.IconButton
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.SecondaryButton
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Text
import app.overfit.ui.onboarding.NameStep
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

/** Screenshots of the button style (dark, 360 dp): gallery, onboarding, armed state. Runs only with `SCREENSHOT_DIR`; the size checks always run. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class ButtonShotTest {
    @get:Rule
    val compose = createComposeRule()
    private val t = stringsFor(Language.DE)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(LocalStrings provides t) {
                    Box(Modifier.width(360.dp).height(640.dp).background(Theme.colors.background).testTag("shot")) {
                        ConfirmArea(Modifier.fillMaxSize()) { Column(Modifier.padding(Space.m)) { content() } }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val dir = System.getenv("SCREENSHOT_DIR") ?: return
        val out = File(dir).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun gallery() = show {
        PrimaryButton("Training starten", {}, icon = Icon.Search)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PrimaryButton("Neu", {}, modifier = Modifier.weight(1f))
            SecondaryButton("Import", {}, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.s))
        SecondaryButton("Zweitaktion", {}, icon = Icon.Plus)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SmallButton("Satz hinzufügen", {})
            SmallButton("Abbrechen", {})
            IconButton(Icon.Plus, "Plus", {})
        }
        Spacer(Modifier.height(Space.m))
        FloatingCard(onClick = {}) {
            Column(Modifier.padding(Space.m)) {
                Text("Push", style = Theme.type.label)
                Text("4 Übungen · 52 Min", style = Theme.type.caption)
            }
        }
        Spacer(Modifier.height(Space.m))
        ConfirmButton("Training beenden", {}, Modifier, compact = true)
        Spacer(Modifier.height(Space.s))
        ConfirmButton("Alle Daten löschen", {})
    }

    @Test fun galleryNormal() { gallery(); shot("galerie-normal") }

    @Test fun galleryArmed() {
        gallery()
        compose.onNodeWithText("Alle Daten löschen").performClick()
        compose.waitForIdle()
        shot("galerie-scharf")
    }

    @Test fun onboarding() { show { NameStep("", {}) }; shot("onboarding-name") }

    @Test fun touchTargetsAtLeast48dp() {
        gallery()
        compose.onNodeWithText("Training starten").assertHeightIsAtLeast(64.dp)
        compose.onNodeWithText("Satz hinzufügen").assertHeightIsAtLeast(40.dp)
    }
}
