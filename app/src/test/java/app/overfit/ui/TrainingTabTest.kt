package app.overfit.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.Providers
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.DeviceDefaults
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.SessionStatus
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.WeightUnit
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Training tab: "+" → start empty → add an exercise → finish → it is a saved workout that starts
 * again; changes made during a workout arrive in the saved workout without asking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TrainingTabTest {
    @get:Rule
    val compose = createComposeRule()

    // Tests that look at an empty app opt out of the first-start sample workouts (Push/Pull/Legs), as an existing install would.
    private val kv = InMemoryKeyValueStore().apply { put(AppData.KEY_SAMPLES_SEEDED, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    private fun launch(): AppDependencies {
        store.setName("Fabi")
        store.completeOnboarding()
        runBlocking { data.seedCatalog() }
        val deps = AppDependencies(store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test", today = { LocalDate(2026, 11, 3) })
        compose.setContent { OverfitApp(deps) }
        waitFor("Duo · bald")
        return deps
    }

    private fun templates() = runBlocking { data.templates.templates.first() }

    @Test
    fun startEmpty_addExercise_finish_isSavedWorkout_startsAgain() {
        val deps = launch()
        waitFor("Noch keine Workouts", substring = true)

        // "+" → start empty → the workout runs without exercises
        compose.onNodeWithContentDescription("Neues Workout").performClick()
        waitFor("Leer starten")
        compose.onNodeWithText("Leer starten").performClick()
        waitFor("Übung hinzufügen")
        val running = requireNotNull(deps.workout.state.value.workout)
        assertEquals(0, running.exercises.size)

        // Add one exercise from the picker
        compose.onNodeWithText("Übung hinzufügen").performScrollTo().performClick()
        waitFor("Übungen hinzufügen")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Bankdrücken")
        waitFor("Bench Press")
        compose.onAllNodesWithText("Bench Press")[0].performClick()
        compose.onNodeWithText("1 hinzufügen").performClick()
        compose.waitUntilState(20_000) { deps.workout.state.value.workout?.exercises?.size == 1 }

        // Finish (confirm pattern) → finish flow: the name is set here
        compose.onNodeWithText("Beenden").performClick()
        compose.onNodeWithText("Beenden? Nochmal tippen").performClick()
        waitFor("Wie war das Training?")
        compose.waitUntilState(20_000) { runBlocking { data.workouts.status(running.id) } == SessionStatus.FINISHED }
        // Saved right away, without asking, under the default name
        compose.waitUntilState(20_000) { templates().size == 1 }
        val nameField = hasSetTextAction() and hasText("Workout vom", substring = true)
        compose.waitUntil(20_000) { compose.onAllNodes(nameField).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(nameField).performTextReplacement("Brust")
        compose.onNodeWithText("Speichern").performClick()
        waitFor("Duo · bald")
        compose.waitUntilState(20_000) { templates().singleOrNull()?.name == "Brust" }
        assertEquals("Brust", runBlocking { data.workouts.getFinished(running.id) }?.workout?.name)

        // In the list with "last done today"; open → start again → the exercise is there
        waitFor("Brust")
        compose.onNodeWithText("zuletzt heute").assertExists()
        compose.onNodeWithText("Brust").performClick()
        waitFor("Starten")
        compose.onNodeWithText("Bench Press").assertExists()
        compose.onNodeWithText("Starten").performClick()
        compose.waitUntilState(20_000) { deps.workout.state.value.workout != null }
        val again = requireNotNull(deps.workout.state.value.workout)
        assertEquals(templates().single().id, again.templateId)
        assertEquals(listOf("Bench Press"), again.exercises.map { it.exercise.name })
    }

    @Test
    fun changesDuringWorkout_arriveInSavedWorkout() {
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, 60, null, List(2) { PlannedSet(60.0, 8) }))))
        }
        val deps = launch()
        waitFor("noch nie trainiert")
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        compose.onNodeWithText("Starten").performClick()
        compose.waitUntilState(20_000) { deps.workout.state.value.workout != null }

        // Add an exercise and a set during the workout
        val w = requireNotNull(deps.workout.state.value.workout)
        compose.runOnIdle { deps.workout.edit { it.addSet(w.exercises[0].id) } }
        compose.onNodeWithText("Übung hinzufügen").performScrollTo().performClick()
        waitFor("Übungen hinzufügen")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Squat")
        val squatRow = hasText("Squat", substring = true) and hasClickAction() and !hasSetTextAction()
        compose.waitUntil(20_000) { compose.onAllNodes(squatRow).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(squatRow)[0].performClick()
        compose.onNodeWithText("1 hinzufügen").performClick()
        compose.waitUntilState(20_000) { deps.workout.state.value.workout?.exercises?.size == 2 }

        compose.onNodeWithText("Beenden").performClick()
        compose.onNodeWithText("Beenden? Nochmal tippen").performClick()
        waitFor("Wie war das Training?")

        // No dialog: the saved workout has the new structure; planned values of kept sets stay
        compose.waitUntilState(20_000) { runBlocking { data.templates.get(templates().single().id) }?.exercises?.size == 2 }
        val push = requireNotNull(runBlocking { data.templates.get(templates().single().id) })
        assertEquals("Push", push.name)
        assertEquals(listOf(PlannedSet(60.0, 8), PlannedSet(60.0, 8), PlannedSet()), push.exercises[0].sets)
        assertEquals(1, templates().size)
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        try {
            compose.waitUntil(20_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e)
        }
    }
}
