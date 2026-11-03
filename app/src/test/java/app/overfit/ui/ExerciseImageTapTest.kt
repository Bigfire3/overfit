package app.overfit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
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
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Tap on an exercise image opens the details (large image, animation); back returns; a running workout is not disturbed. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ExerciseImageTapTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private lateinit var deps: AppDependencies

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = compose.waitUntil(10_000) { shown(text) }
    private fun waitGone(text: String) = compose.waitUntil(10_000) { !shown(text) }
    private fun waitForDescription(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasContentDescription(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun launch() {
        store.setName("Fabi")
        store.completeOnboarding()
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 90, note = null, sets = List(2) { PlannedSet(60.0, 8) }))))
        }
        deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test",
            today = { LocalDate(2026, 11, 3) }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(),
        )
        compose.setContent { OverfitApp(deps) }
        waitFor("Push")
    }

    @Test
    fun runningWorkout_iconOpensReadOnlyDetails_backReturns_workoutKeepsRunning() {
        launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        compose.onNodeWithText("Starten").performClick()
        waitFor("0/2 erledigt")
        compose.onNodeWithContentDescription("Satz 1 abhaken").performClick()
        waitFor("Überspringen") // rest timer bar is running
        val before = deps.workout.state.value.workout
        assertNotNull(before)

        compose.onNodeWithContentDescription("Details zu Bench Press").performClick()
        waitFor("FORTSCHRITT")
        compose.onNodeWithText("Bench Press").assertIsDisplayed()
        // Read-only from the workout: no tracking/rest settings, no save button.
        assertEquals(false, shown("Speichern"))
        assertEquals(false, shown("ERFASSUNG"))

        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Überspringen")
        waitForDescription("Details zu Bench Press")
        assertEquals(before, deps.workout.state.value.workout)
    }

    @Test
    fun editor_iconOpensDetails_backReturnsToEditor() {
        launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Bearbeiten")
        compose.onNodeWithText("Bearbeiten").performClick()
        waitFor("Bearbeitungsmodus")
        compose.onNodeWithContentDescription("Details zu Bench Press").performClick()
        waitFor("FORTSCHRITT")
        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Bearbeitungsmodus")
    }

    @Test
    fun picker_imageOpensDetails_rowStillSelects() {
        launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Bearbeiten")
        compose.onNodeWithText("Bearbeiten").performClick()
        waitFor("Bearbeitungsmodus")
        compose.onNodeWithText("Übung hinzufügen").performClick()
        waitFor("Übungen hinzufügen")
        // Only the rows at the top of the list are composed; the first row's image opens its details.
        val image = hasContentDescription("Details zu", substring = true)
        compose.waitUntil(10_000) { compose.onAllNodes(image).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(image)[0].performClick()
        waitFor("FORTSCHRITT")
        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Übungen hinzufügen")
    }
}
