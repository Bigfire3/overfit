package app.overfit.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.coach.CoachReply
import app.overfit.coach.FakeCoachTransport
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.DeviceDefaults
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Coach: review last workout → plan → accept; demo without access; memory editing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CoachFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private var templateId = 0L

    private val answer = """{"rating":8,"summary":"Starkes Training","positives":["Sauber"],"improvements":[],"tips":["Mehr schlafen"],"photo_feedback":null,"next_session":{"exercises":[{"exercise_id":"wg:bench-press","is_new":false,"rest_sec":90,"sets":[{"weight_kg":62.5,"reps":8,"duration_sec":null},{"weight_kg":62.5,"reps":8,"duration_sec":null}],"reason":"Alle Sätze geschafft"}],"note":null},"memory":"Ziel: Kraft."}"""

    @After
    fun tearDown() = database.close()

    private fun setUp(withKey: Boolean, seedWorkout: Boolean = true): AppDependencies {
        store.setName("Fabi")
        store.completeOnboarding()
        if (withKey) store.connectAi(ProviderId.ANTHROPIC, "sk-test")
        runBlocking {
            data.seedCatalog()
            if (seedWorkout) {
                val bench = requireNotNull(data.exercises.get("wg:bench-press"))
                templateId = data.templates.save(
                    Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 60, note = null, sets = List(2) { PlannedSet(60.0, 8) }))),
                )
                var w = Workout.fromTemplate(requireNotNull(data.templates.get(templateId)), 1_000L, id = "w1")
                w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, 2_000L) } }
                data.workouts.finish(w, 3_600_000L)
            }
        }
        val fake = FakeCoachTransport { _, _ -> CoachReply(answer, answer) }
        val deps = AppDependencies(
            store,
            Providers(HttpClient(MockEngine { respond("{}") })),
            data,
            versionName = "0.1.test",
            today = { LocalDate(2026, 11, 3) },
            coachTransport = { fake },
        )
        compose.setContent { OverfitApp(deps) }
        return deps
    }

    @Test
    fun reviewLastWorkoutAndAcceptPlan() {
        setUp(withKey = true)
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:COACH").performClick()
        waitFor("Letztes Training auswerten")
        compose.onNodeWithText("Letztes Training auswerten").performClick()
        waitFor("An Coach senden")
        compose.onNodeWithText("An Coach senden").performClick()

        waitFor("8/10")
        waitFor("Mehr schlafen")
        waitFor("Bench Press", substring = true)
        compose.onNodeWithText("Alle annehmen").performClick()
        waitFor("Plan übernommen. Das Workout ist angepasst.")

        compose.waitUntilState(10_000) {
            runBlocking { data.templates.get(templateId) }?.exercises?.single()?.sets?.first()?.weightKg == 62.5
        }
        val bench = runBlocking { data.templates.get(templateId) }!!.exercises.single()
        assertEquals(90, bench.restSec)
    }

    @Test
    fun withoutAccessTheDemoReviewIsOffered() {
        setUp(withKey = false)
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:COACH").performClick()
        waitFor("Letztes Training auswerten")
        compose.onNodeWithText("Letztes Training auswerten").performClick()
        waitFor("Kein KI-Zugang")
        compose.onNodeWithText("Demo-Auswertung ansehen").performClick()
        waitFor("Demo · Beispielinhalt, nicht vom Coach")
        waitFor("8/10")
    }

    @Test
    fun editingTheCoachMemory() {
        setUp(withKey = true, seedWorkout = false)
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:COACH").performClick()
        waitFor("Coach-Gedächtnis")
        compose.onNodeWithText("Coach-Gedächtnis").performScrollTo().performClick()
        waitFor("Was der Coach über dich weiß", substring = true)
        compose.onNodeWithTag("coach-memory-input").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Ziel: 100 kg Bankdrücken")
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntilState(10_000) { runBlocking { database.coachDao().currentMemory() }?.text == "Ziel: 100 kg Bankdrücken" }
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}
