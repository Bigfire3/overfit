package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.db.AiRequestEntity
import app.overfit.data.db.OverfitDatabase
import app.overfit.fuel.DeviceKey
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelApi
import app.overfit.fuel.FuelSession
import app.overfit.fuel.RequestSigner
import app.overfit.ui.fuel.RequestLogScreen
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Theme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

private class LogKey : DeviceKey {
    private val point = ByteArray(65) { if (it == 0) 4 else (it * 5).toByte() }
    override fun publicPoint() = point
    override fun sign(message: ByteArray) = ByteArray(64) { 3 }
    override fun delete() = Unit
}

/** "Abrechnung": every AI request as a card (purpose, time, model, cost, status), details on tap, server sync only with credit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class RequestLogTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ null }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val dao = database.aiRequestDao()
    private val requests = CopyOnWriteArrayList<String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine = MockEngine { req ->
        requests += "${req.method.value} ${req.url.encodedPath}"
        val body = when (req.url.encodedPath) {
            "/v1/account/requests" -> """{"entries":[{"id":42,"ts":${(at(4, 9, 15) / 1000)},"durationMs":2100,"purpose":"plan","tier":"standard",
                "status":"ok","requestedModel":"claude-sonnet-5-5","model":"claude-sonnet-5-5","inputTokens":5200,"outputTokens":900,"chargedMicro":12300}],"next":null}"""
            else -> """{"deviceId":"d","balanceMicro":0}"""
        }
        respond(body, HttpStatusCode.OK, json)
    }
    private val session = FuelSession(FuelAccess(InMemoryKeyValueStore(), "https://backend.test") { url -> FuelApi(HttpClient(engine), url, RequestSigner(LogKey())) })

    private fun at(day: Int, hour: Int, minute: Int) =
        LocalDateTime(2026, 11, day, hour, minute).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private val now = at(7, 12, 0)

    private val sample = listOf(
        // Credit, coach review, 0.4 ct.
        AiRequestEntity(
            "r1", at(7, 10, 5), 3240, "FUEL", "review", "claude-sonnet-5-5", "claude-sonnet-5-5", "OK",
            inputTokens = 12_345, outputTokens = 678, cacheReadTokens = 8_000, cacheWriteTokens = 0, costMicro = 4_000, serverId = 7,
        ),
        // Credit, chat answered by a replacement model.
        AiRequestEntity(
            "r2", at(6, 18, 30), 5100, "FUEL", "chat", "claude-opus-5-5", "claude-sonnet-5-5", "OK",
            fallbackReason = "HTTP 529: Overloaded", inputTokens = 2_000, outputTokens = 300, costMicro = 1_234_000,
        ),
        // Credit, failed: not enough credit.
        AiRequestEntity("r3", at(6, 9, 0), 400, "FUEL", "review", "claude-haiku-5-5", null, "FAILED", errorKind = "insufficient_funds"),
        // Own key, fill in with AI.
        AiRequestEntity("r4", at(5, 20, 10), 1800, "OPENROUTER", "autofill", "anthropic/claude-haiku-5-5", "anthropic/claude-haiku-5-5", "OK", inputTokens = 900, outputTokens = 120),
        // Own key, no connection.
        AiRequestEntity("r5", at(5, 8, 0), 15_000, "ANTHROPIC", "chat", "claude-sonnet-5-5", null, "FAILED", errorKind = "NETWORK"),
        // Credit, nothing charged (an old row from the former gifted reviews keeps gift = true): shown neutrally.
        AiRequestEntity("r6", at(4, 7, 45), 2900, "FUEL", "review", "claude-haiku-5-5", "claude-haiku-5-5", "OK", costMicro = 0, gift = true),
    )

    private fun insert(entries: List<AiRequestEntity>) = runBlocking { entries.forEach { dao.upsert(it) } }

    private fun show(fuelActive: Boolean, tagged: Boolean = false) {
        compose.setContent {
            OverfitTheme {
                val content = @androidx.compose.runtime.Composable {
                    RequestLogScreen(dao, session, fuelActive = fuelActive, clock = { now }, onBack = {})
                }
                if (tagged) Box(Modifier.width(360.dp).height(640.dp).background(Theme.colors.background).testTag("shot")) { content() }
                else content()
            }
        }
    }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) {
        try { compose.waitUntil(10_000) { shown(text, substring) } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    @Test
    fun listShowsPurposeModelCostAndErrors() {
        insert(sample)
        show(fuelActive = false)
        waitFor("Abrechnung")
        waitFor("Claude Sonnet 5.5 · Guthaben")
        assertTrue(shown("Coach-Auswertung"))
        assertTrue(shown("Mit KI ausfüllen"))
        assertTrue(shown("0,4 ct · ok"))
        assertTrue(shown("1,23 € · ok"))
        assertTrue(shown("nichts berechnet · ok"))
        assertTrue(!shown("geschenkt", substring = true))
        assertTrue(shown("Fehler: Guthaben reichte nicht"))
        assertTrue(shown("Fehler: Keine Verbindung"))
        assertTrue(shown("über deinen Schlüssel · ok"))
        assertTrue(shown("über OpenRouter", substring = true))
        assertTrue(shown("Claude Sonnet 5.5 · über Anthropic"))
        // Sum of the last 30 days (credit only): 0.4 ct + 1.234 € = 1.238 € → 1,24 €.
        assertTrue(shown("Letzte 30 Tage: 1,24 € Guthaben"))
        assertTrue(shown("nie gespeichert", substring = true))
        // Newest first: the review of today comes before the one of Nov 4.
        val top = compose.onNodeWithTag("request:r1").fetchSemanticsNode().positionInRoot.y
        val bottom = compose.onNodeWithTag("request:r6").fetchSemanticsNode().positionInRoot.y
        assertTrue(top < bottom)
        // Own key: nothing reached the backend.
        compose.waitForIdle()
        assertEquals(emptyList<String>(), requests.toList())
    }

    @Test
    fun tapShowsDetailsWithFallback() {
        insert(sample)
        show(fuelActive = false)
        waitFor("Claude Sonnet 5.5 · Guthaben")
        assertTrue(!shown("Tokens:", substring = true))
        compose.onNodeWithTag("request:r2").performScrollTo().performClick()
        waitFor(
            "Zuerst Claude Opus 5.5: nicht verfügbar (HTTP 529: Overloaded), nicht berechnet · " +
                "Geantwortet hat Claude Sonnet 5.5, berechnet zum Preis des günstigeren der beiden Modelle",
        )
        assertTrue(shown("Tokens: 2.000 ein · 300 aus"))
        assertTrue(shown("Dauer: 5,1 s"))
        assertTrue(shown("Auf diesem Gerät protokolliert"))
        compose.onNodeWithTag("request:r1").performScrollTo().performClick()
        waitFor("Tokens: 12.345 ein · 678 aus")
        assertTrue(shown("Cache: 8.000 gelesen · 0 geschrieben"))
        assertTrue(shown("Server-Protokoll Nr. 7"))
        // Tap again closes the details.
        compose.onNodeWithTag("request:r2").performScrollTo().performClick()
        compose.waitUntil(5_000) { !shown("Geantwortet hat", substring = true) }
    }

    @Test
    fun emptyState() {
        show(fuelActive = false)
        waitFor("Noch keine KI-Anfragen.")
        assertTrue(!shown("Letzte 30 Tage", substring = true))
    }

    @Test
    fun creditSyncsTheServerLog() {
        show(fuelActive = true)
        waitFor("Plan")
        assertTrue(requests.any { it.startsWith("GET /v1/account/requests") })
        assertTrue(shown("1,2 ct · ok"))
        compose.onNodeWithText("Plan").performClick()
        waitFor("Server-Protokoll Nr. 42")
    }

    @Test
    fun ownKeyMakesNoBackendRequest() {
        insert(sample.take(1))
        show(fuelActive = false)
        waitFor("Coach-Auswertung")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(emptyList<String>(), requests.toList())
    }

    /** Evidence screenshot (360 dp, dark); runs only with `SCREENSHOT_DIR`. */
    @Test
    fun screenshot() {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        insert(sample)
        show(fuelActive = false, tagged = true)
        waitFor("Claude Sonnet 5.5 · Guthaben")
        compose.onNodeWithTag("request:r2").performClick()
        waitFor("Geantwortet hat", substring = true)
        compose.waitForIdle()
        val out = File(dir!!).also { it.mkdirs() }
        File(out, "abrechnung-dunkel.png").outputStream().use {
            compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
