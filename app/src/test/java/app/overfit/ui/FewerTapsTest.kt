package app.overfit.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Fewer taps (owner, 8.11.): start a workout straight from the list (1 tap), change a planned weight directly in
 * the workout view (no pencil, no "Save": autosave on back), the view keeps "Start" at the bottom.
 * Screenshots with `SCREENSHOT_DIR` (docs/screenshots/workout-direkt-bearbeiten).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FewerTapsTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    private fun launch(): Long {
        store.setName("Fabi")
        store.completeOnboarding()
        val id = runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 60, note = null, sets = List(2) { PlannedSet(60.0, 8) }))))
        }
        val deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test",
            today = { realToday() }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(),
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        waitFor("Push")
        return id
    }

    @Test
    fun playButtonOnTheCardStartsTheWorkout() {
        launch()
        shot("liste")
        compose.onNodeWithContentDescription("Push starten").performClick()
        waitFor("0/2 erledigt", substring = true)
    }

    @Test
    fun weightIsChangedDirectlyAndSavedOnBack() {
        val id = launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        // No pencil any more: the sets are right there.
        compose.onAllNodes(hasContentDescription("Bearbeiten")).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        compose.onNodeWithContentDescription("Gewicht Satz 1").performTextReplacement("62,5")
        shot("workout-ansicht")
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForTrainingTab()
        compose.waitUntilState { runBlocking { data.templates.get(id) }?.exercises?.single()?.sets?.first()?.weightKg == 62.5 }
    }

    @Test
    fun startAfterAnEditStartsWithTheNewValue() {
        val id = launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        compose.onNodeWithContentDescription("Gewicht Satz 2").performTextReplacement("65")
        compose.onNodeWithText("Starten").performClick()
        waitFor("0/2 erledigt", substring = true)
        assertEquals(65.0, runBlocking { data.templates.get(id) }!!.exercises.single().sets[1].weightKg)
    }

    @Test
    fun invalidValue_firstBackExplains_secondBackDiscards() {
        val id = launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        compose.onNodeWithContentDescription("Gewicht Satz 1").performTextReplacement("6,,5")
        compose.onNodeWithContentDescription("Zurück").performClick()
        // Not stored and not left: the footer says why.
        waitFor("Nicht gespeichert", substring = true)
        compose.onNodeWithText("Starten").assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForTrainingTab()
        assertEquals(60.0, runBlocking { data.templates.get(id) }!!.exercises.single().sets.first().weightKg)
    }

    @Test
    fun replaceLeftWithBack_thenAdd_addsInsteadOfReplacing() {
        val id = launch()
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        compose.onAllNodes(hasContentDescription("Weitere Aktionen für Bench Press", substring = true))[0].performClick()
        waitFor("Ersetzen")
        compose.onNodeWithText("Ersetzen").performClick()
        waitFor("Übung ersetzen")
        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Starten")
        compose.onNodeWithText("Übung hinzufügen").performScrollTo().performClick()
        waitFor("Übungen hinzufügen")
        // Pick some squat (any exercise but Bench Press): search for it, the list is long.
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("belt")
        waitFor("Belt Squat")
        compose.onAllNodes(hasText("Belt Squat"))[0].performClick()
        compose.onNodeWithText("1 hinzufügen").performClick()
        waitFor("Starten")
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitForTrainingTab()
        compose.waitUntilState { runBlocking { data.templates.get(id) }!!.exercises.size == 2 }
        assertEquals("wg:bench-press", runBlocking { data.templates.get(id) }!!.exercises.first().exercise.id)
    }

    private fun hasClickActionAndText(text: String) =
        androidx.compose.ui.test.hasClickAction() and hasText(text, substring = true)

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
