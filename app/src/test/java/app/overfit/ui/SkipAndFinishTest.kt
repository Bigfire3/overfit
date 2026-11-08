package app.overfit.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import app.overfit.ui.workout.FINISH_BUTTON_TAG
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
 * Running training: "Skip today" via the ⋮ menu (card dimmed, "Don't skip" undoes it), skipped sets do not count as
 * open, the floating button turns into "Finish workout" (double tap) and the saved training keeps the exercise as
 * skipped; the saved workout keeps it unchanged. Screenshots with `SCREENSHOT_DIR` (docs/screenshots/training-auslassen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkipAndFinishTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    @Test
    fun skipUndoFinish() {
        store.setName("Fabi")
        store.completeOnboarding()
        val templateId = runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            val squat = requireNotNull(data.exercises.get("wg:belt-squat"))
            data.templates.save(
                Template(0, "Push", null, listOf(
                    TemplateExercise(squat, restSec = 0, note = null, sets = List(3) { PlannedSet(80.0, 5) }),
                    TemplateExercise(bench, restSec = 0, note = null, sets = List(2) { PlannedSet(60.0, 8) }),
                )),
            )
        }
        val deps = AppDependencies(
            store,
            Providers(HttpClient(MockEngine { respond("{}") })),
            data,
            versionName = "0.1.test",
            today = { realToday() },
            haptics = RecordingHaptics(),
            restAlerts = RecordingRestAlerts(),
        )
        compose.setContent { OverfitApp(deps) }

        compose.waitForTrainingTab()
        val w0 = runBlocking { data.templates.get(templateId)!! }
        val squatName = w0.exercises[0].exercise.name
        waitFor("Push", substring = true)
        compose.onNodeWithText("Push", substring = true).performClick()
        waitFor("Starten")
        compose.onNodeWithText("Starten").performClick()
        waitFor("0/5 erledigt", substring = true)

        // Skip the second exercise: dimmed card with "Ausgelassen", its 3 sets no longer count.
        compose.onAllNodes(hasContentDescription("Weitere Aktionen für $squatName", substring = true))[0].performClick()
        waitFor("Heute auslassen")
        compose.onNodeWithText("Heute auslassen").performClick()
        waitFor("Nicht auslassen")
        waitFor("0/2 erledigt", substring = true)
        val entryId = deps.workout.state.value.workout!!.exercises[0].id
        compose.onNodeWithTag("skipped:$entryId").assertExists()

        // Undo, then skip again.
        compose.onNodeWithText("Nicht auslassen").performClick()
        waitFor("0/5 erledigt", substring = true)
        compose.onAllNodes(hasContentDescription("Weitere Aktionen für $squatName", substring = true))[0].performClick()
        waitFor("Heute auslassen")
        compose.onNodeWithText("Heute auslassen").performClick()
        waitFor("Nicht auslassen")

        // Log both bench sets with the floating button; then it says "Training beenden".
        compose.onNodeWithText("Nächsten Satz loggen").performClick()
        waitFor("1/2 erledigt", substring = true)
        compose.onNodeWithText("Nächsten Satz loggen").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(FINISH_BUTTON_TAG)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Training beenden").assertExists()
        shot("training-beenden")

        // Double tap finishes and saves; the training keeps the exercise as skipped, the workout keeps it unchanged.
        val id = deps.workout.state.value.workout!!.id
        compose.onNodeWithTag(FINISH_BUTTON_TAG).performClick()
        // First tap only arms it (green, same label); nothing is saved yet.
        compose.waitForIdle()
        assertEquals(SessionStatus.ACTIVE, runBlocking { data.workouts.status(id) })
        shot("training-beenden-scharf")
        compose.onNodeWithTag(FINISH_BUTTON_TAG).performClick()
        compose.waitUntilState(10_000) { runBlocking { data.workouts.status(id) } == SessionStatus.FINISHED }
        val saved = runBlocking { data.workouts.get(id)!! }
        assertEquals(listOf(true, false), saved.exercises.map { it.skipped })
        assertEquals(2, saved.completedSets)
        assertEquals(3, runBlocking { data.templates.get(templateId)!! }.exercises[0].sets.size)
        waitFor("1 Übung ausgelassen", substring = true)
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
        compose.onAllNodes(hasText(text, substring = substring, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
    }
}
