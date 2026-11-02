package app.overfit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.overfit.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Startet die App und klickt alle Tabs und Unterseiten einmal durch. */
@RunWith(RobolectricTestRunner::class)
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun allScreensOpen() {
        compose.onNodeWithText("Training starten").assertIsDisplayed()

        compose.onNodeWithText("Verlauf").performClick()
        compose.onNodeWithText("Noch keine Trainings", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Übungen").performClick()
        compose.onNodeWithText("Übungskatalog", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Fotos").performClick()
        compose.onNodeWithText("Foto-Tagebuch").assertIsDisplayed()

        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("Coach-Gedächtnis").performClick()
        compose.onNodeWithText("Der Coach hat noch nichts", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("Über Overfit").performScrollTo().performClick()
        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Training starten").assertIsDisplayed()
    }
}
