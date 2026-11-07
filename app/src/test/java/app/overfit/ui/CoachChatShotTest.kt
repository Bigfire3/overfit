package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.Providers
import app.overfit.coach.AthleteProfile
import app.overfit.coach.ChatAttachment
import app.overfit.coach.ChatSendRequest
import app.overfit.coach.CoachChatSchema
import app.overfit.coach.CoachReply
import app.overfit.coach.FakeCoachTransport
import app.overfit.coach.TrainingExperience
import app.overfit.coach.TrainingGoal
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.DeviceDefaults
import app.overfit.data.DiaryPhoto
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.data.WorkoutPhoto
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalCoachStrings
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.coachStringsFor
import app.overfit.i18n.fuelStringsFor
import app.overfit.i18n.stringsFor
import app.overfit.share.AiPrompt
import app.overfit.share.NoopPlatformShare
import app.overfit.ui.coach.chat.ChatOpenRequest
import app.overfit.ui.coach.chat.CoachChatEnv
import app.overfit.ui.coach.chat.CoachChatTabScreen
import app.overfit.ui.coach.chat.CoachHistoryScreen
import app.overfit.ui.components.ConfirmArea
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Theme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Evidence screenshots of the Coach tab chat (360 dp, dark) into `$SCREENSHOT_DIR`. Runs only with that variable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class CoachChatShotTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })

    private val answer = buildJsonObject {
        put(
            "reply",
            JsonPrimitive(
                "Starke Einheit: **Bankdrücken 80 kg × 8** ist neuer Bestwert.\n\n- Rudern bleibt seit drei Wochen gleich\n- Nächstes Mal 2,5 kg mehr beim Rudern",
            ),
        )
        put("workout", JsonPrimitive(AiPrompt.EXAMPLE.replace("Oberkörper A", "Push")))
    }.toString()
    private val fake = FakeCoachTransport { prompt, _ ->
        if (prompt.schemaName == CoachChatSchema.NAME) CoachReply(answer, answer, costMicro = 18_000, servedModel = "claude-sonnet-5-5") else CoachReply("{}", "{}")
    }

    private val photos = (1..4).map { i ->
        DiaryPhoto(WorkoutPhoto("p$i", "s1", "p$i.jpg", 1_762_000_000_000L - i * 86_400_000L, "/nonexistent/p$i.jpg"), "Push")
    }

    private fun deps(): AppDependencies {
        store.setName("Fabi")
        store.completeOnboarding()
        store.useFuel()
        return AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "t", share = NoopPlatformShare(),
            today = { LocalDate(2026, 11, 3) }, coachTransport = { fake },
        )
    }

    private fun env(d: AppDependencies) = CoachChatEnv(
        chat = d.coachChat,
        prefs = data.coachChatPrefs,
        lastTraining = flowOf(ChatAttachment.Training("s1", "Push · heute")),
        trainings = flowOf(listOf(ChatAttachment.Training("s1", "Push · heute"), ChatAttachment.Training("s0", "Pull · 01.11."))),
        plans = flowOf(listOf(ChatAttachment.Plan(1, "Push"), ChatAttachment.Plan(2, "Pull"))),
        exercises = { listOf(ChatAttachment.Exercise("wg:bench-press", "Bankdrücken") to "Bestwert 80×8") },
        photos = flowOf(photos),
        templateNames = flowOf(setOf("Push")),
        latestWeight = { "82,4 kg · Stand 02.11." },
        modelLabel = MutableStateFlow("Claude Sonnet 5.5"),
        costEstimate = MutableStateFlow("≈ 2 ct"),
    )

    private fun render(content: @Composable () -> Unit) {
        assumeTrue(System.getenv("SCREENSHOT_DIR") != null)
        compose.setContent {
            OverfitTheme {
                CompositionLocalProvider(
                    LocalStrings provides stringsFor(Language.DE),
                    LocalFuelStrings provides fuelStringsFor(Language.DE),
                    LocalCoachStrings provides coachStringsFor(Language.DE),
                ) {
                    ConfirmArea {
                        Box(Modifier.width(360.dp).height(640.dp).background(Theme.colors.background).testTag("shot")) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val out = File(System.getenv("SCREENSHOT_DIR")!!).also { it.mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Composable
    private fun Tab(env: CoachChatEnv, request: ChatOpenRequest? = null) {
        CoachChatTabScreen(env, request, {}, onHistory = {}, onStory = {}, onModelChip = {}, onApplyWorkout = {})
    }

    @Test
    fun newChat() {
        val e = env(deps())
        render { Tab(e) }
        save("01-neuer-chat")
    }

    @Test
    fun conversation() {
        val d = deps()
        runBlocking { d.coachChat.sendNow("c1", ChatSendRequest("Wie lief mein Training heute?", listOf(ChatAttachment.Training("s1", "Push · heute")))) }
        val e = env(d)
        render { Tab(e, ChatOpenRequest(chatId = "c1")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chat:workout").fetchSemanticsNodes().isNotEmpty() }
        save("02-gespraech")
    }

    @Test
    fun contextSheet() {
        val e = env(deps())
        render { Tab(e) }
        compose.onNodeWithTag("coach:plus").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("context:photo").fetchSemanticsNodes().size >= 3 }
        compose.onAllNodesWithTag("context:photo")[1].performClick()
        compose.onAllNodesWithTag("context:photo")[0].performClick()
        compose.onNodeWithTag("context:history").performClick()
        save("03-kontext-sheet")
    }

    @Test
    fun profilePopup() {
        data.coachChatPrefs.setProfile(AthleteProfile(TrainingGoal.MUSCLE, TrainingExperience.INTERMEDIATE, 4, 182, "Studio", ""))
        val e = env(deps())
        render { Tab(e) }
        compose.onNodeWithTag("chip:profile").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile:save").fetchSemanticsNodes().isNotEmpty() }
        save("04-profil-popup")
    }

    @Test
    fun earlierChats() {
        val d = deps()
        runBlocking {
            d.coachChat.sendNow("c1", ChatSendRequest("Wie lief mein Training heute?"))
            d.coachChat.sendNow("c2", ChatSendRequest("Plane meine nächste Woche"))
        }
        render { CoachHistoryScreen(d.coachChat, onOpen = {}, onBack = {}) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("history:item").fetchSemanticsNodes().size == 2 }
        save("05-fruehere-chats")
    }
}
