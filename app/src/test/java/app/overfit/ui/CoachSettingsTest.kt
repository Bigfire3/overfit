package app.overfit.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
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
import app.overfit.domain.Workout
import app.overfit.domain.WeightUnit
import app.overfit.share.NoopPlatformShare
import app.overfit.ui.components.ConfirmTags
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import java.util.concurrent.CopyOnWriteArrayList

/** Settings → Coach: instructions (addition reaches the chat prompt, reset), model picker with an own key, auto review. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CoachSettingsTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    private val chatAnswer = buildJsonObject { put("reply", JsonPrimitive("Alles klar.")) }.toString()
    private val fake = FakeCoachTransport { prompt, _ ->
        if (prompt.schemaName == CoachChatSchema.NAME) CoachReply(chatAnswer, chatAnswer) else CoachReply("{}", "{}")
    }

    /** Status the simulated Anthropic answers the model list with. */
    @Volatile private var modelsStatus = HttpStatusCode.OK
    private val requests = CopyOnWriteArrayList<String>()
    private val engine = MockEngine { req ->
        requests += "${req.url.host} ${req.url.encodedPath} ${req.headers["x-api-key"]}"
        respond(
            """{"data":[{"id":"claude-sonnet-5-5"},{"id":"claude-haiku-5-5"}]}""",
            modelsStatus,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    private lateinit var deps: AppDependencies
    private val share = NoopPlatformShare()

    private fun setUp() {
        store.setName("Fabi")
        store.completeOnboarding()
        store.connectAi(ProviderId.ANTHROPIC, "sk-ant-test")
        deps = AppDependencies(
            store, Providers(HttpClient(engine)), data, versionName = "0.1.test", share = share,
            today = { LocalDate(2026, 11, 3) }, coachTransport = { fake },
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:PROFILE").performClick()
        waitFor("Coach-Anweisungen")
    }

    private fun waitFor(text: String) {
        try { compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    private fun input(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun additionGoesIntoTheNextChatRequestAndResetClearsIt() {
        setUp()
        // With an own key the privacy note sits right under the coach settings.
        compose.onNodeWithTag("coach:access-privacy").performScrollTo().assertExists()
        compose.onNodeWithText("Coach-Anweisungen").performScrollTo().performClick()
        waitFor("Overfit-Grundanweisung")
        compose.onNodeWithTag("coach:base-toggle").performScrollTo().performClick()
        waitFor("Weniger")

        val addition = "Ich trainiere für einen Halbmarathon."
        input("coach:addition").performScrollTo().performTextInput(addition)
        compose.onNodeWithTag("coach:addition-save").performClick()
        compose.waitUntilState { data.coachChatPrefs.state.value.addition == addition }
        waitFor("Gespeichert")

        runBlocking { deps.coachChat.sendNow("chat-1", "Was soll ich morgen trainieren?") }
        val system = fake.prompts.last().staticSystem
        assertTrue(system, system.contains(addition))

        // Two taps: the first arms (red), the second resets.
        compose.onNodeWithText("Auf Standard zurück").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(ConfirmTags.armed)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ConfirmTags.armed).performClick()
        compose.waitUntilState { data.coachChatPrefs.state.value.addition.isEmpty() }
        assertTrue(compose.onAllNodes(hasText(addition)).fetchSemanticsNodes().isEmpty())
        runBlocking { deps.coachChat.sendNow("chat-2", "Und jetzt?") }
        assertFalse(fake.prompts.last().staticSystem.contains(addition))
    }

    @Test
    fun ownKeyTestFillsSuggestionsAndApplySetsTheModel() {
        setUp()
        compose.onNodeWithText("Eigener Schlüssel · Anthropic").assertExists()
        compose.onNodeWithText("KI-Zugang").performScrollTo().performClick()
        waitFor("Gespeicherter Schlüssel ••••test")
        compose.onNodeWithTag("key:test").performClick()
        waitFor("Schlüssel funktioniert · 2 Modelle gefunden")
        // Straight from the device to the provider, with the stored key.
        assertEquals(listOf("api.anthropic.com /v1/models sk-ant-test"), requests.toList())

        compose.onNodeWithTag("model:suggestion:claude-haiku-5-5").performScrollTo().performClick()
        compose.onNodeWithTag("model:apply").performScrollTo().performClick()
        compose.waitUntilState { deps.coach.settings.state.value.model(ProviderId.ANTHROPIC) == "claude-haiku-5-5" }
        // Back in the settings: the default model shows the new choice, the key and provider stayed.
        waitFor("Claude Haiku 5.5")
        assertEquals("sk-ant-test", store.apiKey())
        assertEquals(ProviderId.ANTHROPIC, store.state.value.providerId)
    }

    @Test
    fun invalidKeyIsExplained() {
        modelsStatus = HttpStatusCode.Unauthorized
        setUp()
        compose.onNodeWithText("KI-Zugang").performScrollTo().performClick()
        waitFor("Gespeicherter Schlüssel ••••test")
        input("key:input").performTextInput("sk-falsch")
        compose.onNodeWithTag("key:test").performClick()
        waitFor("Schlüssel ungültig – bitte prüfen, ob er vollständig kopiert ist.")
        assertEquals(listOf("api.anthropic.com /v1/models sk-falsch"), requests.toList())
    }

    @Test
    fun promptsCanBeCopiedAndUsedInTheChat() {
        runBlocking {
            data.seedCatalog()
            val bench = requireNotNull(data.exercises.get("wg:bench-press"))
            val id = data.templates.save(Template(0, "Push", null, listOf(TemplateExercise(bench, 60, null, List(2) { PlannedSet(60.0, 8) }))))
            val start = System.currentTimeMillis() - 3_600_000L
            var w = Workout.fromTemplate(requireNotNull(data.templates.get(id)), start, id = "w1")
            w.exercises.forEach { e -> e.sets.forEach { w = w.completeSet(it.id, start + 1_000L) } }
            data.workouts.finish(w, start + 1_800_000L)
        }
        setUp()
        compose.onNodeWithText("Prompts kopieren").performScrollTo().performClick()
        waitFor("Training auswerten lassen")
        waitFor("Nächste Woche planen")

        // Copy: the full prompt with the profile and the finished training goes to the clipboard.
        compose.onNodeWithTag("prompt-copy:Training auswerten lassen").performScrollTo().performClick()
        compose.waitUntilState { share.clipboard != null }
        val copied = share.clipboard.orEmpty()
        assertTrue(copied, copied.contains("Fabi"))
        assertTrue(copied, copied.contains("Bench Press"))

        // Use in chat: Coach tab with the question in the input, not sent.
        compose.onNodeWithTag("prompt-chat:Nächste Woche planen").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coach:input")).fetchSemanticsNodes().isNotEmpty() }
        val ask = "Plane meine nächste Trainingswoche"
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText(ask, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTrue(fake.prompts.none { it.schemaName == CoachChatSchema.NAME })
        assertTrue(runBlocking { database.coachChatDao().allChats() }.isEmpty())
    }

    // 8.11.: no automatic review any more, so no switch for it.
    @Test
    fun automaticReviewSwitchIsGone() {
        setUp()
        compose.waitForIdle()
        compose.onNodeWithTag("coach:auto-review").assertDoesNotExist()
        compose.onAllNodes(hasText("Automatische Auswertung", substring = true)).assertCountEquals(0)
    }
}
