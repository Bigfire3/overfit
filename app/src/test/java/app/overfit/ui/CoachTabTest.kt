package app.overfit.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.coach.AthleteProfile
import app.overfit.coach.ChatAttachment
import app.overfit.coach.CoachChatSchema
import app.overfit.coach.CoachPrompt
import app.overfit.coach.CoachReply
import app.overfit.coach.FakeCoachTransport
import app.overfit.coach.TrainingGoal
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
import app.overfit.ui.coach.chat.ChatOpenRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Coach tab chat with a simulated provider: suggestions, profile chip, automatic "last training", "+" sheet, workout
 * proposal → import preview, earlier chats (open, delete with two taps), demo without access, open request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CoachTabTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private lateinit var deps: AppDependencies

    private val chatAnswer = buildJsonObject {
        put("reply", JsonPrimitive("Probier das hier."))
        put("workout", JsonPrimitive(AiPrompt.EXAMPLE.replace("Oberkörper A", "Coach Oberkörper")))
    }.toString()
    @Volatile private var failure: Exception? = null
    private val fake = FakeCoachTransport { prompt, _ ->
        failure?.let { throw it }
        if (prompt.schemaName == CoachChatSchema.NAME) CoachReply(chatAnswer, chatAnswer, costMicro = 4_200, servedModel = "gemini-3.6-flash") else CoachReply("{}", "{}")
    }

    private fun chatPrompts(): List<CoachPrompt> = fake.prompts.filter { it.schemaName == CoachChatSchema.NAME }

    private fun setUp(withKey: Boolean, openTab: Boolean = true) {
        store.setName("Fabi")
        store.completeOnboarding()
        if (withKey) store.connectAi(ProviderId.ANTHROPIC, "sk-test")
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            val id = data.templates.save(
                Template(0, "Push", null, listOf(TemplateExercise(bench, restSec = 60, note = null, sets = List(2) { PlannedSet(60.0, 8) }))),
            )
            // Trained a minute ago: the automatic chip reads "Push · heute".
            val start = System.currentTimeMillis() - 60_000L
            var w = Workout.fromTemplate(requireNotNull(data.templates.get(id)), start, id = "w1")
            w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, start + 1_000L) } }
            data.workouts.finish(w, start + 30_000L)
        }
        deps = AppDependencies(
            store,
            Providers(HttpClient(MockEngine { respond("{}") })),
            data,
            versionName = "0.1.test",
            share = NoopPlatformShare(),
            today = { LocalDate(2026, 11, 3) },
            coachTransport = { fake },
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        if (openTab) openCoachTab()
    }

    private fun openCoachTab() {
        compose.onNodeWithTag("tab:COACH").performClick()
        waitForTag("coach:input")
    }

    @Test
    fun suggestionPutsItsTextIntoTheInputWithoutSending() {
        setUp(withKey = true)
        waitForTag("chat:suggestion:0")
        compose.onNodeWithText("Was mache ich nächstes Mal?").assertExists()
        compose.onNodeWithText("Wo trete ich auf der Stelle?").assertExists()
        compose.onNodeWithTag("coach:send").assertIsNotEnabled()
        compose.onNodeWithTag("chat:suggestion:0").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText("Wie lief mein Training?")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("coach:send").assertIsEnabled()
        compose.waitForIdle()
        assertTrue(fake.prompts.isEmpty())
        assertTrue(runBlocking { database.coachChatDao().allChats() }.isEmpty())
    }

    @Test
    fun profileChipGoesAlongUntilItIsRemoved() {
        data.coachChatPrefs.setProfile(AthleteProfile(goal = TrainingGoal.STRENGTH))
        setUp(withKey = true)
        waitForTag("chip:profile")
        ask("Wie viele Sätze?")
        compose.waitUntilState(10_000) { chatPrompts().size == 1 }
        val first = chatPrompts().last().dynamicSystem
        assertTrue(first, first.contains("## Profile"))
        assertTrue(first, first.contains("Goal: strength"))
        assertTrue(first, first.contains("Name: Fabi"))

        // Second chat without the profile.
        compose.onNodeWithTag("coach:new").performClick()
        waitForTag("chat:suggestion:0")
        compose.onNodeWithTag("chip:profile:remove").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("chip:profile")).fetchSemanticsNodes().isEmpty() }
        ask("Und jetzt?")
        compose.waitUntilState(10_000) { chatPrompts().size == 2 }
        val second = chatPrompts().last().dynamicSystem
        assertTrue(second, second.contains("Not shared in this chat"))
        assertFalse(second, second.contains("Goal:"))
        // The chip stays off for that chat; the "+" sheet offers to send it again.
        compose.onNodeWithTag("coach:plus").performClick()
        waitFor("Profil wieder mitschicken")
        compose.onNodeWithTag("context:profile").performClick()
        waitForTag("chip:profile")
    }

    @Test
    fun lastTrainingIsAttachedAutomaticallyUnlessRemoved() {
        setUp(withKey = true)
        waitForTag("chip:training")
        // Label plus the day, computed when shown.
        waitFor("Push · heute")
        ask("Wie war das?")
        compose.waitUntilState(10_000) { chatPrompts().size == 1 }
        val withTraining = chatPrompts().last().dynamicSystem
        assertTrue(withTraining, withTraining.contains("## Attached training"))
        assertTrue(withTraining, withTraining.contains("Bench Press"))
        // The card of the attachment sits above the question.
        waitForTag("chat:user")

        compose.onNodeWithTag("coach:new").performClick()
        waitForTag("chip:training")
        compose.onNodeWithTag("chip:training:remove").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("chip:training")).fetchSemanticsNodes().isEmpty() }
        ask("Und ohne?")
        compose.waitUntilState(10_000) { chatPrompts().size == 2 }
        val without = chatPrompts().last().dynamicSystem
        assertFalse(without, without.contains("## Attached training"))
    }

    @Test
    fun contextSheetAttachesTheHistory() {
        setUp(withKey = true)
        compose.onNodeWithTag("coach:plus").performClick()
        waitFor("Verlauf")
        compose.onNodeWithTag("context:add").assertIsNotEnabled()
        compose.onNodeWithTag("context:history").performClick()
        waitFor("1 hinzufügen")
        compose.onNodeWithTag("context:add").performClick()
        waitForTag("chip:history")
        ask("Wie lief der Monat?")
        compose.waitUntilState(10_000) { chatPrompts().size == 1 }
        val prompt = chatPrompts().last().dynamicSystem
        assertTrue(prompt, prompt.contains("## Attached history"))
        // Sent: the chips of the question are gone.
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("chip:history")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun workoutProposalShowsACardAndApplyOpensTheImportPreview() {
        setUp(withKey = true)
        ask("Neues Push-Workout bitte")
        waitFor("Probier das hier.")
        waitFor("Workout-Vorschlag: Coach Oberkörper")
        waitFor("Gemini 3.6 Flash (Google) · 0,0042 € Guthaben")
        assertTrue(chatPrompts().last().staticSystem.contains("wg:bench-press | Bench Press"))

        // A follow-up question carries the history.
        ask("Und mit weniger Gewicht?")
        compose.waitUntilState(10_000) { chatPrompts().size >= 2 }
        assertEquals(2, chatPrompts().last().history.size)

        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("chat:apply")).fetchSemanticsNodes().size == 2 }
        compose.onAllNodes(hasTestTag("chat:apply"))[1].performClick()
        waitFor("Coach Oberkörper")
        // The import preview's own "Übernehmen" (not the chat card's).
        val importButton = hasText("Übernehmen") and !hasTestTag("chat:apply")
        compose.waitUntil(10_000) { compose.onAllNodes(importButton).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(importButton).performClick()
        compose.waitUntilState(10_000) { runBlocking { data.templates.templates.first() }.any { it.name == "Coach Oberkörper" } }
    }

    @Test
    fun earlierChatsOpenAndDeleteWithTwoTaps() {
        setUp(withKey = true)
        ask("Erste Frage")
        waitFor("Probier das hier.")
        compose.waitUntilState(10_000) { runBlocking { database.coachChatDao().allChats() }.size == 1 }

        // New chat empties the screen.
        compose.onNodeWithTag("coach:new").performClick()
        waitForTag("chat:suggestion:0")
        assertTrue(compose.onAllNodes(hasTestTag("chat:user")).fetchSemanticsNodes().isEmpty())

        // Earlier chats → open it again.
        compose.onNodeWithTag("coach:history").performClick()
        waitForTag("history:item")
        compose.onNodeWithTag("history:item").performClick()
        waitForTag("chat:user")
        waitFor("Probier das hier.")

        // Delete: first tap arms (still stored), second deletes.
        compose.onNodeWithTag("coach:history").performClick()
        waitForTag("history:delete")
        compose.onNodeWithTag("history:delete").performClick()
        compose.onNodeWithTag("confirm:armed").assertExists()
        assertEquals(1, runBlocking { database.coachChatDao().allChats() }.size)
        compose.onNodeWithTag("confirm:armed").performClick()
        compose.waitUntilState(10_000) { runBlocking { database.coachChatDao().allChats() }.isEmpty() }
        waitFor("Noch keine Chats.")
        assertTrue(runBlocking { database.coachChatDao().allMessages() }.isEmpty())
    }

    @Test
    fun withoutAccessTheChatAnswersWithADemo() {
        setUp(withKey = false)
        waitFor("Kein KI-Zugang")
        ask("Hallo Coach")
        waitFor("Demo-Antwort", substring = true)
        waitFor("Workout-Vorschlag: Ganzkörper (Demo)")
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun openRequestSendsRightAwayWithItsAttachment() {
        setUp(withKey = true, openTab = false)
        deps.coachChatRequests.open(
            ChatOpenRequest(draft = "Werte mein Training aus", attachments = listOf(ChatAttachment.Training("w1", "Push · heute")), send = true),
        )
        compose.onNodeWithTag("tab:COACH").performClick()
        compose.waitUntilState(10_000) { chatPrompts().size == 1 }
        val prompt = chatPrompts().last()
        assertEquals("Werte mein Training aus", prompt.user)
        assertTrue(prompt.dynamicSystem, prompt.dynamicSystem.contains("## Attached training"))
        waitFor("Probier das hier.")
        compose.waitUntilState(10_000) { deps.coachChatRequests.pending.value == null }
    }

    @Test
    fun deletedOpenChatSwitchesToANewChat() {
        setUp(withKey = true)
        ask("Erste Frage")
        waitFor("Probier das hier.")
        compose.waitUntilState(10_000) { runBlocking { database.coachChatDao().allChats() }.size == 1 }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("chip:training")).fetchSemanticsNodes().isEmpty() }

        // Deleted elsewhere (earlier chats, delete all data) while it is the open chat.
        val id = runBlocking { database.coachChatDao().allChats() }.single().id
        runBlocking { deps.coachChat.delete(id).join() }
        waitForTag("chat:suggestion:0")
        waitForTag("chip:training")
        assertTrue(compose.onAllNodes(hasTestTag("chat:user")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun draftNeverReplacesTypedText() {
        setUp(withKey = true)
        input().performTextInput("Meine eigene Frage")
        deps.coachChatRequests.open(ChatOpenRequest(draft = "Entwurf von woanders"))
        compose.waitUntilState(10_000) { deps.coachChatRequests.pending.value == null }
        waitFor("Meine eigene Frage")
        assertTrue(compose.onAllNodes(hasText("Entwurf von woanders")).fetchSemanticsNodes().isEmpty())
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun consumedLeavesANewerRequestPending() {
        setUp(withKey = true, openTab = false)
        val first = ChatOpenRequest(draft = "Erste")
        val second = ChatOpenRequest(draft = "Zweite")
        deps.coachChatRequests.open(first)
        deps.coachChatRequests.open(second)
        deps.coachChatRequests.consumed(first)
        assertEquals(second, deps.coachChatRequests.pending.value)
        deps.coachChatRequests.consumed(second)
        assertEquals(null, deps.coachChatRequests.pending.value)
    }

    @Test
    fun reviewCarriesTheUsersAddition() {
        setUp(withKey = true, openTab = false)
        data.coachChatPrefs.setAddition("Antworte kurz.")
        runBlocking {
            deps.coach.requestSessionReview("w1")
            deps.coach.processQueue().join()
        }
        val review = fake.prompts.last { it.schemaName != CoachChatSchema.NAME }
        assertTrue(review.staticSystem, review.staticSystem.contains("Antworte kurz."))
    }

    @Test
    fun failedAnswerNamesTheReason() {
        failure = app.overfit.coach.CoachException(app.overfit.coach.CoachErrorKind.AUTH)
        setUp(withKey = true)
        ask("Hallo?")
        waitFor("Keine Antwort bekommen.")
        waitFor("Schlüssel ungültig oder abgelaufen")
    }

    @Test
    fun profilePopupWaitsForAValidHeight() {
        setUp(withKey = true)
        compose.onNodeWithTag("chip:profile").performClick()
        waitForTag("profile:save")
        val height = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("profile:height")))
        height.performTextInput("99")
        waitFor("Erlaubt: 100–250 cm")
        compose.onNodeWithTag("profile:save").assertIsNotEnabled()
        height.performTextClearance()
        height.performTextInput("180")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Erlaubt: 100–250 cm")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("profile:save").assertIsEnabled().performScrollTo().performClick()
        compose.waitUntilState(10_000) { data.coachChatPrefs.state.value.profile.heightCm == 180 }
    }

    private fun input() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("coach:input")))

    private fun ask(text: String) {
        input().performTextInput(text)
        compose.onNodeWithTag("coach:send").performClick()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = substring) or hasContentDescription(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}
