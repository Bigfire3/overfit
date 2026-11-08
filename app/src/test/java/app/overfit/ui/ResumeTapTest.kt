package app.overfit.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Owner: "Wenn man aus einer anderen App in Overfit wechselt ist die App stuck und man kann nichts tippen. Dann muss
 * man erst zurück klicken und dann geht es wieder." Cause: a started and then cancelled back gesture (predictive back)
 * leaves two screens in `visibleEntries`, and the tap blocker for screen changes (AppScaffold) swallowed every tap
 * until Back. Each case: leave/return or gesture, then a tap that has to work right away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResumeTapTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun blocked() = compose.onAllNodes(hasTestTag("nav-transition")).fetchSemanticsNodes().isNotEmpty()

    private lateinit var deps: AppDependencies
    private val scenario get() = compose.activityRule.scenario

    private fun startWorkout() {
        store.setName("Fabi")
        store.completeOnboarding()
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 90, note = null, sets = List(2) { PlannedSet(60.0, 8) }))))
        }
        deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test",
            today = { LocalDate(2026, 11, 3) }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(),
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitUntil(10_000) { shown("Push") }
        compose.onNodeWithText("Push").performClick()
        compose.waitUntil(10_000) { shown("Starten") }
        compose.onNodeWithText("Starten").performClick()
        compose.waitUntil(10_000) { shown("0/2 erledigt") }
    }

    /** Checks off set 1: the rest timer ("Überspringen") only appears if the tap reached the button. */
    private fun tapSet() {
        // A real fade may block for a moment (ScreenMotion.MAX_BLOCK_MS), never for good.
        compose.waitUntil(2_000) { !blocked() }
        compose.onNodeWithContentDescription("Satz 1 abhaken").performClick()
        compose.waitUntil(5_000) { shown("Überspringen") }
    }

    private fun startBackGesture() {
        val d = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread { d.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.05f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
    }

    private fun cancelBackGesture() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }

    /** Return via the rest notification while the workout is minimized: intent arrives before onResume. */
    @Test
    fun notificationWhileMinimized() {
        startWorkout()
        compose.onNodeWithContentDescription("Minimieren").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Training öffnen", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.STARTED)
        deps.requestOpenWorkout()
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { shown("0/2 erledigt") }
        tapSet()
    }

    /** Back gesture begun, app left (gesture navigation, Spotify), the system cancels the gesture in the background. */
    @Test
    fun backGestureCancelledWhileStopped() {
        startWorkout()
        startBackGesture()
        scenario.moveToState(Lifecycle.State.CREATED)
        cancelBackGesture()
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        tapSet()
    }

    /** App left in the middle of a screen fade (minimize), back afterwards: the mini bar reacts. */
    @Test
    fun stoppedMidTransition() {
        startWorkout()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Minimieren").performClick()
        compose.mainClock.advanceTimeBy(50)
        scenario.moveToState(Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(1000)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.waitUntil(2_000) { !blocked() }
        compose.onNode(hasContentDescription("Training öffnen", substring = true)).performClick()
        compose.waitUntil(5_000) { shown("0/2 erledigt") }
    }

    /** Edge swipe started and released again without going back. */
    @Test
    fun backGestureCancelledInForeground() {
        startWorkout()
        startBackGesture()
        cancelBackGesture()
        compose.waitForIdle()
        tapSet()
    }

    /** App left during a back gesture, no cancel delivered. */
    @Test
    fun stoppedDuringGestureNoCancel() {
        startWorkout()
        startBackGesture()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        tapSet()
    }
}
