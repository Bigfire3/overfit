package app.overfit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

/** Import from the clipboard and from an opened link (inbox), samples, AI prompt, Duo teaser. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ShareFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
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

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun importFromClipboardShowsPreviewAndSaves() {
        launchOnboarded()
        share.clipboard = "Hier dein Plan:\n```json\n${AiPrompt.EXAMPLE}\n```\nViel Erfolg!"
        compose.onNodeWithText("Übungen").performClick()
        compose.onNodeWithText("Vorlagen").performClick()
        waitFor("Vorlage importieren")
        compose.onNodeWithText("Vorlage importieren").performScrollTo().performClick()
        compose.onNodeWithText("Aus Zwischenablage importieren").performClick()
        waitFor("Oberkörper A")
        compose.onNodeWithText("Vorschau").assertIsDisplayed()
        compose.onNodeWithText("Bench Press").assertIsDisplayed()
        compose.onNodeWithText("3 Übungen").assertIsDisplayed()
        compose.onNodeWithText("Übernehmen").performClick()
        // The new template opens in the editor.
        // The new template opens in the editor (its save button is pinned in the footer).
        waitFor("Speichern")
        compose.onNodeWithText("Oberkörper A").assertIsDisplayed()
    }

    @Test
    fun emptyClipboardAndGarbageShowErrors() {
        launchOnboarded()
        compose.onNodeWithText("Übungen").performClick()
        compose.onNodeWithText("Vorlagen").performClick()
        waitFor("Vorlage importieren")
        compose.onNodeWithText("Vorlage importieren").performScrollTo().performClick()
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
    fun samplesAddedFromStartAndProfileIdempotently() {
        launchOnboarded()
        waitFor("Beispielvorlagen hinzufügen")
        compose.onNodeWithText("Beispielvorlagen hinzufügen").performScrollTo().performClick()
        // The row disappears after the first template is saved; all three must still arrive.
        waitFor("Legs")
        waitFor("Pull")
        waitFor("Push")
        compose.onNodeWithText("Profil").performClick()
        compose.onNodeWithText("Beispielvorlagen hinzufügen").performScrollTo().performClick()
        waitFor("schon da")
        assertEquals(3, runBlocking { data.templates.templates.first() }.size)
    }

    @Test
    fun aiPromptIsCopiedAndDuoTeaserOpens() {
        launchOnboarded()
        compose.onNodeWithText("Duo · bald").performScrollTo().performClick()
        waitFor("Zwei Handys trainieren gemeinsam")
        compose.onNodeWithText("Duo ist noch nicht verfügbar", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Übungen").performClick()
        compose.onNodeWithText("Vorlagen").performClick()
        waitFor("KI-Vorlage kopieren")
        compose.onNodeWithText("KI-Vorlage kopieren").performScrollTo().performClick()
        assertTrue(share.clipboard!!.contains("```json"))
        assertTrue(share.clipboard!!.contains("Bench Press"))
    }
}
