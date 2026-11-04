package app.overfit.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.ExerciseAutofillSchema
import app.overfit.ai.Providers
import app.overfit.coach.CoachReply
import app.overfit.coach.FakeCoachTransport
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.DeviceDefaults
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Equipment
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Language
import app.overfit.domain.Muscle
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Running workout → "Add exercise" → picker → "Create … as a new exercise" → fill in with AI → save: the exercise is
 * in the catalogue with the AI's fields and directly in the running workout, which keeps its checked-off set and rest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CustomExerciseFlowTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val alerts = RecordingRestAlerts()

    private val answer = """{"trackingType":"WEIGHT_REPS","primaryMuscles":["shoulders","core"],"secondaryMuscles":["glutes","nonsense"],
        |"equipment":"kettlebell","restSec":90,"note":"Arm gestreckt über dem Kopf halten."}""".trimMargin()
    private val fake = FakeCoachTransport { _, _ -> CoachReply(answer, answer) }

    @Test
    fun createExerciseWithAiInsideRunningWorkout() {
        store.setName("Fabi")
        store.completeOnboarding()
        store.useFuel()
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            data.templates.save(
                Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 60, note = null, sets = List(2) { PlannedSet(60.0, 8) }))),
            )
        }
        val deps = AppDependencies(
            store,
            Providers(HttpClient(MockEngine { respond("{}") })),
            data,
            versionName = "0.1.test",
            today = { LocalDate(2026, 11, 3) },
            haptics = RecordingHaptics(),
            restAlerts = alerts,
            coachTransport = { fake },
        )
        compose.setContent { OverfitApp(deps) }

        waitFor("Push")
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        compose.onNodeWithText("Starten").performClick()
        waitFor("0/2 erledigt", substring = true)
        compose.onNodeWithContentDescription("Satz 1 abhaken").performClick()
        waitFor("Überspringen")
        val restBefore = requireNotNull(alerts.pending)

        // Picker → search → create row
        compose.onNodeWithText("Übung hinzufügen").performScrollTo().performClick()
        waitFor("Übungen hinzufügen")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Turkish Get-up Neu")
        waitFor("„Turkish Get-up Neu“ als neue Übung anlegen")
        compose.onNodeWithText("„Turkish Get-up Neu“ als neue Übung anlegen").performScrollTo().performClick()

        // Form: name prefilled, AI fills the rest
        waitFor("Mit KI ausfüllen")
        compose.onNodeWithText("Mit KI ausfüllen").performClick()
        waitFor("Von der KI ausgefüllt", substring = true)
        assertEquals(ExerciseAutofillSchema.schema, fake.prompts.single().schema)
        assertTrue(fake.prompts.single().user.contains("Turkish Get-up Neu"))
        compose.onNodeWithText("Speichern").performClick()

        // Back in the running workout with the new exercise; the set and the rest are untouched.
        // Wait for the header only the workout with both exercises shows (1 of 2 + 3 sets done): the name alone
        // also matches the form and the picker's search field while the save is still running. A wait on the
        // state alone would not do either: it does not pump the main looper, so the save would never finish.
        waitFor("1/5 erledigt", substring = true)
        val workout = requireNotNull(deps.workout.state.value.workout)
        assertEquals(1, workout.completedSets)
        assertEquals(listOf("Bench Press", "Turkish Get-up Neu"), workout.exercises.map { it.exercise.name })
        assertEquals(restBefore, alerts.pending)

        val created = runBlocking { data.exercises.getActive().single { it.name == "Turkish Get-up Neu" } }
        assertEquals(ExerciseSource.CUSTOM, created.source)
        assertEquals(TrackingType.WEIGHT_REPS, created.trackingType)
        assertEquals(listOf(Muscle.SHOULDERS, Muscle.CORE), created.primaryMuscles)
        assertEquals(listOf(Muscle.GLUTES), created.secondaryMuscles)
        assertEquals(Equipment.KETTLEBELL, created.equipment)
        assertEquals(90, created.defaultRestSec)
        assertNotNull(created.note)
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(20_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}
