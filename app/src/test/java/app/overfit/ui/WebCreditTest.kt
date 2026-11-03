package app.overfit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import app.overfit.domain.ThemeMode
import app.overfit.domain.WeightUnit
import app.overfit.fuel.DeviceKey
import app.overfit.fuel.Distribution
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelApi
import app.overfit.fuel.FuelSession
import app.overfit.fuel.RequestSigner
import app.overfit.i18n.GermanFuel
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.share.NoopPlatformShare
import app.overfit.ui.fuel.FuelScreen
import app.overfit.ui.fuel.RedeemCodeScreen
import app.overfit.ui.theme.OverfitTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

private class WebKey : DeviceKey {
    private val point = ByteArray(65) { if (it == 0) 4 else (it * 5).toByte() }
    override fun publicPoint() = point
    override fun sign(message: ByteArray) = ByteArray(64) { 3 }
    override fun delete() = Unit
}

private class ManualLifecycle : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

/**
 * Credit screen: "Top up on the web" follows the server flag and the build's channel (sideload on, store off),
 * opens the purchase link in the browser and reloads the balance when the app comes back; "Redeem code" books a code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class WebCreditTest {
    @get:Rule
    val compose = createComposeRule()

    private val host = "backend.test"
    private val requests = CopyOnWriteArrayList<String>()
    private val bodies = CopyOnWriteArrayList<String>()
    private var channels = """["sideload"]"""
    private var enabled = true
    private var balance = 1_000_000L
    private var redeemAnswer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"creditedMicro":5000000,"balanceMicro":6000000}"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun bodyOf(req: HttpRequestData) = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private val engine = MockEngine { req ->
        requests += "${req.method.value} ${req.url.encodedPath}"
        bodies += bodyOf(req)
        when (req.url.encodedPath) {
            "/v1/config" -> respond(
                """{"mode":"test","markupBp":2800,"packages":[{"productId":"overfit.fuel.5","priceMicro":6990000,"creditMicro":5000000}],
                   "webPurchase":{"enabled":$enabled,"testMode":true,"provider":"stripe","channels":$channels,
                   "packages":[{"id":"web.5","priceMicro":5000000,"creditMicro":5000000},{"id":"web.10","priceMicro":10000000,"creditMicro":10000000}]}}""",
                HttpStatusCode.OK, json,
            )
            "/v1/purchase/web-link" -> respond(
                """{"url":"https://bigfire3.github.io/overfit/guthaben/#t=tok123&lang=de","expiresAt":1,"ttlSec":900}""", HttpStatusCode.OK, json,
            )
            "/v1/codes/redeem" -> respond(redeemAnswer.second, redeemAnswer.first, json)
            "/v1/devices" -> respond("""{"deviceId":"d"}""", HttpStatusCode.OK, json)
            else -> respond("""{"accountId":"a","balanceMicro":$balance,"expiring":[]}""", HttpStatusCode.OK, json)
        }
    }
    private val kv = InMemoryKeyValueStore()
    private val session = FuelSession(FuelAccess(kv, "https://$host") { url -> FuelApi(HttpClient(engine), url, RequestSigner(WebKey())) })
    private val opened = CopyOnWriteArrayList<String>()
    private val uriHandler = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
    private val lifecycle = ManualLifecycle()
    private val redeemOpen = androidx.compose.runtime.mutableStateOf(false)

    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) {
        try { compose.waitUntil(10_000) { shown(text) } } catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    private fun showFuel(distribution: Distribution, onRedeem: () -> Unit = {}) = compose.setContent {
        OverfitTheme(ThemeMode.LIGHT) {
            CompositionLocalProvider(
                LocalStrings provides stringsFor(Language.DE),
                LocalFuelStrings provides GermanFuel,
                LocalUriHandler provides uriHandler,
                LocalLifecycleOwner provides lifecycle,
            ) {
                if (redeemOpen.value) RedeemCodeScreen(session, onBack = {})
                else FuelScreen(session, onBack = {}, onLedger = {}, onRecovery = {}, onInviteShow = {}, onInviteRedeem = {},
                    fuelActive = true, onRedeemCode = { onRedeem(); redeemOpen.value = true }, distribution = distribution)
            }
        }
    }

    @Test
    fun sideloadedBuildOpensPurchaseLinkAndReloadsBalanceOnReturn() {
        showFuel(Distribution.Sideload)
        waitFor("Im Web aufladen")
        assertTrue("test-mode note", shown("Testmodus – keine echte Zahlung"))
        // Packages per channel with both values: web 1:1, store with the surcharge shown openly.
        assertTrue("web package", shown("10,00 € Guthaben · Preis: 10,00 €"))
        assertTrue("store package", shown("5,00 € Guthaben · 6,99 € (inkl. Store-Gebühr)"))
        waitFor("1,00")
        val accountCalls = { requests.count { it == "GET /v1/account" } }
        val before = accountCalls()

        compose.onNodeWithText("Im Web aufladen").performScrollTo().performClick()
        compose.waitUntilState { opened.isNotEmpty() }
        assertEquals("https://bigfire3.github.io/overfit/guthaben/#t=tok123&lang=de", opened.single())
        assertTrue(requests.contains("POST /v1/purchase/web-link"))
        assertTrue("locale goes along", bodies.any { it.contains("\"locale\":\"de\"") })

        // Paid in the browser, back in the app → balance is fetched again and shown
        balance = 6_000_000
        compose.runOnIdle {
            lifecycle.registry.currentState = Lifecycle.State.STARTED
            lifecycle.registry.currentState = Lifecycle.State.RESUMED
        }
        waitFor("6,00")
        assertTrue(accountCalls() > before)
    }

    @Test
    fun storeBuildHidesWebTopUpUntilServerAllowsStore() {
        showFuel(Distribution.Store)
        waitFor("Code einlösen")
        compose.waitUntilState { requests.contains("GET /v1/config") }
        compose.waitForIdle()
        assertFalse("store build: no web purchase by default", shown("Im Web aufladen"))
        assertFalse("no web packages either", shown("10,00 € Guthaben · Preis: 10,00 €"))
        assertTrue("store package with surcharge", shown("5,00 € Guthaben · 6,99 € (inkl. Store-Gebühr)"))
    }

    @Test
    fun storeBuildShowsWebTopUpWhenServerAllowsStore() {
        channels = """["sideload","store"]"""
        showFuel(Distribution.Store)
        waitFor("Im Web aufladen")
    }

    @Test
    fun serverFlagOffHidesWebTopUp() {
        enabled = false
        showFuel(Distribution.Sideload)
        waitFor("Code einlösen")
        compose.waitUntilState { requests.contains("GET /v1/config") }
        compose.waitForIdle()
        assertFalse(shown("Im Web aufladen"))
    }

    @Test
    fun redeemCode() {
        var redeemTapped = false
        showFuel(Distribution.Sideload) { redeemTapped = true }
        waitFor("Code einlösen")
        compose.onNodeWithText("Code einlösen").performScrollTo().performClick()
        assertTrue(redeemTapped)
        waitFor("Guthaben-Code einlösen")
        compose.onNode(hasSetTextAction()).performTextInput("abcd-efgh-jkmn")
        compose.onNodeWithText("Einlösen").performClick()
        waitFor("5,00 € Guthaben gutgeschrieben.")
        assertTrue(bodies.any { it.contains("\"code\":\"ABCD-EFGH-JKMN\"") })

        redeemAnswer = HttpStatusCode.Conflict to """{"error":{"code":"already_redeemed","message":"x"}}"""
        compose.onNode(hasSetTextAction()).performTextInput("abcd-efgh-jkmn")
        compose.onNodeWithText("Einlösen").performClick()
        waitFor("Dieser Code wurde schon eingelöst.")
    }
}

/** "Zurück zur App" on the purchase page (overfit://guthaben) opens the credit screen, not the training import. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CreditReturnLinkTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG)).also { it.completeOnboarding(); it.useFuel() }
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { ByteArray(0) })
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine = MockEngine { req ->
        when (req.url.encodedPath) {
            "/v1/config" -> respond("""{"packages":[]}""", HttpStatusCode.OK, json)
            "/v1/chat/inbox" -> respond("""{"items":[]}""", HttpStatusCode.OK, json)
            "/v1/chat" -> respond("""{"enabled":false,"photos":false,"connections":[]}""", HttpStatusCode.OK, json)
            else -> respond("""{"accountId":"a","balanceMicro":0,"expiring":[]}""", HttpStatusCode.OK, json)
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun returnLinkOpensCreditScreen() {
        val fuel = FuelAccess(kv, "https://backend.test") { url -> FuelApi(HttpClient(engine), url, RequestSigner(WebKey())) }
        val deps = AppDependencies(
            store, Providers(HttpClient(engine)), data, versionName = "0.1.test", share = NoopPlatformShare(),
            today = { LocalDate(2026, 11, 3) }, fuel = fuel, kv = kv,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForIdle()
        deps.inbox.offer("overfit://guthaben")
        try { compose.waitUntil(10_000) { compose.onAllNodes(hasText("Code einlösen")).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { throw AssertionError(compose.onRoot().printToString(), e) }
        compose.waitUntilState { deps.inbox.pending.value == null }
        assertFalse("not the training import", compose.onAllNodes(hasText("Importieren", substring = true)).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodes(hasText("Code einlösen")).fetchSemanticsNodes().isEmpty())
    }
}
