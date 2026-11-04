package app.overfit.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.coach.CoachChatSchema
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
import app.overfit.share.AiPrompt
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Coach tab: prompts with visible text and copy, chat with a simulated provider (workout → import preview), demo, delete. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CoachTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(app.overfit.data.AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val share = NoopPlatformShare()

    private val chatAnswer = buildJsonObject {
        put("reply", JsonPrimitive("Probier das hier."))
        put("workout", JsonPrimitive(AiPrompt.EXAMPLE.replace("Oberkörper A", "Coach Oberkörper")))
    }.toString()
    private val fake = FakeCoachTransport { prompt, _ ->
        if (prompt.schemaName == CoachChatSchema.NAME) CoachReply(chatAnswer, chatAnswer, costMicro = 4_200, servedModel = "gemini-3.6-flash") else CoachReply("{}", "{}")
    }

    @After
    fun tearDown() = database.close()

    private fun setUp(withKey: Boolean) {
        store.setName("Fabi")
        store.completeOnboarding()
        if (withKey) store.connectAi(ProviderId.ANTHROPIC, "sk-test")
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            val id = data.templates.save(
                Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 60, note = null, sets = List(2) { PlannedSet(60.0, 8) }))),
            )
            var w = Workout.fromTemplate(requireNotNull(data.templates.get(id)), 1_000L, id = "w1")
            w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, 2_000L) } }
            data.workouts.finish(w, 3_600_000L)
        }
        val deps = AppDependencies(
            store,
            Providers(HttpClient(MockEngine { respond("{}") })),
            data,
            versionName = "0.1.test",
            share = share,
            today = { LocalDate(2026, 11, 3) },
            coachTransport = { fake },
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:COACH").performClick()
        waitFor("Prompts & Vorlagen".uppercase())
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("prompt:Kontext kopieren")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun tabShowsAccessAndPromptsWithTheirFullTextAndCopies() {
        setUp(withKey = true)
        compose.onNodeWithText("Eigener Key · Anthropic").assertExists()
        // Every prompt shows its text; the review prompt carries the profile and the last workout.
        compose.onNodeWithTag("prompt:Training auswerten lassen").performScrollTo().assertExists()
        compose.onNodeWithTag("prompt-copy:Training auswerten lassen").performScrollTo().performClick()
        val copied = share.clipboard.orEmpty()
        assertTrue(copied, copied.contains("Fabi"))
        assertTrue(copied, copied.contains("Bench Press"))
        assertTrue(copied, copied.endsWith("Wiederholungen, Pausen)?"))
        waitFor("Kopiert")
        // The text box shows the beginning of exactly that text.
        assertTrue(compose.onAllNodes(hasText(copied.lines().first(), substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun chatProposesAWorkoutThatOpensTheImportPreview() {
        setUp(withKey = true)
        input("coach:tabInput").performScrollTo().performTextInput("Neues Push-Workout bitte")
        compose.onNodeWithText("Senden").performScrollTo().performClick()
        waitFor("Probier das hier.")
        waitFor("Workout-Vorschlag: Coach Oberkörper")
        waitFor("Gemini 3.6 Flash (Google) · 0,0042 € Guthaben")

        // The system prompt has the profile and the workouts; a second question carries the history.
        val first = fake.prompts.last()
        assertTrue(first.dynamicSystem, first.dynamicSystem.contains("Name: Fabi"))
        assertTrue(first.dynamicSystem, first.dynamicSystem.contains("Push"))
        assertTrue(first.staticSystem.contains("wg:bench-press | Bench Press"))
        input("coach:chatInput").performTextInput("Und mit weniger Gewicht?")
        compose.onNodeWithText("Senden").performClick()
        compose.waitUntilState(10_000) { fake.prompts.size >= 2 }
        assertEquals(2, fake.prompts.last().history.size)

        compose.waitUntil(10_000) { compose.onAllNodesWithText("Workout übernehmen").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Workout übernehmen")[1].performClick()
        waitFor("Coach Oberkörper")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.waitUntilState(10_000) { runBlocking { data.templates.templates.first() }.any { it.name == "Coach Oberkörper" } }
    }

    @Test
    fun withoutAccessTheChatAnswersWithADemo() {
        setUp(withKey = false)
        compose.onNodeWithText("Kein KI-Zugang").assertExists()
        input("coach:tabInput").performScrollTo().performTextInput("Hallo Coach")
        compose.onNodeWithText("Senden").performScrollTo().performClick()
        waitFor("Demo-Antwort", substring = true)
        waitFor("Workout-Vorschlag: Ganzkörper (Demo)")
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun useInChatPrefillsAndDeleteNeedsTwoTaps() {
        setUp(withKey = true)
        compose.onNodeWithTag("prompt-chat:Nächste Woche planen", useUnmergedTree = true).performScrollTo().performClick()
        waitFor("Plane meine nächste Trainingswoche", substring = true)
        compose.onNodeWithText("Senden").performClick()
        waitFor("Probier das hier.")
        compose.waitUntilState(10_000) { runBlocking { database.coachChatDao().allChats() }.size == 1 }

        compose.onNodeWithText("Chat löschen").performClick()
        waitFor("Wirklich löschen?")
        assertEquals(1, runBlocking { database.coachChatDao().allChats() }.size)
        compose.onNodeWithText("Wirklich löschen?").performClick()
        compose.waitUntilState(10_000) { runBlocking { database.coachChatDao().allChats() }.isEmpty() }
        assertTrue(runBlocking { database.coachChatDao().allMessages() }.isEmpty())
    }

    /** The text field inside a tagged input (the tag sits on the input's column). */
    private fun input(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}
