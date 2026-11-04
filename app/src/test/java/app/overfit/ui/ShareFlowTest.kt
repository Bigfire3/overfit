package app.overfit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import app.overfit.share.AiPrompt
import app.overfit.share.ImportInbox
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Import from the clipboard and from an opened link (inbox), samples, AI prompt. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ShareFlowTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // Tests that look at an empty app opt out of the first-start sample workouts (Push/Pull/Legs), as an existing install would.
    private val kv = InMemoryKeyValueStore().apply { put(AppData.KEY_SAMPLES_SEEDED, "1") }
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(
        database, kv,
        readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
        readSample = { File("../shared/src/commonMain/composeResources/files/samples/$it.json").readBytes() },
    )
    private val share = NoopPlatformShare()
    private val inbox = ImportInbox()
    private val providers = Providers(HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }))

    private fun launchOnboarded() {
        store.setName("Kim"); store.setBirthYear(1995); store.completeOnboarding()
        compose.setContent {
            OverfitApp(
                AppDependencies(store, providers, data, versionName = "t", share = share, inbox = inbox, today = { LocalDate(2026, 11, 2) }),
            )
        }
    }

    /** Training tab → "+" (new workout screen with the import rows under "Weitere Wege"). */
    private fun openPlus() {
        compose.onNodeWithContentDescription("Neues Workout").performClick()
        waitFor("Weitere Wege".uppercase())
    }

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun importFromClipboardShowsPreviewAndSaves() {
        launchOnboarded()
        share.clipboard = "Hier dein Plan:\n```json\n${AiPrompt.EXAMPLE}\n```\nViel Erfolg!"
        openPlus()
        waitFor("Workout importieren")
        compose.onNodeWithText("Workout importieren").performScrollTo().performClick()
        compose.onNodeWithText("Aus Zwischenablage importieren").performClick()
        // Wait for the preview itself: the import screen shows the AI prompt, whose example also says "Oberkörper A".
        waitFor("Vorschau")
        compose.onNodeWithText("Vorschau").assertIsDisplayed()
        compose.onNodeWithText("Bench Press").assertIsDisplayed()
        compose.onNodeWithText("3 Übungen").assertIsDisplayed()
        compose.onNodeWithText("Übernehmen").performClick()
        // The new workout opens in its plan screen (primary action "Starten" pinned in the footer).
        waitFor("Starten")
        // The plan screen shows its footer before the workout is loaded: wait for the exact name and "Bearbeiten".
        compose.waitUntil(20_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Oberkörper A")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(androidx.compose.ui.test.hasText("Bearbeiten")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Oberkörper A").assertIsDisplayed()
        compose.onNodeWithText("Bearbeiten").assertIsDisplayed()
        assertEquals(1, runBlocking { data.templates.templates.first() }.size)
    }

    @Test
    fun emptyClipboardAndGarbageShowErrors() {
        launchOnboarded()
        openPlus()
        waitFor("Workout importieren")
        compose.onNodeWithText("Workout importieren").performScrollTo().performClick()
        compose.onNodeWithText("Aus Zwischenablage importieren").performClick()
        waitFor("Die Zwischenablage ist leer.")
        share.clipboard = "Einkaufsliste: Milch, Eier"
        compose.onNodeWithText("Aus Zwischenablage importieren").performClick()
        waitFor("kein Overfit-Training")
    }

    @Test
    fun openedLinkGoesToImportPreview() {
        launchOnboarded()
        val link = app.overfit.share.ShareFormat.toLink(
            (app.overfit.share.ShareFormat.parseInput(AiPrompt.EXAMPLE) as app.overfit.share.ShareParse.Ok).training,
        )
        inbox.offer(link)
        waitFor("Vorschau")
        compose.onNodeWithText("Oberkörper A").assertIsDisplayed()
    }

    @Test
    fun firstStartCreatesSamples_deletingKeepsThemGone() {
        kv.remove(AppData.KEY_SAMPLES_SEEDED)
        launchOnboarded()
        waitFor("Legs"); waitFor("Pull"); waitFor("Push")
        compose.waitUntilState(10_000) { runBlocking { data.templates.templates.first() }.size == 3 }
        val pull = runBlocking { data.templates.templates.first() }.first { it.name == "Pull" }
        runBlocking { data.templates.delete(pull.id) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pull").fetchSemanticsNodes().isEmpty() }
        // Next start: the flag is set, nothing comes back
        assertEquals(0, runBlocking { data.seedSamplesOnce() }.getOrThrow())
        assertEquals(listOf("Push", "Legs"), runBlocking { data.templates.templates.first() }.map { it.name })
        // There is no "add sample workouts" row anymore, neither in the Training tab nor in the profile.
        compose.onAllNodesWithText("Beispiel-Workouts hinzufügen").assertCountEquals(0)
        compose.onNode(androidx.compose.ui.test.hasText("Profil") and androidx.compose.ui.test.hasClickAction()).performClick()
        waitFor("Über Overfit")
        compose.onAllNodesWithText("Beispiel-Workouts hinzufügen").assertCountEquals(0)
    }

    @Test
    fun aiPromptIsCopied() {
        launchOnboarded()
        openPlus()
        waitFor("KI-Prompt kopieren")
        // The full prompt is visible before copying.
        compose.onNodeWithTag("prompt:KI-Prompt kopieren").performScrollTo().assertExists()
        compose.onNodeWithTag("prompt-copy:KI-Prompt kopieren").performScrollTo().performClick()
        assertTrue(share.clipboard!!.contains("```json"))
        assertTrue(share.clipboard!!.contains("Bench Press"))
    }
}
