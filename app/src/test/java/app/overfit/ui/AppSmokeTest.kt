package app.overfit.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var keyIsValid = false
    private val secrets = InMemorySecretStore()
    // Tests that look at an empty app opt out of the first-start sample workouts (Push/Pull/Legs), as an existing install would.
    private val kv = InMemoryKeyValueStore().apply { put(AppData.KEY_SAMPLES_SEEDED, "1"); put(app.overfit.data.AppStore.KEY_COACH_STORY_SEEN, "1") }
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
        compose.waitForTrainingTab(timeoutMillis = 5_000)

        // Training tab (no profile hero anymore): empty state with the "+" button; the profile data is in the store.
        compose.onNodeWithContentDescription("Neues Workout").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Workouts", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals("Fabi", store.state.value.profile.name)
        assertEquals(82.4, store.state.value.weights.last().kg, 0.001)
        assertEquals("sk-or-wrong", secrets.load())
        assertTrue(store.state.value.onboardingDone)
        assertEquals(1995, store.state.value.profile.birthYear)

        // Other tabs
        // No History tab any more: finished trainings are reached through the calendar (Kalender tab)
        compose.onAllNodes(hasText("Verlauf")).assertCountEquals(0)
        compose.onAllNodes(hasClickAction() and hasTestTag("tab:HISTORY")).assertCountEquals(0)
        // Exercises are no tab anymore: four tabs, the catalogue opens from the Training tab
        compose.onAllNodes(hasText("Übungen") and hasClickAction()).assertCountEquals(0)
        compose.onNode(hasText("Workouts") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Übungen").performClick()
        compose.onNodeWithText("Eigene Übung").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithTag("tab:PHOTOS").performClick()
        compose.onNodeWithText("Noch keine Trainings oder Fotos.").assertIsDisplayed()

        // Coach tab: AI access with provider, memory
        compose.onNodeWithTag("tab:COACH").performClick()
        compose.onNodeWithText("Eigener Key · OpenRouter").assertIsDisplayed()
        compose.onNodeWithText("Coach-Gedächtnis").performScrollTo().performClick()
        compose.onNodeWithText("Was der Coach über dich weiß", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()

        // Settings, sub-screens, language switch
        compose.onNode(androidx.compose.ui.test.hasText("Profil") and androidx.compose.ui.test.hasClickAction()).performClick()
        compose.onNodeWithText("Coach & KI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Über Overfit").performScrollTo().performClick()
        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertExists()
        compose.onNodeWithText("Version 0.1.test").assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("English").performScrollTo().performClick()
        // Page title and tab both say "Profile" now (one word per tab, same on the page).
        compose.onNodeWithText("ABOUT YOU").assertExists()
        compose.onNode(hasText("Profile") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Workouts") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("New workout").assertIsDisplayed()
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

        compose.onNodeWithTag("tab:COACH").performClick()
        compose.onNodeWithText("Kein KI-Zugang").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasText("Profil") and androidx.compose.ui.test.hasClickAction()).performClick()
        compose.onNodeWithText("Alle Daten löschen").performScrollTo().performClick()
        compose.onNodeWithTag("dialog:confirm").performClick()

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
        compose.onNodeWithContentDescription("Übungen").performClick()
        waitFor("Suchen, z. B. Klimmzug")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Klimmzug")
        waitFor("Pull-up")

        // Detail of a catalogue exercise: change tracking type, save
        compose.onNodeWithText("Pull-up").performClick()
        waitFor("ERFASSUNG")
        compose.onNodeWithText("Gewicht × Wdh.").performClick()
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntilState(5_000) {
            kotlinx.coroutines.runBlocking { data.exercises.get("wg:pull-up")?.trackingType } == TrackingType.WEIGHT_REPS
        }

        // Custom exercise, prefilled with the search text
        waitFor("Eigene Übung")
        compose.onNodeWithText("Suchen, z. B. Klimmzug", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Eigene Übung").performClick()
        compose.onNodeWithText("Klimmzug").assertIsDisplayed()
        compose.onNodeWithText("Zeit").performClick()
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntilState(5_000) {
            kotlinx.coroutines.runBlocking { database.exerciseDao().count() } > 300 &&
                kotlinx.coroutines.runBlocking { data.exercises.exercises.first() }.any { it.isCustom && it.name == "Klimmzug" }
        }

        // Workout: back from the catalogue to the Training tab → "+" → create; name, one exercise from the picker, an extra set, save
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForTrainingTab()
        compose.onNodeWithContentDescription("Neues Workout").performClick()
        waitFor("Workout erstellen")
        compose.onNodeWithText("Workout erstellen").performClick()
        waitFor("Name des Workouts")
        compose.onNodeWithText("Name des Workouts").performTextInput("Push")
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

        // Saving closes the editor; the Training tab lists the workout with its exercise
        compose.waitForTrainingTab()
        compose.waitUntilState(5_000) { kotlinx.coroutines.runBlocking { data.templates.templates.first() }.size == 1 }
        compose.onNodeWithText("Push", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Bench Press", substring = true).assertIsDisplayed()
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString().take(3000), e)
        }
    }

    private fun hasTextExactly(text: String) = androidx.compose.ui.test.hasText(text, substring = false)
}
