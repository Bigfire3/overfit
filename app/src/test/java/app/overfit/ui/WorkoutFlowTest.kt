package app.overfit.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.overfit.platform.HapticPattern
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Template → workout → check off a set → rest → log the rest → finish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class WorkoutFlowTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val haptics = RecordingHaptics()
    private val alerts = RecordingRestAlerts()

    @Test
    fun templateToFinishedWorkout() {
        store.setName("Fabi")
        store.completeOnboarding()
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
            today = { realToday() },
            haptics = haptics,
            restAlerts = alerts,
        )
        compose.setContent { OverfitApp(deps) }

        // Training tab → open the saved workout → "Starten"
        waitFor("Push", substring = true)
        compose.onNodeWithText("Push", substring = true).performClick()
        waitFor("Starten")
        compose.onNodeWithText("Starten").performClick()
        waitFor("0/2 erledigt", substring = true)
        assertEquals(1, alerts.permissionRequests)

        // Check off set 1: tick, rest of the template starts
        compose.onNodeWithContentDescription("Satz 1 abhaken").performClick()
        waitFor("Überspringen")
        assertTrue(HapticPattern.Tick in haptics.played)
        val alert = requireNotNull(alerts.pending)
        assertEquals(60_000L, alert.endsAt - alert.startedAt)
        compose.onNodeWithText("Nächste: Bench Press · Satz 2", substring = true).assertExists()

        // Skip the rest, log the last set with the footer button
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Überspringen")).fetchSemanticsNodes().isEmpty() }
        assertNull(alerts.pending)
        compose.onNodeWithText("Nächsten Satz loggen").performClick()
        waitFor("Alle Sätze erledigt")
        compose.onNodeWithText("Alle Sätze erledigt").assertIsNotEnabled()

        // Finish (two taps) → finish flow (summary, note) → save → start; stored as finished
        val id = requireNotNull(deps.workout.state.value.workout).id
        compose.onNodeWithText("Beenden").performClick()
        compose.onNodeWithTag("dialog:confirm").performClick()
        waitFor("Wie war das Training?")
        compose.waitUntilState(5_000) { runBlocking { data.workouts.status(id) } == SessionStatus.FINISHED }
        // The name field comes first, the note field second.
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Stark heute")
        // "Sauna" checkbox: off by default, checked here.
        compose.onNodeWithText("Sauna").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithText("Sauna").assertIsOn()
        compose.onNodeWithText("Speichern").performClick()
        compose.waitForTrainingTab()
        compose.waitUntilState(5_000) { runBlocking { data.workouts.getFinished(id)?.note } == "Stark heute" }
        compose.waitUntilState(5_000) { runBlocking { data.workouts.getFinished(id)?.sauna } == true }
        val saved = requireNotNull(runBlocking { data.workouts.get(id) })
        assertEquals(2, saved.completedSets)
        assertEquals(60.0, saved.exercises.single().sets.first().actual.weightKg)

        // Calendar → today → the day view lists the training → detail with the note (no History tab any more)
        compose.onAllNodes(hasText("Verlauf")).assertCountEquals(0)
        compose.openCalendarDay(realToday().dayOfMonth)
        waitFor("Push")
        compose.onAllNodes(hasText("Push")).onFirst().performClick()
        waitFor("Stark heute")

        // Edit afterwards: sauna off, set 1 from 60 to 62,5 kg, one more set → save
        compose.onNodeWithContentDescription("Training bearbeiten").performClick()
        waitFor("Statistiken und Bestwerte", substring = true)
        compose.onNodeWithText("Sauna").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Sauna").assertIsOff()
        compose.onNodeWithContentDescription("Gewicht Satz 1").performScrollTo().performTextReplacement("62,5")
        compose.onNodeWithText("Satz hinzufügen").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Gewicht Satz 3")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntilState(5_000) { runBlocking { data.workouts.getFinished(id)?.sauna } == false }
        val edited = requireNotNull(runBlocking { data.workouts.get(id) })
        assertEquals(3, edited.completedSets)
        assertEquals(62.5, edited.exercises.single().sets.first().actual.weightKg)
        assertEquals("Stark heute", runBlocking { data.workouts.getFinished(id)?.note })

        // Back on the detail (it reloads): 3 sets; back to the calendar: the day lost its sauna ring
        waitFor("3 von 3")
        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Trainings")
        compose.onNodeWithContentDescription("Zurück").performClick()
        val day = realToday().dayOfMonth
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Tag $day, Training")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription("Tag $day, Training, Sauna")).assertCountEquals(0)
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(20_000) {
        compose.onAllNodes(hasText(text, substring = substring, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
    }
}
