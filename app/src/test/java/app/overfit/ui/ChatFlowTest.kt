package app.overfit.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
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
import app.overfit.fuel.RequestSigner
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class TestKey : DeviceKey {
    private val point = ByteArray(65) { if (it == 0) 4 else (it * 3).toByte() }
    override fun publicPoint() = point
    override fun sign(message: ByteArray) = ByteArray(64) { 7 }
    override fun delete() = Unit
}

/** Profile → KI-Coach → a chat provider: instructions, "copy context" without a server, consent → upload → pairing code. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ChatFlowTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(app.overfit.data.AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG)).also {
        it.setName("Fabian"); it.completeOnboarding()
    }
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { ByteArray(0) })
    private val share = NoopPlatformShare()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<String>()

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun await(text: String, substring: Boolean = false) {
        try { compose.waitUntil(10_000) { shown(text, substring) } } catch (e: Throwable) { throw AssertionError(compose.onRoot().printToString(), e) }
    }

    private fun deps(fuel: FuelAccess? = null) =
        if (fuel == null) AppDependencies(store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "t", share = share, today = { LocalDate(2026, 11, 3) }, kv = kv)
        else AppDependencies(store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "t", share = share, today = { LocalDate(2026, 11, 3) }, fuel = fuel, kv = kv)

    @Test
    fun withoutBackendContextAndGemInstructionsStillWork() {
        compose.setContent { OverfitApp(deps()) }
        compose.onNodeWithTag("tab:COACH").performClick()
        compose.onNodeWithText("KI-Chat-Verbindungen".uppercase()).performScrollTo().assertExists()
        compose.onNodeWithText("Gemini").performScrollTo().performClick()
        await("Startet bald".uppercase())
        compose.onNodeWithText("Ersatzweg für Gemini: eigenes Gem".uppercase()).assertExists()

        // The prompt shows its full text before it is copied.
        compose.onNodeWithTag("prompt:Kontext kopieren").performScrollTo().assertExists()
        compose.onNodeWithTag("prompt-copy:Kontext kopieren").performScrollTo().performClick()
        await("Kopiert")
        val ctx = share.clipboard.orEmpty()
        assertTrue(ctx, ctx.contains("Das ist mein Trainingsstand aus der App Overfit"))
        assertTrue(ctx.contains("Fabian"))
        assertTrue(ctx.contains("```json"))

        compose.onNodeWithTag("prompt-copy:Gem-/GPT-Anleitung kopieren").performScrollTo().performClick()
        assertTrue(share.clipboard.orEmpty().contains("Trainings-Coach"))
    }

    @Test
    fun connectUploadsThenShowsPairingCode() {
        val engine = MockEngine { req ->
            requests += "${req.method.value} ${req.url.encodedPath}"
            val body = when (req.url.encodedPath) {
                "/v1/chat/pairing" -> """{"code":"K7QM-3XWP","expiresAt":1762128600,"ttlSec":600}"""
                "/v1/chat/sync" -> """{"applied":1,"skipped":0,"deleted":0,"lastSyncAt":1762128500}"""
                "/v1/chat/enable", "/v1/chat" -> """{"enabled":true,"photos":false,"connections":[]}"""
                else -> """{"deviceId":"d"}"""
            }
            respond(body, HttpStatusCode.OK, json)
        }
        val fuel = FuelAccess(kv, "https://backend.test") { url -> FuelApi(HttpClient(engine), url, RequestSigner(TestKey())) }
        compose.setContent { OverfitApp(deps(fuel)) }
        compose.onNodeWithTag("tab:COACH").performClick()
        compose.onNodeWithText("Claude").performScrollTo().performClick()
        await("https://backend.test/mcp", substring = true)
        compose.onNodeWithText("KI-Chat verbinden").performClick()

        // consent with the body-photos switch (off by default)
        await("Daten für den KI-Chat freigeben")
        compose.onNodeWithText("Körperfotos teilen").assertExists()
        compose.onNodeWithText("Zustimmen und verbinden").performClick()
        await("KI-Chat ist eingeschaltet".uppercase())
        // The first sync runs in the background after "enabled" is shown.
        compose.waitUntilState(5_000) { "POST /v1/chat/sync" in requests.toList() }
        assertTrue(requests.toString(), "POST /v1/chat/enable" in requests && "POST /v1/chat/sync" in requests)
        assertTrue(requests.indexOf("POST /v1/chat/enable") < requests.indexOf("POST /v1/chat/sync"))

        compose.onNodeWithText("Code anzeigen").performClick()
        await("K7QM-3XWP")
        await("Läuft ab in", substring = true)
        assertEquals(true, kv.get("overfit.chat.enabled") == "1")
    }
}
