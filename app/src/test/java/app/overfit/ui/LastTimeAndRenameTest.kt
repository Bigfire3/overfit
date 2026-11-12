package app.overfit.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
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
import app.overfit.domain.SetValues
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
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
 * Fabian's two wishes in the real app: in a saved workout each exercise card shows the sets from last time
 * ("Letztes Mal: …", nothing if never trained), and a past training can be renamed from its detail page (only the training, not the
 * saved workout). With `SCREENSHOT_DIR` set it also writes the screenshots for the PR (dark, 360 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class LastTimeAndRenameTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(
        database, kv,
        readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
        readSample = { File("../shared/src/commonMain/composeResources/files/samples/$it.json").readBytes() },
    )
    private val out = System.getenv("SCREENSHOT_DIR")?.let { File(it).also(File::mkdirs) }
    private var n = 0

    private fun shot(name: String) {
        val dir = out ?: return
        compose.waitForIdle()
        n++
        File(dir, "%02d-%s-dunkel-360.png".format(n, name)).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) {
        try { compose.waitUntil(20_000) { shown(text, substring) } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    private fun back() = compose.onNodeWithContentDescription("Zurück").performClick()

    @Test
    fun lastValuesInTheWorkoutAndRenamingAPastTraining() {
        store.setName("Fabi")
        store.completeOnboarding()
        // A finished "Push" from today: the first exercise with two sets done, the others not touched.
        val (first, second) = runBlocking {
            data.seedCatalog().getOrThrow()
            data.importer.addSamples()
            val push = data.templates.templates.first().first { it.name == "Push" }
            val template = data.templates.get(push.id)!!
            var w = Workout.fromTemplate(template, System.currentTimeMillis() - 3_600_000, id = "past")
            val sets = w.exercises[0].sets
            w = w.setValues(sets[0].id, SetValues(62.5, 8)).completeSet(sets[0].id, w.startedAt + 60_000)
            w = w.setValues(sets[1].id, SetValues(65.0, 6)).completeSet(sets[1].id, w.startedAt + 120_000)
            data.workouts.finish(w, endedAt = w.startedAt + 1_800_000)
            template.exercises[0].exercise.name to template.exercises[1].exercise.name
        }
        val deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test", share = NoopPlatformShare(),
            today = { realToday() }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(), kv = kv,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        waitFor("Push")

        // 1. Saved workout: each exercise card says what was done last time, right under its name.
        compose.onAllNodes(hasText("Push"))[0].performScrollTo().performClick()
        waitFor("Starten")
        waitFor(first)
        val firstCaption = hasText("Letztes Mal: 62,5 kg × 8, 65 × 6") and hasAnySibling(hasText(first))
        compose.waitUntil(20_000) { compose.onAllNodes(firstCaption, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        shot("workout-letzte-werte")
        // The second exercise was not trained yet: no "last time" line on its card.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(second, substring = true))
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(second), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("Letztes Mal", substring = true) and hasAnySibling(hasText(second)), useUnmergedTree = true)
            .assertCountEquals(0)
        back()
        compose.waitForTrainingTab()

        // 2. Calendar → day → training: tap the name, rename, save.
        compose.onAllNodes(hasText("Kalender") and hasClickAction())[0].performClick()
        compose.openCalendarDay(realToday().dayOfMonth)
        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Training bearbeiten").fetchSemanticsNodes().isNotEmpty() }
        shot("training-detail")
        compose.onNode(hasText("Push") and hasClickAction()).performClick()
        waitFor("Statistiken und Bestwerte", substring = true)
        compose.onNode(hasSetTextAction() and hasText("Push")).performTextReplacement("Brust Montag")
        shot("training-umbenennen")
        compose.onNodeWithText("Speichern").performClick()
        waitFor("Brust Montag")
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Training bearbeiten").fetchSemanticsNodes().isNotEmpty() }
        shot("training-umbenannt")
        back()
        waitFor("Brust Montag")
        shot("kalender-tag-umbenannt")

        runBlocking {
            assertEquals("Brust Montag", data.workouts.getFinished("past")!!.workout.name)
            assertEquals("Brust Montag", data.workouts.history.first().single().name)
            // The saved workout keeps its name.
            assertEquals(1, data.templates.templates.first().count { it.name == "Push" })
        }
    }
}
