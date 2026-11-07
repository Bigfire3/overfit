package app.overfit.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
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
import app.overfit.domain.WeightUnit
import app.overfit.fuel.DeviceKey
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelApi
import app.overfit.fuel.ModelNames
import app.overfit.fuel.RequestSigner
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private class NamesKey : DeviceKey {
    private val point = ByteArray(65) { if (it == 0) 4 else (it * 7).toByte() }
    override fun publicPoint() = point
    override fun sign(message: ByteArray) = ByteArray(64) { 5 }
    override fun delete() = Unit
}

/**
 * The server picks provider and model per credit tier (config switch): the coach model choice shows the names from
 * `/v1/config`, never a built-in Claude name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ServerModelNamesTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val config = """
        {"mode":"test","preset":"openai","markupBp":800,"maxTokensCap":8192,"defaultTier":"standard",
         "tiers":{
           "standard":{"available":true,"provider":"openai","model":"gpt-5","displayName":"GPT-5","vendor":"OpenAI",
             "images":true,"price":{"in":1150000,"out":9200000},"thinkingTokens":0,
             "fallback":{"model":"@cf/meta/llama-4-scout-17b-16e-instruct","displayName":"Llama 4 Scout","vendor":"Cloudflare"}},
           "lite":{"available":true,"provider":"workers-ai","model":"@cf/meta/llama-4-scout-17b-16e-instruct",
             "displayName":"Llama 4 Scout","vendor":"Cloudflare","images":true,"price":{"in":250000,"out":600000},"fallback":null},
           "pro":{"available":false,"provider":"openai","model":"gpt-5-pro","displayName":"GPT-5 Pro","vendor":"OpenAI","fallback":"standard"}},
         "usageProfiles":{"coach":{"inputTokens":6000,"outputTokens":900},"chat":{"inputTokens":3000,"outputTokens":300},
           "autofill":{"inputTokens":800,"outputTokens":200}},
         "modelNames":{"gpt-5":{"displayName":"GPT-5","vendor":"OpenAI"}},
         "packages":[]}
    """.trimIndent()
    private val engine = MockEngine { req ->
        val body = when (req.url.encodedPath) {
            "/v1/config" -> config
            "/v1/chat/inbox" -> """{"items":[]}"""
            "/v1/chat" -> """{"enabled":false,"photos":false,"connections":[]}"""
            else -> """{"deviceId":"d","balanceMicro":4200000}"""
        }
        respond(body, HttpStatusCode.OK, json)
    }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) {
        try { compose.waitUntil(15_000) { shown(text) } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    @After
    fun forgetNames() = ModelNames.clear()

    @Test
    fun coachModelChoiceShowsTheServersModels() {
        store.setName("Fabi")
        store.completeOnboarding()
        store.useFuel()
        val fuel = FuelAccess(kv, "https://backend.test") { url -> FuelApi(HttpClient(engine), url, RequestSigner(NamesKey())) }
        val deps = AppDependencies(
            store, Providers(HttpClient(engine), kv) { fuel.service() }, data, versionName = "0.1.test",
            share = NoopPlatformShare(), today = { LocalDate(2026, 11, 7) }, fuel = fuel, kv = kv,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:COACH").performClick()
        waitFor("Coach-Einstellungen")
        compose.onNodeWithText("Coach-Einstellungen").performScrollTo().performClick()

        waitFor("GPT-5 (OpenAI)")
        assertTrue(shown("Llama 4 Scout (Cloudflare)"))
        // The unavailable tier is not offered; no built-in Claude name anywhere.
        assertTrue(!shown("GPT-5 Pro", substring = true))
        assertTrue(compose.onRoot().printToString(), !shown("Claude", substring = true))
        // The config is kept for offline use.
        assertTrue(kv.get(app.overfit.ai.FuelProvider.CONFIG_KEY).orEmpty().contains("gpt-5"))
    }
}
