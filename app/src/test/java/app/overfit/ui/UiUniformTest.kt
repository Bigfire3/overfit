package app.overfit.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import app.overfit.ui.components.HEADER_ACTIONS_TAG
import app.overfit.ui.components.MAX_HEADER_ACTIONS
import app.overfit.ui.components.MORE_MENU_TAG
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

/**
 * UI audit 12.11. (Fabian: "dass alle Symbole … gleich groß sind und alles ein bisschen einheitlich ist, die Menüs nicht
 * so überladen"): on every main screen the header actions are 48 dp touch targets on one line, at most
 * [MAX_HEADER_ACTIONS] of them, icon buttons are exactly 48 × 48, and the coach icon sits in the top right corner.
 * Every icon-only button on those screens is at least 48 dp. While the coach lies over a screen, screen readers
 * only reach the coach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class UiUniformTest {
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

    private val density get() = compose.density.density
    private fun Float.dp() = this / density
    private fun near(a: Float, b: Float) = abs(a - b) <= 0.5f
    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) {
        try { compose.waitUntil(20_000) { shown(text) } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }
    private fun waitForDescription(d: String) = compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(d).fetchSemanticsNodes().isNotEmpty() }
    private fun idle() {
        compose.waitForIdle()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("nav-transition")).fetchSemanticsNodes().isEmpty() }
    }
    private fun click(text: String) {
        if (!shown(text)) compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) {}
        node.performClick()
    }
    private fun tab(tag: String) {
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
        idle()
    }
    private fun back() { compose.onNodeWithContentDescription("Zurück").performClick(); idle() }

    private fun Rect.describe() = "%.1f × %.1f dp".format(width.dp(), height.dp())
    private fun SemanticsNode.label() =
        (config.getOrNull(SemanticsProperties.ContentDescription) ?: config.getOrNull(SemanticsProperties.Text)?.map { it.text }).orEmpty().joinToString()

    /** Header of the screen on show: one action group, ≤ 3 targets, all ≥ 48 dp on one line, icons 48 × 48, coach last. */
    private fun checkHeader(screen: String, coach: Boolean) {
        idle()
        val groups = compose.onAllNodes(hasTestTag(HEADER_ACTIONS_TAG), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("$screen: header action groups", 1, groups.size)
        val actions = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(HEADER_ACTIONS_TAG)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("$screen: ${actions.size} header actions (${actions.map { it.label() }})", actions.size <= MAX_HEADER_ACTIONS)
        actions.forEach { n ->
            val b = n.boundsInRoot
            assertTrue("$screen: ${n.label()} is ${b.describe()}\n" + compose.onNode(hasTestTag(HEADER_ACTIONS_TAG), useUnmergedTree = true).printToString(), b.width.dp() >= 47.5f && b.height.dp() >= 47.5f)
            assertTrue("$screen: ${n.label()} off the line", near(actions[0].boundsInRoot.center.y.dp(), b.center.y.dp()))
            val iconOnly = n.config.getOrNull(SemanticsProperties.Text) == null
            if (iconOnly) assertTrue("$screen: icon ${n.label()} is ${b.describe()}", near(b.width.dp(), 48f) && near(b.height.dp(), 48f))
        }
        val coachNodes = compose.onAllNodes(hasTestTag("coach:open"), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("$screen: coach icon", if (coach) 1 else 0, coachNodes.size)
        if (coach) {
            val right = actions.maxOf { it.boundsInRoot.right }
            assertTrue("$screen: coach icon is not in the corner", near(coachNodes[0].boundsInRoot.right.dp(), right.dp()))
        }
        // The back arrow (if any) is a 48 dp target as well.
        compose.onAllNodesWithContentDescription("Zurück").fetchSemanticsNodes().forEach {
            assertTrue("$screen: back is ${it.boundsInRoot.describe()}", it.boundsInRoot.width.dp() >= 47.5f && it.boundsInRoot.height.dp() >= 47.5f)
        }
        checkIconButtons(screen)
    }

    /** Every tappable thing without text but with a description (icon button, check circle, ▶) is ≥ 48 dp to tap. */
    private fun checkIconButtons(screen: String) {
        val small = compose.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.Text) == null && it.config.getOrNull(SemanticsProperties.ContentDescription) != null }
            // Layout size, not the visible bounds: a target cut off at the edge of a scrolled list is not too small.
            .filter { it.size.width.toFloat().dp() < 47.5f || it.size.height.toFloat().dp() < 47.5f }
        assertTrue("$screen: small touch targets ${small.map { it.label() + " " + "%.1f × %.1f dp".format(it.size.width.toFloat().dp(), it.size.height.toFloat().dp()) }}", small.isEmpty())
    }

    @Test fun mainScreensShareOneHeaderPattern() {
        store.setName("Fabi")
        store.completeOnboarding()
        store.markCoachStorySeen()
        val deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test", share = NoopPlatformShare(),
            today = { realToday() }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(), kv = kv,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        runBlocking { data.importer.addSamples() }
        waitFor("Legs")
        checkHeader("Workouts", coach = false)

        // Exercise catalogue and an exercise.
        compose.onNodeWithContentDescription("Übungen").performClick()
        waitFor("Eigene Übung")
        checkHeader("Übungen", coach = false)
        back()
        compose.waitForTrainingTab()

        // Workout (view = editor): [⋮ share, delete] [coach].
        compose.onAllNodes(hasText("Push"))[0].performScrollTo().performClick()
        waitFor("Starten")
        checkHeader("Workout", coach = true)
        // Delete in the ⋮ keeps its two taps: the first one only arms it, the menu stays open.
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Workout löschen", substring = true).performClick()
        compose.onNodeWithTag("confirm:armed").assertExists()
        compose.onNodeWithTag(MORE_MENU_TAG).assertExists()
        compose.onNodeWithText("Teilen", substring = true).performClick()
        waitFor("Link kopieren")
        back()
        waitFor("Starten")

        // Running workout: [finish] [coach].
        click("Starten")
        waitFor("Nächsten Satz loggen")
        checkHeader("Training", coach = true)
        // The coach layer hides the training from screen readers, and gives it back when closed.
        compose.onNodeWithTag("coach:open").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coach:close")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTrue("training reachable under the coach", compose.onAllNodes(hasText("Nächsten Satz loggen")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("coach:close").performClick()
        waitFor("Nächsten Satz loggen")

        compose.onAllNodesWithContentDescription("Satz 1 abhaken")[0].performClick()
        waitFor("Überspringen")
        click("Überspringen")
        click("Beenden")
        compose.onNodeWithTag("confirm:armed").performClick()
        waitFor("Training beendet")
        compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        click("Speichern")
        compose.waitForTrainingTab()

        // Calendar, day, training.
        compose.openCalendarDay(realToday().dayOfMonth)
        waitFor("Push")
        checkHeader("Tag", coach = true)
        compose.onAllNodes(hasText("Push"))[0].performClick()
        waitForDescription("Training bearbeiten")
        checkHeader("Trainingsdetail", coach = true)
        back()
        back()
        checkHeader("Kalender", coach = false)

        tab("tab:COACH")
        waitForDescription("Neuer Chat")
        checkHeader("Coach", coach = false)
        compose.onNodeWithTag("coach:new").assertExists()
    }
}
