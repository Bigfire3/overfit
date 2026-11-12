package app.overfit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.platform.testTag
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
import app.overfit.ui.components.ConfirmArea
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.CONFIRM_ARMED_MILLIS
import app.overfit.ui.components.ConfirmTags
import app.overfit.ui.components.HeaderDeleteButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.overfit.ui.components.SetRowSurface
import app.overfit.ui.components.Text
import app.overfit.ui.theme.OverfitTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Feedback round 3 (Session AE): confirm buttons disarm each other and on a tap elsewhere, sets are
 * deleted by swiping left, screen changes are short and swallow double taps, tab switches are instant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class UiFeedbackTest {
    // The app of launchApp() (set there); closed after the composition is gone, see AppTeardown.
    private var appData: AppData? = null
    private var appDatabase: OverfitDatabase? = null

    @get:Rule(order = 0)
    val teardown = AppTeardown({ appData }, { appDatabase ?: error("no database") }, closeDatabase = { appDatabase != null })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun confirmButton_firstTapArms_secondTapRuns_tapElsewhereDisarms() {
        var deleted = 0
        var finished = 0
        compose.setContent {
            OverfitTheme {
                ConfirmArea(Modifier.fillMaxSize()) {
                    Column {
                        Text("Irgendwo", Modifier.testTag("elsewhere"))
                        ConfirmButton("Löschen", onConfirm = { deleted++ })
                        ConfirmButton("Beenden", onConfirm = { finished++ })
                        HeaderDeleteButton("Workout löschen", onConfirm = { deleted += 10 })
                    }
                }
            }
        }
        // First tap: armed (tag + screen-reader state), the label stays the same, nothing runs yet, no popup.
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithTag(ConfirmTags.armed).assertTextEquals("Löschen")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "zum Bestätigen erneut tippen"))
        assertEquals(0, deleted)
        // Tap elsewhere disarms; the next tap only arms again.
        compose.onNodeWithTag("elsewhere").performClick()
        compose.onAllNodesWithTag(ConfirmTags.armed).assertCountEquals(0)
        compose.onNodeWithText("Löschen").performClick()
        assertEquals(0, deleted)
        // Arming another button disarms the first one.
        compose.onNodeWithText("Beenden").performClick()
        compose.onNodeWithTag(ConfirmTags.armed).assertTextEquals("Beenden")
        compose.onAllNodesWithTag(ConfirmTags.armed).assertCountEquals(1)
        // Second tap runs the action once and disarms.
        compose.onNodeWithText("Beenden").performClick()
        assertEquals(1, finished)
        compose.onAllNodesWithTag(ConfirmTags.armed).assertCountEquals(0)
        // Icon button: the bin's description announces the armed state; second tap deletes.
        compose.onNodeWithContentDescription("Workout löschen").performClick()
        compose.onNodeWithContentDescription("Workout löschen – zum Bestätigen erneut tippen").assertExists()
        compose.onNodeWithTag(ConfirmTags.armed).performClick()
        assertEquals(10, deleted)
    }

    @Test
    fun confirmButton_disarmsAfterTimeout_andOnScroll() {
        var deleted = 0
        compose.setContent {
            OverfitTheme {
                ConfirmArea(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("scroll")) {
                        ConfirmButton("Löschen", onConfirm = { deleted++ })
                        androidx.compose.foundation.layout.Spacer(Modifier.height(2000.dp))
                    }
                }
            }
        }
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithTag(ConfirmTags.armed).assertExists()
        // Just before the timeout it is still armed, after it it falls back.
        compose.mainClock.advanceTimeBy(CONFIRM_ARMED_MILLIS - 500)
        compose.onNodeWithTag(ConfirmTags.armed).assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onAllNodesWithTag(ConfirmTags.armed).assertCountEquals(0)
        compose.onNodeWithText("Löschen").performClick()
        assertEquals(0, deleted)
        // Scrolling disarms as well.
        compose.onNodeWithTag(ConfirmTags.armed).assertExists()
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onAllNodesWithTag(ConfirmTags.armed).assertCountEquals(0)
        assertEquals(0, deleted)
    }

    @Test
    fun setRow_swipeLeftDeletes_swipeRightDoesNothing() {
        val sets = mutableStateListOf(1, 2, 3)
        compose.setContent {
            OverfitTheme {
                Column {
                    sets.toList().forEach { n ->
                        androidx.compose.runtime.key(n) {
                            SetRowSurface("Satz $n entfernen", { sets.remove(n) }, Modifier.testTag("row:$n")) { Text("Satz $n") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("row:2").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf(1, 2, 3), sets.toList())
        compose.onNodeWithTag("row:2").performTouchInput { swipeLeft() }
        compose.waitUntilState { sets.toList() == listOf(1, 3) }
        compose.onAllNodesWithTag("row:2").assertCountEquals(0)
        // The row that moved up is not left half swiped.
        compose.onNodeWithText("Satz 3").assertExists()
    }

    private fun launchApp(): AppDependencies {
        val kv = InMemoryKeyValueStore()
        val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
        val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
        val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
        appData = data; appDatabase = database
        store.setName("Fabi")
        store.completeOnboarding()
        runBlocking { data.seedCatalog(); data.seedSamplesOnce() }
        val deps = AppDependencies(store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test", today = { LocalDate(2026, 11, 3) })
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Push").fetchSemanticsNodes().isNotEmpty() }
        return deps
    }

    @Test
    fun screenChange_isShort_andSwallowsADoubleTapOnBack() {
        launchApp()
        // From the workout screen into the exercise details (a screen on top of the workout), then back.
        compose.onNodeWithText("Push").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Starten").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Details zu", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription("Details zu", substring = true))[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("FORTSCHRITT").fetchSemanticsNodes().isNotEmpty() }

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(32)
        // Mid-transition: taps are swallowed, so a second "back" cannot pop the workout screen as well.
        compose.onNodeWithTag("nav-transition").assertExists()
        compose.onAllNodesWithTag("nav-transition")[0].performClick()
        // The whole fade-through is over well within 300 ms.
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodesWithTag("nav-transition").assertCountEquals(0)
        // Still on the workout screen (its header has the ⋮ with share and delete), not on the Training tab.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Starten").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Weitere Optionen").assertExists()
    }

    @Test
    fun deletingAWorkout_landsInTheWorkoutList_notOnABlackScreen() {
        launchApp()
        compose.onNodeWithText("Push").performClick()
        // Delete sits in the ⋮ and keeps its two taps: first tap arms the entry, the second deletes.
        compose.openMenuEntry("Workout löschen")
        compose.onNodeWithText("Workout löschen – zum Bestätigen erneut tippen").assertExists()
        compose.onNodeWithTag("confirm:armed").performClick()
        // Back on the training tab (list with Pull and Legs), not on an empty screen.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pull").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Legs").assertCountEquals(1)
        compose.onAllNodesWithText("Push").assertCountEquals(0)
        compose.onNodeWithTag("tab:PHOTOS").assertExists()
    }

    @Test
    fun tabSwitch_isInstant() {
        launchApp()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tab:PHOTOS").performClick()
        // No transition between tabs: within 50 ms (a screen change fades for 240 ms) the training tab is gone.
        compose.mainClock.advanceTimeBy(50)
        compose.onAllNodesWithText("Push", substring = true).assertCountEquals(0)
        compose.onAllNodesWithTag("nav-transition").assertCountEquals(0)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun workoutScreen_swipeDeletesSet_undoBringsItBack_shareAndDeleteInHeader() {
        val deps = launchApp()
        compose.onNodeWithText("Push").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Starten").fetchSemanticsNodes().isNotEmpty() }
        // Workout screen = editor: share and delete in the ⋮ top right (UI audit 12.11.), no pencil.
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Teilen").assertExists()
        compose.onNodeWithText("Workout löschen").assertExists()
        compose.onNodeWithText("Teilen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Link kopieren").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Starten").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription("Bearbeiten")).assertCountEquals(0)

        val pushId = runBlocking { deps.data.templates.templates.first().first { it.name == "Push" }.id }
        fun storedSets() = runBlocking { deps.data.templates.get(pushId) }!!.exercises[0].sets.size
        assertEquals(4, storedSets())
        compose.onAllNodesWithText("4 Sätze", substring = true)[0].assertExists()
        compose.onAllNodesWithTag("set-row:4")[0].performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Satz gelöscht").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("3 Sätze", substring = true)[0].assertExists()
        // Stored right away (autosave), undo brings it back and stores that as well.
        compose.waitUntilState(10_000) { storedSets() == 3 }
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Satz gelöscht").fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithText("4 Sätze", substring = true)[0].assertExists()
        compose.waitUntilState(10_000) { storedSets() == 4 }
    }

    @Test
    fun newWorkoutEditor_hasNoShareOrDelete() {
        launchApp()
        compose.onNodeWithContentDescription("Neues Workout").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Workout erstellen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Workout erstellen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Name des Workouts").fetchSemanticsNodes().isNotEmpty() }
        // A new workout is not stored yet: nothing to share or delete, "Speichern" instead of "Starten".
        compose.onNodeWithText("Speichern").assertExists()
        compose.onAllNodesWithText("Starten").assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Teilen")).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Weitere Optionen")).assertCountEquals(0)
    }
}
