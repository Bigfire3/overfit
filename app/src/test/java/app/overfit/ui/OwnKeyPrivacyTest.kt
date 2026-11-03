package app.overfit.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
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
import app.overfit.domain.WeightUnit
import app.overfit.fuel.DeviceKey
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelApi
import app.overfit.fuel.RequestSigner
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import java.util.concurrent.CopyOnWriteArrayList

private class PrivacyKey : DeviceKey {
    private val point = ByteArray(65) { if (it == 0) 4 else (it * 3).toByte() }
    override fun publicPoint() = point
    override fun sign(message: ByteArray) = ByteArray(64) { 7 }
    override fun delete() = Unit
}

/**
 * Guarantee: with an own API key and without switching on Fuel, Spot, the AI chat connection or a short-code QR,
 * the app makes **zero** requests to the overfit backend. A backend URL is configured on purpose and every request
 * of every HttpClient (backend and providers) goes through one counting engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OwnKeyPrivacyTest {
    @get:Rule
    val compose = createComposeRule()

    private val backendHost = "backend.test"
    private val requests = CopyOnWriteArrayList<String>()
    private val backendRequests get() = requests.filter { it.startsWith("$backendHost ") }

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine = MockEngine { req ->
        requests += "${req.url.host} ${req.method.value} ${req.url.encodedPath}"
        val body = if (req.url.host == backendHost) {
            when (req.url.encodedPath) {
                "/v1/config" -> """{"currency":"EUR","unit":"micro-euro","markupBp":800,"storeFeeBp":1500,"models":{},"maxTokensCap":8192,"packages":[],"spot":{"starterMicro":1000000,"rewardMicro":1000000,"tokenTtlSec":45,"monthlyLimitPerInviter":10,"expiryDays":180}}"""
                "/v1/chat/inbox" -> """{"items":[]}"""
                "/v1/chat" -> """{"enabled":false,"photos":false,"connections":[]}"""
                else -> """{"deviceId":"d","balanceMicro":0,"expiring":[]}"""
            }
        } else "{}"
        respond(body, HttpStatusCode.OK, json)
    }

    private val chatAnswer = buildJsonObject { put("reply", JsonPrimitive("Direkt vom Anbieter.")) }.toString()
    private val fake = FakeCoachTransport { prompt, _ ->
        if (prompt.schemaName == CoachChatSchema.NAME) CoachReply(chatAnswer, chatAnswer) else CoachReply("{}", "{}")
    }

    @After
    fun tearDown() = database.close()

    private fun waitFor(text: String) {
        try { compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    private var backDispatcher: OnBackPressedDispatcher? = null

    private fun input(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun ownKeyMeansZeroBackendRequestsUntilFuelIsOpened() {
        store.setName("Fabi")
        store.completeOnboarding()
        store.connectAi(ProviderId.ANTHROPIC, "sk-test")
        val fuel = FuelAccess(kv, "https://$backendHost") { url -> FuelApi(HttpClient(engine), url, RequestSigner(PrivacyKey())) }
        val deps = AppDependencies(
            store, Providers(HttpClient(engine)), data, versionName = "0.1.test", share = NoopPlatformShare(),
            today = { LocalDate(2026, 11, 3) }, fuel = fuel, kv = kv, coachTransport = { fake },
        )
        assertTrue("a backend URL must be configured for this test to mean something", deps.chat.configured)
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            OverfitApp(deps)
        }

        // App start and "back in the foreground", also explicitly, plus the coach queue.
        waitFor("Duo · bald")
        deps.chat.onForeground()
        deps.chat.onBackground()
        deps.coach.processQueue()

        // Every tab, the coach with its privacy note and a chat message, the profile with the Fuel row.
        compose.onNodeWithTag("tab:HISTORY").performClick()
        compose.onNodeWithTag("tab:PHOTOS").performClick()
        compose.onNodeWithTag("tab:COACH").performClick()
        waitFor("Prompts & Vorlagen")
        compose.onNodeWithText("Deine Daten gehen direkt an Anthropic, nicht über overfit-Server.").performScrollTo().assertExists()
        input("coach:tabInput").performScrollTo().performTextInput("Was soll ich morgen trainieren?")
        compose.onNodeWithText("Senden").performScrollTo().performClick()
        waitFor("Direkt vom Anbieter.")
        compose.runOnIdle { backDispatcher!!.onBackPressed() } // the chat covers the tab bar
        waitFor("Prompts & Vorlagen")
        compose.onNodeWithTag("tab:PROFILE").performClick()
        waitFor("Fuel")
        compose.waitForIdle()
        assertEquals("requests to the overfit backend: $requests", emptyList<String>(), backendRequests)

        // Opening Fuel only shows the opt-in; still nothing. Only "Fuel ansehen" talks to the server (control: the counter works).
        compose.onNodeWithText("Fuel", useUnmergedTree = true).performScrollTo().performClick()
        waitFor("Fuel ansehen")
        compose.waitForIdle()
        assertEquals("requests to the overfit backend: $requests", emptyList<String>(), backendRequests)
        compose.onNodeWithText("Fuel ansehen").performClick()
        compose.waitUntilState(15_000) { backendRequests.isNotEmpty() }
    }
}
