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

/** Profile → Fuel → "Demo ansehen" → balance, packages, ledger. No server, no real data. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class FuelDemoTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
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
        // "startet bald" is also the state of the AI chat connections in the coach settings: open the credit row by its label.
        compose.onNodeWithText("Guthaben", useUnmergedTree = true).performScrollTo().performClick()

        // Not configured: explanation + demo
        compose.onNodeWithText("Guthaben startet bald").assertExists()
        compose.onNodeWithText("Demo ansehen").performClick()

        // Demo balance and packages
        try { compose.waitUntil(5_000) { shown("4,20 € Guthaben") } } catch (e: Throwable) { throw AssertionError(compose.onRoot().printToString(), e) }
        compose.onNodeWithText("Demo – lokale Beispieldaten, nichts davon ist echt.").assertExists()
        // Credit is "X € Guthaben"; nothing gifted, nothing expires, no invitations.
        assertTrue(!shown("geschenkt", substring = true) && !shown("gültig bis", substring = true))
        // Store packages show both values: credit and the higher store price.
        compose.onNodeWithText("5,00 € Guthaben · 6,99 € (inkl. Store-Gebühr)").performScrollTo().assertExists()
        compose.onNodeWithText("28 % Aufschlag", substring = true).assertExists()
        assertTrue(shown("Bald im Store"))
        assertTrue(!shown("Einladung", substring = true) && !shown("einladen", substring = true))

        // Ledger
        compose.onNodeWithText("Buchungen").performScrollTo().performClick()
        compose.waitUntil(5_000) { shown("Coach-Auswertung") }
        // Real model names instead of tier names: all credit tiers are Claude.
        assertTrue(shown("Claude Sonnet 5.5 (Anthropic)", substring = true))
        assertTrue(shown("Claude Opus 5.5 (Anthropic)", substring = true))
        assertTrue(shown("Claude Haiku 5.5 (Anthropic)", substring = true))
        assertTrue(!shown("Gemini", substring = true))
        // A replacement model is said openly, with the chosen Claude model (not an old Gemini name).
        compose.onNodeWithText("Ersatzmodell, berechnet wurde der Preis von Claude Opus 5.5").performScrollTo().assertExists()
        assertTrue(!shown("Standard", substring = true) && !shown("Lite", substring = true))
        // Every booking says whether it was paid or used.
        assertTrue(shown("bezahlt ·", substring = true))
        assertTrue(shown("verbraucht ·", substring = true))
        assertTrue(shown("+4,22 € Guthaben"))
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntil(5_000) { shown("Wiederherstellungscode") }
    }
}
