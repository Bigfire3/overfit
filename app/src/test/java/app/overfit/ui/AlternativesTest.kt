package app.overfit.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import app.overfit.domain.Workout
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Exercise alternatives (A/B weeks): added in the editor via ⋮ → "Alternative hinzufügen", and in the training the
 * variant not done last time is chosen; chips switch it until a set is done. Screenshots with `SCREENSHOT_DIR`
 * (docs/screenshots/workout-alternativen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlternativesTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    private fun launch(): AppDependencies {
        val deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test",
            today = { realToday() }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(),
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        return deps
    }

    private fun onboard() {
        store.setName("Fabi")
        store.completeOnboarding()
    }

    @Test
    fun editorAddsAlternative() {
        onboard()
        val id = runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 60, note = null, sets = List(2) { PlannedSet(60.0, 8) }))))
        }
        launch()
        waitFor("Push")
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        waitFor("Bench Press", substring = true)
        compose.onAllNodes(hasContentDescription("Weitere Aktionen für Bench Press", substring = true))[0].performClick()
        waitFor("Alternative hinzufügen")
        compose.onNodeWithText("Alternative hinzufügen").performClick()
        waitFor("Suchen, z. B. Klimmzug")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("belt")
        waitFor("Belt Squat")
        compose.onAllNodes(hasText("Belt Squat"))[0].performClick()
        waitFor("Starten")
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("alternative:wg:belt-squat")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("alternative:wg:belt-squat", useUnmergedTree = true).assertExists()
        waitFor("Alternative")
        shot("editor-alternative")

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForTrainingTab()
        compose.waitUntilState(10_000) {
            runBlocking { data.templates.get(id)!! }.exercises.single().alternatives.map { it.exercise.id } == listOf("wg:belt-squat")
        }
        val saved = runBlocking { data.templates.get(id)!! }.exercises.single()
        assertEquals("wg:bench-press", saved.exercise.id)
        assertEquals(Workout.DEFAULT_SETS, saved.alternatives.single().sets.size)
    }

    @Test
    fun trainingRotatesAndChipsSwitch() {
        onboard()
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            val belt = requireNotNull(data.exercises.get("wg:belt-squat"))
            val t = Template(
                0, "Push", null,
                listOf(
                    TemplateExercise(
                        bench, restSec = 0, note = null, sets = List(2) { PlannedSet(60.0, 8) },
                        alternatives = listOf(TemplateExercise(belt, restSec = 0, note = null, sets = List(3) { PlannedSet(80.0, 5) })),
                    ),
                ),
            )
            val saved = t.copy(id = data.templates.save(t))
            // Last time: variant A (bench) done.
            val start = System.currentTimeMillis() - 2 * 86_400_000L
            val last = Workout.fromTemplate(saved, start, id = "old")
            val done = last.completeSet(last.exercises[0].sets[0].id, start + 60_000)
            data.workouts.save(done)
            data.workouts.finish(done, start + 3_600_000)
        }
        val deps = launch()
        compose.onNodeWithContentDescription("Push starten").performClick()
        waitFor("0/3 erledigt", substring = true)
        val entryId = deps.workout.state.value.workout!!.exercises.single().id
        val chips = hasAnyAncestor(hasTestTag("variants:$entryId"))
        // Variant B (belt squat) is chosen, both chips are shown.
        compose.onNode(chips and hasText("Belt Squat") and isSelected()).assertExists()
        compose.onNode(chips and hasText("Bench Press") and isSelected().not()).assertExists()
        shot("training-variante")

        // Chip A switches back to bench press (its 2 planned sets).
        compose.onNode(chips and hasText("Bench Press")).performClick()
        compose.waitUntilState(10_000) { deps.workout.state.value.workout!!.exercises.single().exercise.id == "wg:bench-press" }
        waitFor("0/2 erledigt", substring = true)
        compose.onNode(chips and hasText("Bench Press") and isSelected()).assertExists()

        // A done set ends switching: the chips are gone.
        compose.onNodeWithText("Nächsten Satz loggen").performClick()
        waitFor("1/2 erledigt", substring = true)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("variants:$entryId")).fetchSemanticsNodes().isEmpty() }
        assertEquals("wg:bench-press", deps.workout.state.value.workout!!.exercises.single().exercise.id)
    }

    private fun shot(name: String) {
        val dir = System.getenv("SCREENSHOT_DIR") ?: return
        val out = File(dir).apply { mkdirs() }
        compose.waitForIdle()
        File(out, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(20_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}
