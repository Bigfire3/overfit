package app.overfit.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Coach: review last workout → plan → accept; demo without access; memory editing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CoachFlowTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(app.overfit.data.AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private var templateId = 0L

    private val answer = """{"rating":8,"summary":"Starkes Training","positives":["Sauber"],"improvements":[],"tips":["Mehr schlafen"],"photo_feedback":null,"next_session":{"exercises":[{"exercise_id":"wg:bench-press","is_new":false,"rest_sec":90,"sets":[{"weight_kg":62.5,"reps":8,"duration_sec":null},{"weight_kg":62.5,"reps":8,"duration_sec":null}],"reason":"Alle Sätze geschafft"}],"note":null},"memory":"Ziel: Kraft."}"""

    private val fake = FakeCoachTransport { _, _ -> CoachReply(answer, answer) }
    private val workoutId = "w1"

    private fun setUp(withKey: Boolean, seedWorkout: Boolean = true): AppDependencies {
        store.setName("Fabi")
        store.completeOnboarding()
        if (withKey) store.connectAi(ProviderId.ANTHROPIC, "sk-test")
        runBlocking {
            data.seedCatalog()
            if (seedWorkout) {
                val bench = requireNotNull(data.exercises.get("wg:bench-press"))
                templateId = data.templates.save(
                    Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 60, note = null, sets = List(2) { PlannedSet(60.0, 8) }))),
                )
                // An hour ago, so the calendar shows it today.
                val start = System.currentTimeMillis() - 3_600_000L
                var w = Workout.fromTemplate(requireNotNull(data.templates.get(templateId)), start, id = "w1")
                w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, start + 1_000L) } }
                data.workouts.finish(w, start + 1_800_000L)
            }
        }
        val deps = AppDependencies(
            store,
            Providers(HttpClient(MockEngine { respond("{}") })),
            data,
            versionName = "0.1.test",
            // The real day: the calendar does not open days after "today".
            today = { realToday() },
            coachTransport = { fake },
        )
        compose.setContent { OverfitApp(deps) }
        return deps
    }

    // Fabian 8.11.: "wenn man jetzt ein Workout abschließt, dann geht es ja direkt los, ohne dass man nochmal gefragt wird."
    @Test
    fun finishOffersAReviewCardThatOnlyOpensTheChat() {
        val deps = setUp(withKey = true)
        compose.waitForTrainingTab()
        // Training tab → "Push" → "Starten" → "Beenden" (two taps) → finish flow.
        waitFor("Push", substring = true)
        compose.onNodeWithText("Push", substring = true).performClick()
        waitFor("Starten")
        compose.onNodeWithText("Starten").performClick()
        waitFor("Beenden")
        compose.onNodeWithText("Beenden").performClick()
        compose.onNodeWithTag("confirm:armed").performClick()
        waitForTag("finish:review")
        compose.onNodeWithTag("finish:review").performScrollTo()
        waitFor("Training auswerten lassen")
        waitFor("eigener Schlüssel")
        compose.waitForIdle()
        assertTrue(fake.prompts.isEmpty())

        // Tap: saves the finish flow and opens the chat with the training attached; still nothing sent.
        // Last item of the screen: after performScrollTo it may sit under the floating "Speichern"; tap it by its action.
        compose.onNodeWithTag("finish:review").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitForTag("coach:input")
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText("Werte mein Training aus")).fetchSemanticsNodes().isNotEmpty() }
        waitForTag("chip:training")
        compose.waitForIdle()
        assertTrue(fake.prompts.isEmpty())
        assertTrue(runBlocking { data.database.coachChatDao().allChats() }.isEmpty())

        // The user sends it.
        compose.onNodeWithTag("coach:send").performClick()
        compose.waitUntilState(10_000) { fake.prompts.any { it.schemaName == app.overfit.coach.CoachChatSchema.NAME } }
        val prompt = fake.prompts.last { it.schemaName == app.overfit.coach.CoachChatSchema.NAME }
        assertTrue(prompt.dynamicSystem, prompt.dynamicSystem.contains("## Attached training"))
        assertEquals("Werte mein Training aus", prompt.user)
    }

    @Test
    fun trainingCoachIconOpensTheChatWithoutSending() {
        setUp(withKey = true)
        compose.waitForTrainingTab()
        // Calendar → today → training → coach icon = the coach over the training, with it as chip, nothing sent.
        openToday()
        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        waitForTag("coach:open")
        compose.onNodeWithTag("coach:open").performClick()
        waitForTag("chip:training")
        compose.waitForIdle()
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun structuredReviewPlanCanStillBeAccepted() {
        // Reviews from before the chat (and the review engine) still open from Profile → Coach → "Auswertungen".
        val deps = setUp(withKey = true)
        compose.waitForTrainingTab()
        runBlocking { deps.coach.requestSessionReview(workoutId) }
        deps.coach.processQueue()
        compose.onNodeWithTag("tab:PROFILE").performClick()
        waitFor("Auswertungen")
        compose.onNodeWithText("Auswertungen").performScrollTo().performClick()
        waitFor("8/10")
        compose.onAllNodes(hasText("8/10"))[0].performClick()
        waitFor("Mehr schlafen")
        waitFor("Bench Press", substring = true)
        compose.onNodeWithText("Alle annehmen").performClick()
        waitFor("Plan übernommen. Das Workout ist angepasst.")

        compose.waitUntilState(10_000) {
            runBlocking { data.templates.get(templateId) }?.exercises?.single()?.sets?.first()?.weightKg == 62.5
        }
        val bench = runBlocking { data.templates.get(templateId) }!!.exercises.single()
        assertEquals(90, bench.restSec)
    }

    @Test
    fun withoutAccessTheCoachOverATrainingAnswersWithADemo() {
        setUp(withKey = false)
        compose.waitForTrainingTab()
        openToday()
        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        waitForTag("coach:open")
        compose.onNodeWithTag("coach:open").performClick()
        waitForTag("chip:training")
        waitFor("Demo · kostenlos")
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("coach:input"))).performTextInput("Wie war das?")
        compose.onNodeWithTag("coach:send").performClick()
        waitFor("Demo-Antwort", substring = true)
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun editingTheCoachMemory() {
        setUp(withKey = true, seedWorkout = false)
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:PROFILE").performClick()
        waitFor("Coach-Gedächtnis")
        compose.onNodeWithText("Coach-Gedächtnis").performScrollTo().performClick()
        waitFor("Was der Coach über dich weiß", substring = true)
        compose.onNodeWithTag("coach-memory-input").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Ziel: 100 kg Bankdrücken")
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntilState(10_000) { runBlocking { database.coachDao().currentMemory() }?.text == "Ziel: 100 kg Bankdrücken" }
    }

    /** Calendar → today. The first visit of the calendar scrolls to today; a second tap on the tab after that is safe. */
    private fun openToday() {
        compose.onNodeWithTag("tab:PHOTOS").performClick()
        compose.waitForIdle()
        compose.openCalendarDay(realToday().dayOfMonth)
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}
