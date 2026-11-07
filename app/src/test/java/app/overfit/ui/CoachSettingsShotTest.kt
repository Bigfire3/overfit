package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.DeviceDefaults
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.data.db.AiRequestEntity
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.fuel.DemoFuelService
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelSession
import app.overfit.share.NoopPlatformShare
import app.overfit.ui.coach.CreditPopupContent
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Theme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Evidence screenshots (360 dp, dark) of the coach settings: model picker (credit / own key), credit popup content,
 * settings → Coach, coach instructions. Only runs with `SCREENSHOT_DIR` (e.g. docs/screenshots/coach-chat).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class CoachSettingsShotTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore().apply { put(AppStore.KEY_COACH_STORY_SEEN, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() })
    private val engine = MockEngine {
        respond(
            """{"data":[{"id":"claude-sonnet-5-5"},{"id":"claude-opus-5-5"},{"id":"claude-haiku-5-5"},{"id":"claude-sonnet-4-5"}]}""",
            HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    private val demo = DemoFuelService()

    private fun outDir(): File {
        val dir = System.getenv("SCREENSHOT_DIR")
        assumeTrue(dir != null)
        return File(dir!!).also { it.mkdirs() }
    }

    private fun shot(out: File, name: String) {
        compose.waitForIdle()
        File(out, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) {
        try { compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    private fun app(fuel: Boolean) {
        store.setName("Fabi")
        store.completeOnboarding()
        store.connectAi(ProviderId.ANTHROPIC, "sk-ant-api03-1234")
        if (fuel) store.useFuel()
        val deps = AppDependencies(
            store, Providers(HttpClient(engine)) { demo }, data, versionName = "0.1.test", share = NoopPlatformShare(),
            today = { LocalDate(2026, 11, 3) }, kv = kv,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
        compose.onNodeWithTag("tab:PROFILE").performClick()
        waitFor("Coach-Anweisungen")
    }

    @Test
    fun modelCredit() {
        val out = outDir()
        app(fuel = true)
        compose.onNodeWithText("KI-Zugang").performScrollTo().performClick()
        waitFor("Claude Opus 5.5")
        shot(out, "06-modell-guthaben")
    }

    @Test
    fun modelOwnKey() {
        val out = outDir()
        app(fuel = false)
        compose.onNodeWithText("KI-Zugang").performScrollTo().performClick()
        waitFor("Gespeicherter Schlüssel ••••1234")
        compose.onNodeWithTag("key:test").performClick()
        waitFor("Schlüssel funktioniert · 4 Modelle gefunden")
        compose.onNodeWithTag("model:suggestion:claude-sonnet-5-5").performScrollTo().performClick()
        // Suggestions and "Apply" in view (the test result line sits right above them).
        compose.onNodeWithTag("model:apply").performScrollTo()
        shot(out, "07-modell-eigener-schluessel")
    }

    @Test
    fun creditPopup() {
        val out = outDir()
        val now = 1_794_052_800_000L
        runBlocking {
            val dao = database.aiRequestDao()
            dao.upsert(AiRequestEntity("r1", now - 60_000, providerId = "FUEL", purpose = "chat", requestedModel = "claude-sonnet-5-5", servedModel = "claude-sonnet-5-5", status = "OK", costMicro = 21_668))
            dao.upsert(AiRequestEntity("r2", now - 3_600_000, providerId = "FUEL", purpose = "review", requestedModel = "claude-opus-5-5", servedModel = "claude-opus-5-5", status = "OK", costMicro = 104_120))
            dao.upsert(AiRequestEntity("r3", now - 7_200_000, providerId = "FUEL", purpose = "autofill", requestedModel = "claude-haiku-5-5", servedModel = "claude-haiku-5-5", status = "OK", costMicro = 261))
            dao.upsert(AiRequestEntity("r4", now - 90_000_000, providerId = "FUEL", purpose = "chat", requestedModel = "claude-sonnet-5-5", servedModel = "claude-sonnet-5-5", status = "OK", costMicro = 19_870))
        }
        val session = FuelSession(FuelAccess(kv, "") { error("No backend") }).also { it.startDemo() }
        compose.setContent {
            OverfitTheme {
                Box(Modifier.fillMaxSize().background(Theme.colors.background).padding(16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().background(Theme.colors.surface, RoundedCornerShape(24.dp))) {
                        CreditPopupContent(session, database.aiRequestDao(), onOpenFuel = {}, onAllRequests = {}, onClose = {})
                    }
                }
            }
        }
        waitFor("Alle Anfragen ansehen")
        waitFor("4,20 €")
        shot(out, "08-guthaben-popup")
    }

    @Test
    fun settingsCoach() {
        val out = outDir()
        app(fuel = false)
        // Bottom of the coach section first, then back up to its title: the section fills the screen.
        compose.onNodeWithText("Coach ⓘ – was die KI kann").performScrollTo()
        compose.onNodeWithText("COACH").performScrollTo()
        shot(out, "09-einstellungen-coach")
    }

    @Test
    fun instructions() {
        val out = outDir()
        app(fuel = false)
        compose.onNodeWithText("Coach-Anweisungen").performScrollTo().performClick()
        waitFor("Overfit-Grundanweisung")
        shot(out, "10-coach-anweisungen")
    }
}
