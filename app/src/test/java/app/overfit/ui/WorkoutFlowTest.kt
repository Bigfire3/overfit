package app.overfit.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
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
import kotlinx.datetime.LocalDate
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
    @get:Rule
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
            today = { LocalDate(2026, 11, 3) },
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
        compose.onNodeWithText("Beenden? Nochmal tippen").performClick()
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

        // History tab shows the workout; the detail has the note
        compose.onNodeWithText("Verlauf").performClick()
        waitFor("Push")
        compose.onAllNodes(hasText("Push")).onFirst().performClick()
        waitFor("Stark heute")
        // Sauna can be changed afterwards in the detail.
        compose.onNodeWithText("Sauna").performScrollTo().assertIsOn().performClick()
        compose.waitUntilState(5_000) { runBlocking { data.workouts.getFinished(id)?.sauna } == false }
        compose.onNodeWithText("Sauna").assertIsOff()
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(20_000) {
        compose.onAllNodes(hasText(text, substring = substring, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
    }
}
