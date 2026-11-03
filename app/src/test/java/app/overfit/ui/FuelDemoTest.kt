package app.overfit.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
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
import app.overfit.platform.HapticPattern
import app.overfit.platform.RecordingHaptics
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Profile → Fuel → "Demo ansehen" → balance, invite (QR), redeem → reveal. No server, no real data. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class FuelDemoTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG)).also { it.completeOnboarding() }
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { ByteArray(0) })
    private val haptics = RecordingHaptics()

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun demoWalkthrough() {
        compose.setContent {
            OverfitApp(
                AppDependencies(
                    store, Providers(HttpClient(MockEngine { respond("{}") })), data,
                    versionName = "0.1.test", today = { LocalDate(2026, 11, 2) }, haptics = haptics,
                ),
            )
        }
        compose.onNodeWithText("Profil").performClick()
        compose.onNodeWithText("startet bald").performScrollTo().performClick()

        // Not configured: explanation + demo
        compose.onNodeWithText("Guthaben startet bald").assertExists()
        compose.onNodeWithText("Demo ansehen").performClick()

        // Demo balance and packages
        try { compose.waitUntil(5_000) { shown("4,20 € Guthaben") } } catch (e: Throwable) { throw AssertionError(compose.onRoot().printToString(), e) }
        compose.onNodeWithText("Demo – lokale Beispieldaten, nichts davon ist echt.").assertExists()
        // Credit is "X € Guthaben"; gifted reviews are their own counter; bought vs. gifted, gifted first.
        compose.onNodeWithText("5 geschenkte Auswertungen").assertExists()
        compose.onNodeWithText("Gekauft: 3,20 € Guthaben · geschenkt: 1,00 € Guthaben").assertExists()
        compose.onNodeWithText("Geschenktes wird zuerst verbraucht.").assertExists()
        compose.onNodeWithText("Geschenkte Auswertungen gelten für Coach-Auswertungen mit Gemini 3.6 Flash", substring = true).assertExists()
        // Store packages show both values: credit and the higher store price.
        compose.onNodeWithText("5,00 € Guthaben · 6,99 € (inkl. Store-Gebühr)").performScrollTo().assertExists()
        compose.onNodeWithText("28 % Aufschlag", substring = true).assertExists()
        assertTrue(shown("Bald im Store"))

        // Ledger
        compose.onNodeWithText("Buchungen").performScrollTo().performClick()
        compose.waitUntil(5_000) { shown("Coach-Auswertung") }
        // Real model names instead of tier names; a replacement model is said openly.
        assertTrue(shown("Gemini 3.6 Flash (Google)", substring = true))
        compose.onNodeWithText("Ersatzmodell, berechnet wurde der Preis von Gemini 3.6 Flash").performScrollTo().assertExists()
        assertTrue(!shown("Standard", substring = true) && !shown("Lite", substring = true))
        // Every booking says whether it was paid, gifted or used; gifted reviews show as a count.
        compose.onNodeWithText("+5 Auswertungen").performScrollTo().assertExists()
        assertTrue(shown("bezahlt ·", substring = true))
        assertTrue(shown("geschenkt ·", substring = true))
        assertTrue(shown("verbraucht ·", substring = true))
        assertTrue(shown("+3,20 € Guthaben"))
        compose.onNodeWithContentDescription("Zurück").performClick()

        // invite screen shows a rotating code
        compose.onNodeWithText("Freunde einladen").performScrollTo().performClick()
        compose.waitUntil(5_000) { shown("Neuer Code in", substring = true) }
        assertTrue(shown("Ihr bekommt beide 5 Coach-Auswertungen geschenkt", substring = true))
        compose.onNodeWithContentDescription("Zurück").performClick()

        // Redeem (paste) → reveal
        compose.onNodeWithText("Einladung einlösen").performScrollTo().performClick()
        compose.onNodeWithText("Link oder Code").performTextInput("overfit://invite#demo-invite-token-1")
        compose.onNodeWithText("Einlösen").performClick()
        compose.waitUntil(10_000) { shown("Einladung von Fabian") }
        assertTrue(HapticPattern.Reveal in haptics.played)
    }
}
