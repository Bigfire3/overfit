package app.overfit.ui

import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import app.overfit.domain.WeightUnit
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Evidence screenshots of the main screens in light and dark. Only runs when `SCREENSHOT_DIR` is set
 * (`SCREENSHOT_DIR=docs/screenshots/before ./gradlew … :app:testDebugUnitTest --tests '*ScreenshotTest*'`);
 * otherwise it is skipped, so normal builds are not slowed down.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
open class ScreenshotTest {
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
    private val share = NoopPlatformShare()
    private lateinit var out: File
    private var n = 0
    private lateinit var suffix: String

    private fun shot(name: String) {
        compose.waitForIdle()
        n++
        val file = File(out, "%02d-%s-%s.png".format(n, name, suffix))
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) {
        try { compose.waitUntil(20_000) { shown(text) } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }
    private fun click(text: String) {
        if (!shown(text)) compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) {}
        node.performClick()
    }
    private fun tab(name: String) {
        val tabNode = hasText(name) and hasClickAction()
        repeat(4) {
            if (compose.onAllNodes(tabNode).fetchSemanticsNodes().isNotEmpty()) return@repeat
            if (compose.onAllNodes(hasContentDescription("Zurück")).fetchSemanticsNodes().isNotEmpty()) {
                try { compose.onNodeWithContentDescription("Zurück").performClick() } catch (_: AssertionError) {}
            }
            compose.waitForIdle()
        }
        compose.waitUntil(10_000) { compose.onAllNodes(tabNode).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabNode).performClick()
    }
    private fun back() { compose.onNodeWithContentDescription("Zurück").performClick() }

    @Test fun dark() = run("dunkel")

    private fun run(label: String) {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        out = File(dir!!).also { it.mkdirs() }
        suffix = label
        store.setName("Fabi")
        store.completeOnboarding()
        val deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test", share = share,
            today = { realToday() }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(), kv = kv,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        shot("training-leer")
        // The sample workouts normally arrive at the first start; here they are added after the empty-state shot.
        runBlocking { data.importer.addSamples() }
        waitFor("Legs")
        shot("training")

        // Workout detail
        compose.onAllNodes(hasText("Push"))[0].performScrollTo().performClick()
        waitFor("Starten")
        shot("workout-detail")
        // Share (icon top right)
        compose.onNodeWithContentDescription("Teilen").performClick()
        waitFor("Link kopieren")
        shot("teilen")
        back()
        // Edit mode: blocks per exercise, set rows (wait until the workout page is back after the share screen)
        waitFor("Bearbeiten")
        click("Bearbeiten")
        waitFor("Bearbeitungsmodus")
        shot("workout-bearbeiten")
        back()
        waitFor("Starten")
        // Run it
        click("Starten")
        waitFor("Nächsten Satz loggen")
        shot("training-laufend")
        compose.onAllNodesWithContentDescription("Satz 1 abhaken")[0].performClick()
        waitFor("Überspringen")
        shot("training-pause")
        click("Überspringen")
        // Finish flow
        click("Beenden") // popup: its own screenshot in DialogShotTest (Robolectric does not draw dialog windows)
        compose.onNodeWithTag("dialog:confirm").performClick()
        waitFor("Training beendet")
        compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        shot("beenden")
        click("Speichern")
        compose.waitForTrainingTab()
        tab("Kalender")
        waitFor("1 Training")
        shot("kalender")
        compose.openCalendarDay(realToday().dayOfMonth)
        waitFor("Push")
        shot("kalender-tag")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Teilen").fetchSemanticsNodes().isNotEmpty() }
        shot("training-detail")
        compose.onNodeWithContentDescription("Training bearbeiten").performClick()
        waitFor("Satz hinzufügen")
        shot("training-nachtraeglich-bearbeiten")
        back()
        back()
        back()
        tab("Profil")
        shot("profil")
        // Coach tab without its first-time story (the story itself is not part of this tour).
        store.markCoachStorySeen()
        tab("Coach")
        compose.waitForIdle()
        shot("coach")
        tab("Profil")
        tab("Workouts")
        compose.onNodeWithContentDescription("Übungen").performClick()
        waitFor("Eigene Übung")
        shot("uebungskatalog")
        back()
        compose.waitForTrainingTab()
        compose.onNodeWithContentDescription("Neues Workout").performClick()
        waitFor("Workout importieren")
        shot("neues-workout-menue")
        click("Workout importieren")
        shot("import")
    }
}

/** Same tour on a narrow phone (360 dp), for the UI audit. */
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest360 : ScreenshotTest()
