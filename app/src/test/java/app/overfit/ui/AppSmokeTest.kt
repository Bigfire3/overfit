package app.overfit.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.overfit.ai.Providers
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.TrackingType
import java.io.File
import kotlinx.coroutines.flow.first
import app.overfit.data.DeviceDefaults
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs onboarding, then opens every tab and sub-screen once. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp") // a typical phone instead of Robolectric's tiny default screen
class AppSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private var keyIsValid = false
    private val secrets = InMemorySecretStore()
    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, secrets, DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    // Catalogue straight from the source tree (the working directory of :app tests is app/).
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val providers = Providers(
        HttpClient(MockEngine { respond("{}", if (keyIsValid) HttpStatusCode.OK else HttpStatusCode.Unauthorized) }),
    )

    private fun launch() = compose.setContent {
        OverfitApp(AppDependencies(store, providers, data, versionName = "0.1.test", today = { LocalDate(2026, 11, 2) }))
    }

    private fun continueButton() = compose.onNodeWithText("Weiter")

    @Test
    fun onboardingThenAllScreens() {
        launch()

        // Name
        continueButton().assertIsNotEnabled()
        compose.onNodeWithText("Dein Name").performTextInput("Fabi")
        continueButton().performClick()

        // Age
        compose.onNodeWithText("Jahre").assertIsDisplayed()
        compose.onNodeWithText("30").performTextInput("31")
        continueButton().performClick()

        // Weight
        compose.onNodeWithText("75").performTextInput("82,4")
        continueButton().performClick()

        // AI: wrong key first, then a valid one
        compose.onNodeWithText("OpenRouter").assertIsDisplayed()
        compose.onNodeWithText("API-Key einfügen", substring = true).performTextInput("sk-or-wrong")
        compose.onNodeWithText("Verbinden").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTextExactly("Dieser Key funktioniert nicht.")).fetchSemanticsNodes().isNotEmpty() }

        keyIsValid = true
        compose.onNodeWithText("Verbinden").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTextExactly("Training starten")).fetchSemanticsNodes().isNotEmpty() }

        // Start tab shows the profile. (Robolectric runs in non-touch mode, so the first focusable row may
        // get initial focus and scroll the screen a little – hence performScrollTo.)
        compose.onNodeWithText("Hallo, Fabi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("82,4 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stand 2. Nov. 2026").performScrollTo().assertIsDisplayed()
        assertEquals("sk-or-wrong", secrets.load())
        assertTrue(store.state.value.onboardingDone)
        assertEquals(1995, store.state.value.profile.birthYear)

        // Other tabs
        compose.onNodeWithText("Verlauf").performClick()
        compose.onNodeWithText("Noch keine Trainings", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Übungen").performClick()
        compose.onNodeWithText("Katalog").assertIsDisplayed()
        compose.onNodeWithText("Fotos").performClick()
        compose.onNodeWithText("Foto-Tagebuch").assertIsDisplayed()

        // Settings, sub-screens, language switch
        compose.onNodeWithText("Profil").performClick()
        compose.onNodeWithText("Verbunden").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Coach-Gedächtnis").performScrollTo().performClick()
        compose.onNodeWithText("Was der Coach über dich weiß", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Über Overfit").performScrollTo().performClick()
        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertExists()
        compose.onNodeWithText("Version 0.1.test").assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Profile").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Hi, Fabi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("82.4 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("As of Nov 2, 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun skipAiAndResetReturnsToOnboarding() {
        launch()
        compose.onNodeWithText("Dein Name").performTextInput("Sam")
        continueButton().performClick()
        compose.onNodeWithText("30").performTextInput("40")
        continueButton().performClick()
        compose.onNodeWithText("75").performTextInput("70")
        continueButton().performClick()
        compose.onNodeWithText("Später einrichten").performClick()

        compose.onNodeWithText("Profil").performClick()
        compose.onNodeWithText("Nicht verbunden").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Alle Daten löschen").performScrollTo().performClick()
        compose.onNodeWithText("Wirklich alles löschen?").performClick()

        compose.onNodeWithText("Dein Name").assertIsDisplayed()
        assertEquals("", store.state.value.profile.name)
    }

    @Test
    fun catalogSearchCustomExerciseAndTemplate() {
        launch()
        compose.onNodeWithText("Dein Name").performTextInput("Kim")
        continueButton().performClick()
        compose.onNodeWithText("30").performTextInput("30")
        continueButton().performClick()
        compose.onNodeWithText("75").performTextInput("80")
        continueButton().performClick()
        compose.onNodeWithText("Später einrichten").performClick()

        // Catalogue: German search term finds the English exercise
        compose.onNodeWithText("Übungen").performClick()
        waitFor("Suchen, z. B. Klimmzug")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Klimmzug")
        waitFor("Pull-up")

        // Detail of a catalogue exercise: change tracking type, save
        compose.onNodeWithText("Pull-up").performClick()
        waitFor("ERFASSUNG")
        compose.onNodeWithText("Gewicht × Wdh.").performClick()
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntil(5_000) {
            kotlinx.coroutines.runBlocking { data.exercises.get("wg:pull-up")?.trackingType } == TrackingType.WEIGHT_REPS
        }

        // Custom exercise, prefilled with the search text
        waitFor("Eigene Übung")
        compose.onNodeWithText("Suchen, z. B. Klimmzug", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Eigene Übung").performClick()
        compose.onNodeWithText("Klimmzug").assertIsDisplayed()
        compose.onNodeWithText("Zeit").performClick()
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntil(5_000) {
            kotlinx.coroutines.runBlocking { database.exerciseDao().count() } > 1000 &&
                kotlinx.coroutines.runBlocking { data.exercises.exercises.first() }.any { it.isCustom && it.name == "Klimmzug" }
        }

        // Template: name, one exercise from the picker, an extra set, save
        compose.onNodeWithText("Vorlagen").performClick()
        compose.onNodeWithText("Neue Vorlage").performClick()
        waitFor("Name der Vorlage")
        compose.onNodeWithText("Name der Vorlage").performTextInput("Push")
        compose.onNodeWithText("Übung hinzufügen").performClick()
        waitFor("Übungen hinzufügen")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Bankdrücken")
        waitFor("Bench Press")
        compose.onAllNodesWithText("Bench Press")[0].performClick()
        compose.onNodeWithText("1 hinzufügen").performClick()
        waitFor("3 Sätze", substring = true)
        compose.onNodeWithText("Satz hinzufügen").performClick()
        waitFor("4 Sätze", substring = true)
        compose.onNodeWithText("Speichern").performClick()

        // Back in the templates list (its footer button), the template shows its exercise
        waitFor("Neue Vorlage")
        compose.onNodeWithText("Push").assertIsDisplayed()
        compose.onNodeWithText("Bench Press").assertIsDisplayed()
        compose.waitUntil(5_000) { kotlinx.coroutines.runBlocking { data.templates.templates.first() }.size == 1 }
        compose.onNodeWithText("Start").performClick()
        waitFor("Push")
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun hasTextExactly(text: String) = androidx.compose.ui.test.hasText(text, substring = false)
}
