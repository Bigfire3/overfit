package app.overfit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.overfit.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Startet die App und klickt alle Tabs, den Katalog und den Vorlagen-Editor durch. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp") // typische Handygröße, damit Listen und FAB sichtbar sind
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun allScreensOpen() {
        compose.onNodeWithText("Training starten").assertIsDisplayed()

        compose.onNodeWithText("Verlauf").performClick()
        compose.onNodeWithText("Noch keine Trainings", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Übungen").performClick()
        compose.onNodeWithText("Katalog").assertIsDisplayed()
        // Text des ExtendedFAB liegt in einem clearAndSetSemantics-Teilbaum → ungemergter Baum.
        compose.onNodeWithText("Eigene Übung", useUnmergedTree = true).assertExists()

        compose.onNodeWithText("Fotos").performClick()
        compose.onNodeWithText("Foto-Tagebuch").assertIsDisplayed()

        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("Alle Bilder offline laden").assertIsDisplayed()
        compose.onNodeWithText("Coach-Gedächtnis").performClick()
        compose.onNodeWithText("Der Coach hat noch nichts", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("Über Overfit").performScrollTo().performClick()
        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Training starten").assertIsDisplayed()
    }

    @Test
    fun catalogSearchAndTemplateEditor() {
        compose.onNodeWithText("Übungen").performClick()
        // Katalog wird beim ersten Start im Hintergrund eingespielt.
        compose.waitUntilAtLeastOneExists(hasText("Übung suchen", substring = true), 20_000)
        compose.onNode(hasSetTextAction()).performTextInput("Klimmzug")
        compose.waitUntilAtLeastOneExists(hasText("Pull-up"), 20_000)

        // Neue Vorlage mit einer Übung anlegen.
        compose.onNodeWithText("Vorlagen").performClick()
        compose.onNodeWithText("Neue Vorlage", useUnmergedTree = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Name der Vorlage"), 10_000)
        compose.onNodeWithText("Name der Vorlage").performTextReplacement("Push")
        compose.onNodeWithText("Übung hinzufügen").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Übungen hinzufügen"), 10_000)
        compose.onNode(hasSetTextAction()).performTextInput("Bankdrücken")
        compose.waitUntilAtLeastOneExists(hasText("Bench Press"), 20_000)
        compose.onAllNodes(hasText("Bench Press")).onFirst().performClick()
        compose.onNodeWithText("1 hinzufügen").performClick()

        compose.waitUntilAtLeastOneExists(hasText("3 Sätze", substring = true), 10_000)
        compose.onNodeWithText("Satz hinzufügen").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("4 Sätze", substring = true), 10_000)
        compose.onNodeWithContentDescription("Speichern").performClick()

        // Zurück im Vorlagen-Tab: Vorlage erscheint mit ihrer Übung.
        compose.waitUntilAtLeastOneExists(hasText("1 Übung · Bench Press", substring = true), 10_000)
        compose.onNodeWithText("Start").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Push"), 10_000)
    }
}
