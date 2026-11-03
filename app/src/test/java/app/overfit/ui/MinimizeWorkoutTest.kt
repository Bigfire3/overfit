package app.overfit.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Minimize the workout, browse the app, bar with rest countdown, reopen (tap, swipe, back gesture, notification), conflict. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MinimizeWorkoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val alerts = RecordingRestAlerts()
    private lateinit var deps: AppDependencies

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) { shown(text, substring) }
    private fun waitGone(text: String) = compose.waitUntil(10_000) { !shown(text) }
    private fun waitForDescription(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private var backDispatcher: OnBackPressedDispatcher? = null
    private fun back() { compose.runOnIdle { backDispatcher!!.onBackPressed() } }
    private fun show() = compose.setContent {
        backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        OverfitApp(deps)
    }

    @Test
    fun minimizeBrowseAndReopen() {
        store.setName("Fabi")
        store.completeOnboarding()
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 90, note = null, sets = List(2) { PlannedSet(60.0, 8) }))))
        }
        deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test",
            today = { LocalDate(2026, 11, 3) }, haptics = RecordingHaptics(), restAlerts = alerts,
        )
        show()

        // Start the workout, check off a set (rest starts), minimize with the button
        waitFor("Push")
        compose.onNodeWithText("Push").performClick()
        waitFor("Starten")
        compose.onNodeWithText("Starten").performClick()
        waitFor("0/2 erledigt", substring = true)
        compose.onNodeWithContentDescription("Satz 1 abhaken").performClick()
        waitFor("Überspringen")
        compose.onNodeWithContentDescription("Minimieren").performClick()

        // Back on the start screen: the bar shows the rest countdown (1:2x/1:30), the workout keeps running untouched
        waitForDescription("Training öffnen")
        assertNotNull(alerts.pending)
        assertNotNull(deps.workout.state.value.workout)
        compose.onNodeWithText("Zum laufenden Training").assertIsDisplayed()
        assertEquals(true, shown("1:", substring = true))

        // Browse all main screens: the bar stays
        for (tab in listOf("Kalender", "Profil", "Training")) {
            compose.onNode(hasText(tab) and hasClickAction()).performClick()
            waitForDescription("Training öffnen")
        }

        // Reopen with a tap: maximized workout, set 1 still done; then back gesture minimizes (does not end)
        compose.onNode(hasContentDescription("Training öffnen", substring = true)).performClick()
        waitFor("Nächsten Satz loggen")
        back()
        waitForDescription("Training öffnen")
        assertNotNull(deps.workout.state.value.workout)

        // Swipe up reopens as well
        compose.onNode(hasContentDescription("Training öffnen", substring = true)).performTouchInput { swipeUp() }
        waitFor("Nächsten Satz loggen")

        // Notification tap while minimized → maximized
        back()
        waitForDescription("Training öffnen")
        deps.requestOpenWorkout()
        waitFor("Nächsten Satz loggen")

        // Skip the rest, minimize: the bar now shows name/exercise/set instead of the countdown
        compose.onNodeWithText("Überspringen").performClick()
        compose.onNodeWithContentDescription("Minimieren").performClick()
        waitForDescription("Training öffnen")
        waitFor("Bench Press · Satz 2/2")

        // Second workout: choice instead of a start; "end running" needs a second tap
        compose.onNodeWithText("Laufendes beenden").performClick()
        compose.onNodeWithText("Beenden? Nochmal tippen").assertIsDisplayed()
        assertNotNull(deps.workout.state.value.workout)
        compose.onNodeWithText("Beenden? Nochmal tippen").performClick()
        waitFor("Wie war das Training?")
        assertNull(deps.workout.state.value.workout)
    }

    @Test
    fun noBarWithoutWorkout() {
        store.setName("Fabi")
        store.completeOnboarding()
        deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test",
            today = { LocalDate(2026, 11, 3) }, restAlerts = alerts,
        )
        show()
        compose.waitForTrainingTab()
        assertEquals(false, compose.onAllNodes(hasContentDescription("Training öffnen", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }
}
