package app.overfit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
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

    private var pushTemplate: Template? = null

    private fun launch(show: Boolean = true) {
        store.setName("Fabi")
        store.completeOnboarding()
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            val id = data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 90, note = null, sets = List(2) { PlannedSet(60.0, 8) }))))
            pushTemplate = data.templates.get(id)
        }
        deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test",
            today = { LocalDate(2026, 11, 3) }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(),
        )
        if (!show) return
        compose.setContent { OverfitApp(deps) }
        waitFor("Push")
    }

    @Test
    fun workoutScreen_exerciseCardImageOpensDetails_backReturns() {
        launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        // The exercise is a card with its image, as in the running workout.
        waitFor("2 Sätze · Pause 1:30")
        compose.onNodeWithContentDescription("Details zu Bench Press").performClick()
        waitFor("FORTSCHRITT")
        // The workout screen is editable, so are the exercise details opened from it (not read-only any more).
        assertEquals(true, shown("ERFASSUNG"))
        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Starten")
    }

    @Test
    fun trainingDetail_exerciseCardImageOpensDetails() {
        launch(show = false)
        // Straight into the repository (the controller's finish joins a Main-thread job, which a blocked test thread would deadlock).
        val id = runBlocking {
            val start = 1_793_700_000_000L
            val w0 = app.overfit.domain.Workout.fromTemplate(requireNotNull(pushTemplate), start)
            val w = w0.completeSet(w0.exercises[0].sets[0].id, start + 60_000)
            data.workouts.save(w)
            data.workouts.finish(w, start + 3_600_000)
            w.id
        }
        var opened: String? = null
        compose.setContent {
            app.overfit.ui.theme.OverfitTheme {
                app.overfit.ui.history.WorkoutDetailScreen(
                    id, data.workouts, data.templates, WeightUnit.KG, Language.DE, kotlinx.coroutines.MainScope(),
                    onBack = {}, onOpenPhoto = {}, onTemplateCreated = {}, onShare = {},
                    onExerciseDetails = { opened = it },
                )
            }
        }
        waitFor("1/2 erledigt")
        // The card sits below "Trainierte Muskeln", out of view on a small screen.
        compose.onNodeWithContentDescription("Details zu Bench Press").performScrollTo().performClick()
        assertEquals("wg:bench-press", opened)
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
    fun workoutScreen_editSurvivesDetails_backReturnsToWorkout() {
        launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        // Edited in place (no pencil); the change must survive a trip to the exercise details.
        compose.onNodeWithContentDescription("Gewicht Satz 1").performTextReplacement("62,5")
        compose.onNodeWithContentDescription("Details zu Bench Press").performClick()
        waitFor("FORTSCHRITT")
        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Starten")
        waitFor("62,5")
        val id = requireNotNull(pushTemplate).id
        compose.waitUntilState(10_000) { runBlocking { data.templates.get(id) }?.exercises?.single()?.sets?.first()?.weightKg == 62.5 }
    }

    @Test
    fun picker_imageOpensDetails_rowStillSelects() {
        launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        waitFor("Übung hinzufügen")
        compose.onNodeWithText("Übung hinzufügen").performScrollTo().performClick()
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
