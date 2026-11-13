package app.overfit.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.coach.CoachChatSchema
import app.overfit.coach.CoachPrompt
import app.overfit.coach.CoachReply
import app.overfit.coach.FakeCoachTransport
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
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * One way into the AI (Fabian 8.11.): the coach icon top right opens the coach over the screen, with the screen's
 * context as chip; nothing is sent before the user taps send; a proposal shows the difference and "Apply" changes
 * exactly that workout (saved workout: its editor; running workout: right away, with undo).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class CoachOverlayTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private lateinit var deps: AppDependencies
    private var templateId = 0L

    // The coach proposes "Push" with a third, heavier bench set and a plank.
    private val proposal = """{"v":1,"kind":"template","name":"Push","exercises":[""" +
        """{"id":"wg:bench-press","restSec":90,"sets":[{"kg":62.5,"reps":8},{"kg":62.5,"reps":8},{"kg":62.5,"reps":8}]},""" +
        """{"name":"Plank","restSec":60,"sets":[{"sec":45},{"sec":45}]}]}"""
    private val answer = buildJsonObject {
        put("reply", JsonPrimitive("Mehr Volumen beim Bankdrücken, dazu Plank."))
        put("workout", JsonPrimitive(proposal))
    }.toString()
    private val fake = FakeCoachTransport { prompt, _ ->
        if (prompt.schemaName == CoachChatSchema.NAME) CoachReply(answer, answer) else CoachReply("{}", "{}")
    }

    private fun chatPrompts(): List<CoachPrompt> = fake.prompts.filter { it.schemaName == CoachChatSchema.NAME }

    private fun setUp() {
        store.setName("Fabi")
        store.completeOnboarding()
        store.connectAi(ProviderId.ANTHROPIC, "sk-test")
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            templateId = data.templates.save(
                Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 90, note = null, sets = List(2) { PlannedSet(60.0, 8) }))),
            )
        }
        deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test", share = NoopPlatformShare(),
            today = { realToday() }, coachTransport = { fake },
        )
        compose.setContent {
            backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            OverfitApp(deps)
        }
        compose.waitForTrainingTab()
    }

    private var backDispatcher: androidx.activity.OnBackPressedDispatcher? = null

    /** System back (gesture/button). */
    private fun back() { compose.runOnIdle { backDispatcher!!.onBackPressed() } }

    private fun openPush() {
        waitFor("Push", substring = true)
        compose.onNodeWithText("Push", substring = true).performClick()
        waitFor("Starten")
    }

    private fun input() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("coach:input")))

    @Test
    fun savedWorkoutProposalShowsTheDiffAndOpensTheEditor() {
        setUp()
        openPush()
        compose.onNodeWithTag("coach:open").performClick()
        waitForTag("coach:close")
        waitForTag("chip:plan")
        shot("bh2-01-coach-ueber-workout")
        // System back closes the coach (wins over the editor's own back handler) and returns to the workout.
        back()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coach:close")).fetchSemanticsNodes().isEmpty() }
        waitFor("Starten")
        compose.onNodeWithTag("coach:open").performClick()
        waitForTag("chip:plan")
        compose.waitForIdle()
        assertTrue("nothing is sent by opening", fake.prompts.isEmpty())

        input().performTextInput("Mehr Volumen bitte")
        compose.onNodeWithTag("coach:send").performClick()
        compose.waitUntilState(10_000) { chatPrompts().size == 1 }
        val prompt = chatPrompts().single().dynamicSystem
        assertTrue(prompt, prompt.contains("## Attached workout (plan): Push"))

        waitForTag("chat:change")
        waitFor("Änderung für „Push“")
        waitFor("Plank", substring = true)
        shot("bh2-02-aenderung-fuer-workout")
        compose.onNodeWithTag("chat:apply").performClick()
        // The normal editor, pre-filled; "Speichern" overwrites exactly this workout.
        waitFor("Speichern")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coach:close")).fetchSemanticsNodes().isEmpty() }
        shot("bh2-03-editor-mit-vorschlag")
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntilState(10_000) { runBlocking { data.templates.get(templateId) }?.exercises?.size == 2 }
        val saved = runBlocking { data.templates.get(templateId) }!!
        assertEquals("Push", saved.name)
        assertEquals(3, saved.exercises[0].sets.size)
        assertEquals(1, runBlocking { data.templates.templates.first() }.count { it.name == "Push" })
        // Back from the fresh workout screen: not a stale copy of the old version underneath, but the list.
        waitFor("Starten")
        back()
        compose.waitForTrainingTab()
    }

    @Test
    fun runningWorkoutProposalIsAppliedAndUndone() {
        setUp()
        openPush()
        compose.onNodeWithText("Starten").performClick()
        waitFor("Beenden")
        // First bench set done.
        compose.onNodeWithContentDescription("Satz 1 abhaken").performClick()
        compose.waitUntilState(10_000) { deps.workout.state.value.workout?.completedSets == 1 }
        val before = deps.workout.state.value.workout!!

        compose.onNodeWithTag("coach:open").performClick()
        waitForTag("chip:running")
        input().performTextInput("Pass den Rest an")
        compose.onNodeWithTag("coach:send").performClick()
        compose.waitUntilState(10_000) { chatPrompts().size == 1 }
        val prompt = chatPrompts().single().dynamicSystem
        assertTrue(prompt, prompt.contains("## Running workout (in progress right now, not finished): Push"))
        assertTrue(prompt, prompt.contains("done 60×8"))

        waitFor("Änderung am laufenden Training")
        compose.onNodeWithTag("chat:apply").performClick()
        waitForTag("chat:undo")
        shot("bh2-04-laufendes-training-uebernommen")
        compose.waitUntilState(10_000) { deps.workout.state.value.workout?.exercises?.size == 2 }
        val after = deps.workout.state.value.workout!!
        // The done set is untouched, the open ones follow the proposal, the plank was added.
        assertEquals(before.exercises[0].sets[0], after.exercises[0].sets[0])
        assertEquals(3, after.exercises[0].sets.size)
        assertEquals(PlannedSet(62.5, 8), after.exercises[0].sets[1].target)
        assertEquals("Plank", after.exercises[1].exercise.name)

        compose.onNodeWithTag("chat:undo").performClick()
        compose.waitUntilState(10_000) { deps.workout.state.value.workout == before }
        waitForTag("chat:apply")
        // Close → back on the running workout.
        compose.onNodeWithTag("coach:close").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coach:close")).fetchSemanticsNodes().isEmpty() }
        waitFor("Beenden")
    }

    @Test
    fun trainingDetailAndDayOpenTheCoachWithTheirContext() {
        setUp()
        runBlocking {
            val start = startEarlierToday()
            var w = Workout.fromTemplate(requireNotNull(data.templates.get(templateId)), start, id = "w1")
            w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, start + 1_000L) } }
            data.workouts.finish(w, endOf(start))
        }
        compose.openCalendarDay(realToday().dayOfMonth)
        compose.onNodeWithTag("coach:open").performClick()
        waitForTag("chip:day")
        shot("bh2-05-coach-ueber-tag")
        compose.onNodeWithTag("coach:close").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coach:close")).fetchSemanticsNodes().isEmpty() }

        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Training bearbeiten")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("coach:open").performClick()
        waitForTag("chip:training")
        input().performTextInput("Wie war das?")
        compose.onNodeWithTag("coach:send").performClick()
        compose.waitUntilState(10_000) { chatPrompts().size == 1 }
        assertTrue(chatPrompts().single().dynamicSystem.contains("## Attached training"))
        // Its saved workout is the target of a proposal.
        waitFor("Änderung für „Push“")
    }

    /** Evidence screenshot into `$SCREENSHOT_DIR` (only when set). */
    private fun shot(name: String) {
        val dir = System.getenv("SCREENSHOT_DIR") ?: return
        compose.waitForIdle()
        val out = File(dir).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onAllNodes(androidx.compose.ui.test.isRoot()).let { roots -> roots[roots.fetchSemanticsNodes().size - 1] }
                .captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = substring) or hasContentDescription(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}
