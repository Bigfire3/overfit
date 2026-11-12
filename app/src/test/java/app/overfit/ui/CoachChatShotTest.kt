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
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
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
import app.overfit.data.db.AiRequestEntity
import app.overfit.data.db.OverfitDatabase
import app.overfit.fuel.DemoFuelService
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelSession
import app.overfit.ui.coach.CreditChip
import app.overfit.ui.coach.CreditPopupContent
import app.overfit.ui.coach.ModelPicker
import app.overfit.ui.components.BottomSheet
import app.overfit.ui.components.CenterPopup
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

    private val demo = DemoFuelService()
    private val now = System.currentTimeMillis()
    private val push = ChatAttachment.Training("s1", "Push", startedAt = now)
    private val pull = ChatAttachment.Training("s0", "Pull", startedAt = now - 2 * 86_400_000L)

    private fun deps(): AppDependencies {
        store.setName("Fabi")
        store.completeOnboarding()
        store.useFuel()
        return AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })) { demo }, data, versionName = "t", share = NoopPlatformShare(),
            today = { LocalDate(2026, 11, 3) }, coachTransport = { fake },
        )
    }

    private fun env(d: AppDependencies) = CoachChatEnv(
        chat = d.coachChat,
        prefs = data.coachChatPrefs,
        lastTraining = flowOf(push),
        trainings = flowOf(listOf(push, pull)),
        plans = flowOf(listOf(ChatAttachment.Plan(1, "Push"), ChatAttachment.Plan(2, "Pull"))),
        exercises = { listOf(ChatAttachment.Exercise("wg:bench-press", "Bankdrücken") to "Bestwert 80×8") },
        photos = flowOf(photos),
        templateNames = flowOf(setOf("Push")),
        latestWeight = { "82,4 kg · Stand 02.11." },
        modelLabel = MutableStateFlow("Claude Sonnet 5.5"),
        pricing = MutableStateFlow(app.overfit.coach.ChatPricing.Credit(app.overfit.fuel.TierPrice(1_840_000, 9_200_000), 2_800, 500)),
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
    private fun Tab(env: CoachChatEnv, request: ChatOpenRequest? = null, creditChip: @Composable () -> Unit = {}) {
        CoachChatTabScreen(env, request, {}, onHistory = {}, onStory = {}, onModelChip = {}, onApplyWorkout = {}, creditChip = creditChip)
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
        runBlocking { d.coachChat.sendNow("c1", ChatSendRequest("Wie lief mein Training heute?", listOf(push))) }
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
    fun modelSheet() {
        val d = deps()
        val e = env(d)
        render {
            Box {
                Tab(e)
                BottomSheet(true, onDismiss = {}, closeLabel = "Zurück") {
                    ModelPicker(store.state.value, store, d.providers, d.coach, onApplied = {}, onSurface = true)
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Claude Opus 5.5").fetchSemanticsNodes().isNotEmpty() }
        save("06b-modell-sheet")
    }

    @Test
    fun creditPopup() {
        val d = deps()
        val e = env(d)
        val t = 1_794_052_800_000L
        runBlocking {
            val dao = database.aiRequestDao()
            dao.upsert(AiRequestEntity("r1", t - 60_000, providerId = "FUEL", purpose = "chat", requestedModel = "claude-sonnet-5-5", servedModel = "claude-sonnet-5-5", status = "OK", costMicro = 21_668))
            dao.upsert(AiRequestEntity("r2", t - 3_600_000, providerId = "FUEL", purpose = "review", requestedModel = "claude-opus-5-5", servedModel = "claude-opus-5-5", status = "OK", costMicro = 104_120))
        }
        val session = FuelSession(FuelAccess(kv, "") { error("No backend") }).also { it.startDemo() }
        render {
            Box {
                Tab(e, creditChip = { CreditChip(session, fuelActive = true, onClick = {}) })
                CenterPopup(true, onDismiss = {}, closeLabel = "Schließen") {
                    CreditPopupContent(session, database.aiRequestDao(), onOpenFuel = {}, onAllRequests = {}, onClose = {})
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Alle Anfragen ansehen").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("4,20 €").fetchSemanticsNodes().isNotEmpty() }
        save("08b-guthaben-popup")
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

    // --- Session BH (8.11.): earlier chat opened, price on the send button, review card after the training ------------

    @Test
    fun openedEarlierChat() {
        val d = deps()
        runBlocking { d.coachChat.sendNow("c1", ChatSendRequest("Wie lief mein Training heute?", listOf(push, ChatAttachment.Photo("p1", "Foto 1")))) }
        val e = env(d)
        render { CoachChatTabScreen(e, null, {}, onHistory = {}, onStory = {}, onModelChip = {}, onApplyWorkout = {}, openedChatId = "c1") }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chat:workout").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("coach:price", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // From the start: the question with its attachment cards.
        compose.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction())[0].performScrollToIndex(0)
        save("bh-01-frueherer-chat")
    }

    @Test
    fun priceWithoutAndWithPhotos() {
        val e = env(deps())
        render { Tab(e, ChatOpenRequest(draft = "Wie sieht meine Technik aus?", attachments = listOf(push))) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("coach:price", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        save("bh-02-preis-ohne-fotos")
    }

    @Test
    fun priceWithPhotos() {
        val e = env(deps())
        val photos = listOf(ChatAttachment.Photo("p1", "Foto 1"), ChatAttachment.Photo("p2", "Foto 2"))
        render { Tab(e, ChatOpenRequest(draft = "Wie sieht meine Technik aus?", attachments = listOf(push) + photos)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("coach:price", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        save("bh-03-preis-mit-zwei-fotos")
    }

    @Test
    fun finishReviewCard() {
        val e = env(deps())
        render {
            androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 24.dp)) {
                app.overfit.ui.coach.CoachFinishSection(e, "s1", onReview = {})
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("≈", substring = true).fetchSemanticsNodes().isNotEmpty() }
        save("bh-04-abschluss-karte")
    }
}
