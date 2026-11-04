package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.overfit.domain.Language
import app.overfit.domain.SetValues
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.WorkoutSet
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.Block
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.ui.workout.WorkoutSetRow
import app.overfit.ui.workout.setColumnLabels
import app.overfit.ui.components.SetColumnsHeader
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Evidence screenshots of the set rows (running training / edit training) on a 360 dp phone with the
 * values 1, 11, 102.5 and 1000, at font scale 1.0 and 1.3, light and dark. Only runs when `SCREENSHOT_DIR`
 * is set, like [ScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class SetFieldShotTest {
    @get:Rule
    val compose = createComposeRule()

    private val rows = listOf(1.0 to 1, 11.0 to 11, 102.5 to 10, 1000.0 to 1000)

    private fun shot(language: Language, fontScale: Float, name: String) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        val t = stringsFor(language)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                OverfitTheme {
                    CompositionLocalProvider(LocalStrings provides t) {
                        Column(
                            Modifier.width(360.dp).background(Theme.colors.background).padding(horizontal = Space.l, vertical = Space.m)
                                .testTag("shot"),
                        ) {
                            Block {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                    SetColumnsHeader(t.setColumn, setColumnLabels(TrackingType.WEIGHT_REPS, WeightUnit.KG, t), trailing = 48.dp)
                                    rows.forEachIndexed { i, (kg, reps) ->
                                        WorkoutSetRow(
                                            number = i + 1,
                                            type = TrackingType.WEIGHT_REPS,
                                            unit = WeightUnit.KG,
                                            language = language,
                                            set = WorkoutSet("s$i", target = SetValues(kg, reps), actual = SetValues(kg, reps), completed = i == 0),
                                            onValues = {},
                                            onToggle = {},
                                            onRemove = {},
                                        )
                                    }
                                    WorkoutSetRow(
                                        number = 5,
                                        type = TrackingType.WEIGHT_REPS,
                                        unit = WeightUnit.KG,
                                        language = language,
                                        set = WorkoutSet("s5", target = SetValues(60.0, 8)),
                                        onValues = {},
                                        onToggle = {},
                                        onRemove = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun dark_de_1_0() = shot(Language.DE, 1.0f, "satz-dunkel-de-1.0")
    @Test fun dark_de_1_3() = shot(Language.DE, 1.3f, "satz-dunkel-de-1.3")
    @Test fun dark_en_1_0() = shot(Language.EN, 1.0f, "satz-dunkel-en-1.0")
    @Test fun dark_en_1_3() = shot(Language.EN, 1.3f, "satz-dunkel-en-1.3")
}
