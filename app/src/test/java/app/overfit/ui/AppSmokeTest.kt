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
import app.overfit.data.AppStore
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

/** Runs onboarding, then opens every tab and sub-screen once. */
@RunWith(RobolectricTestRunner::class)
class AppSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private var keyIsValid = false
    private val secrets = InMemorySecretStore()
    private val store = AppStore(InMemoryKeyValueStore(), secrets, DeviceDefaults(Language.DE, WeightUnit.KG))
    private val providers = Providers(
        HttpClient(MockEngine { respond("{}", if (keyIsValid) HttpStatusCode.OK else HttpStatusCode.Unauthorized) }),
    )

    private fun launch() = compose.setContent {
        OverfitApp(AppDependencies(store, providers, versionName = "0.1.test", today = { LocalDate(2026, 11, 2) }))
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

        // Start tab shows the profile
        compose.onNodeWithText("Hallo, Fabi").assertIsDisplayed()
        compose.onNodeWithText("82,4 kg").assertIsDisplayed()
        compose.onNodeWithText("Stand 2. Nov. 2026").assertIsDisplayed()
        assertEquals("sk-or-wrong", secrets.load())
        assertTrue(store.state.value.onboardingDone)
        assertEquals(1995, store.state.value.profile.birthYear)

        // Other tabs
        compose.onNodeWithText("Verlauf").performClick()
        compose.onNodeWithText("Noch keine Trainings", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Übungen").performClick()
        compose.onNodeWithText("Übungskatalog", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Fotos").performClick()
        compose.onNodeWithText("Foto-Tagebuch").assertIsDisplayed()

        // Settings, sub-screens, language switch
        compose.onNodeWithText("Profil").performClick()
        compose.onNodeWithText("Verbunden").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Coach-Gedächtnis").performScrollTo().performClick()
        compose.onNodeWithText("Der Coach hat noch nichts", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Über Overfit").performScrollTo().performClick()
        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertExists()
        compose.onNodeWithText("Version 0.1.test").assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Profile").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Hi, Fabi").assertIsDisplayed()
        compose.onNodeWithText("82.4 kg").assertIsDisplayed()
        compose.onNodeWithText("As of Nov 2, 2026").assertIsDisplayed()
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

    private fun hasTextExactly(text: String) = androidx.compose.ui.test.hasText(text, substring = false)
}
