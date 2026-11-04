package app.overfit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
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
import app.overfit.ui.components.Confirm
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
    fun confirmButton_asksInAPopup_cancelKeeps_confirmRuns() {
        var deleted = 0
        compose.setContent {
            OverfitTheme(app.overfit.domain.ThemeMode.DARK) {
                ConfirmArea(Modifier.fillMaxSize()) {
                    Column {
                        Text("Irgendwo", Modifier.testTag("elsewhere"))
                        ConfirmButton("Löschen", "Wirklich löschen?", onConfirm = { deleted++ })
                        ConfirmButton("Beenden", "Wirklich beenden?", onConfirm = {}, kind = Confirm.Positive)
                    }
                }
            }
        }
        // The tap opens a popup with the question; "Abbrechen" closes it without deleting.
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Wirklich löschen?").assertExists()
        compose.onNodeWithTag("dialog:cancel").performClick()
        compose.onAllNodesWithText("Wirklich löschen?").assertCountEquals(0)
        assertEquals(0, deleted)
        // The other button asks its own question; its action runs only from the popup.
        compose.onNodeWithText("Beenden").performClick()
        compose.onNodeWithText("Wirklich beenden?").assertExists()
        compose.onNodeWithTag("dialog:cancel").performClick()
        // Confirm in the popup runs the action once and closes it.
        compose.onAllNodesWithText("Löschen")[0].performClick()
        compose.onNodeWithTag("dialog:confirm").performClick()
        assertEquals(1, deleted)
        compose.onAllNodesWithText("Wirklich löschen?").assertCountEquals(0)
    }

    @Test
    fun setRow_swipeLeftDeletes_swipeRightDoesNothing() {
        val sets = mutableStateListOf(1, 2, 3)
        compose.setContent {
            OverfitTheme(app.overfit.domain.ThemeMode.DARK) {
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
        compose.onNodeWithText("Push").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bearbeiten").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bearbeiten").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bearbeitungsmodus").fetchSemanticsNodes().isNotEmpty() }

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
        // The workout page reloads its plan from Room (background thread, not covered by waitForIdle): wait for its
        // content instead of asserting at once, which failed now and then under load in the full suite.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Starten").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bearbeiten").assertExists()
    }

    @Test
    fun deletingAWorkout_landsInTheWorkoutList_notOnABlackScreen() {
        launchApp()
        compose.onNodeWithText("Push").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bearbeiten").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Workout löschen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Wirklich löschen?", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dialog:confirm").performClick()
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
    fun editor_swipeDeletesSet_undoBringsItBack_noShareOrDeleteInEditMode() {
        launchApp()
        compose.onNodeWithText("Push").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bearbeiten").fetchSemanticsNodes().isNotEmpty() }
        // Workout screen: share and delete as icons top right.
        compose.onNodeWithContentDescription("Teilen").assertExists()
        compose.onNodeWithContentDescription("Workout löschen").assertExists()
        compose.onNodeWithText("Bearbeiten").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bearbeitungsmodus").fetchSemanticsNodes().isNotEmpty() }
        // Edit mode: neither share nor delete.
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Teilen")).assertCountEquals(0)
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Workout löschen")).assertCountEquals(0)

        compose.onAllNodesWithText("4 Sätze", substring = true)[0].assertExists()
        compose.onAllNodesWithTag("set-row:4")[0].performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Satz gelöscht").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("3 Sätze", substring = true)[0].assertExists()
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Satz gelöscht").fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithText("4 Sätze", substring = true)[0].assertExists()
    }
}
