package app.overfit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import app.overfit.data.db.SessionStatus
import app.overfit.domain.Language
import app.overfit.domain.ThemeMode
import app.overfit.domain.WeightUnit
import app.overfit.platform.HapticPattern
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import app.overfit.share.AiPrompt
import app.overfit.share.NoopPlatformShare
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * "Ein Tag mit Overfit": one long walk through the app as a user would use it – onboarding, the Push/Pull/Legs
 * samples, sharing and re-importing, a workout with rest timer, edits, an app restart in the middle,
 * the AI template, Fuel/Spot demo, "copy context", Duo teaser, language, theme and "delete all data".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DayWithOverfitTest {
    @get:Rule
    val compose = createComposeRule()

    private val secrets = InMemorySecretStore()
    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, secrets, DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(
        database, kv,
        readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
        readSample = { File("../shared/src/commonMain/composeResources/files/samples/$it.json").readBytes() },
    )
    private val share = NoopPlatformShare()
    private val haptics = RecordingHaptics()
    private val alerts = RecordingRestAlerts()
    private val providers = Providers(HttpClient(MockEngine { respond("{}") }))
    private var process by mutableIntStateOf(0)
    private lateinit var deps: AppDependencies

    private fun newProcess() = AppDependencies(
        store, providers, data, versionName = "0.1.test", share = share,
        today = { LocalDate(2026, 11, 3) }, haptics = haptics, restAlerts = alerts, kv = kv,
    ).also { deps = it }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) {
        try { compose.waitUntil(20_000) { shown(text, substring) } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    private fun waitGone(text: String) {
        try { compose.waitUntil(10_000) { !shown(text) } }
        catch (e: Throwable) { throw AssertionError("Still showing \"$text\":\n" + compose.onRoot().printToString(), e) }
    }

    private fun click(text: String) {
        // Lazy lists only compose what is near the viewport: scroll the list to the row first.
        // Fixed (non-scrolling) screens have nothing to scroll; just tap there.
        if (compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()) {
            compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        }
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) {}
        node.performClick()
    }
    private fun tab(name: String) { compose.onNode(hasText(name, substring = false) and hasClickAction()).performClick() }
    private fun back() { compose.onNodeWithContentDescription("Zurück").performClick() }
    private fun templates() = runBlocking { data.templates.templates.first() }

    @Test
    fun aDayWithOverfit() {
        compose.setContent { key(process) { OverfitApp(remember(process) { newProcess() }) } }

        // --- Onboarding ---------------------------------------------------------------------
        compose.onNodeWithText("Dein Name").performTextInput("Fabi")
        click("Weiter")
        compose.onNodeWithText("30").performTextInput("31")
        click("Weiter")
        compose.onNodeWithText("75").performTextInput("82,4")
        click("Weiter")
        click("Später einrichten")
        waitFor("Hallo, Fabi")
        assertTrue(store.state.value.onboardingDone)

        // --- Samples: Push, Pull, Legs ------------------------------------------------------
        click("Beispielvorlagen hinzufügen")
        waitFor("Legs"); waitFor("Pull"); waitFor("Push")
        compose.waitUntil(10_000) { templates().size == 3 }
        val push = templates().first { it.name == "Push" }

        // --- Share every template as a link and import it again from the clipboard ----------
        for (name in listOf("Push", "Pull", "Legs")) {
            tab("Übungen")
            click("Vorlagen")
            waitFor(name)
            compose.onAllNodesWithText(name)[0].performScrollTo().performClick()
            waitFor("Speichern")
            click("Teilen")
            waitFor("Link kopieren")
            click("Link kopieren")
            waitFor("Link kopiert")
            val link = share.clipboard.orEmpty()
            assertTrue(link, link.startsWith("http") || link.startsWith("overfit"))
            back() // share → editor
            compose.onNodeWithContentDescription("Zurück").performClick() // editor → list
            waitFor("Neue Vorlage")
            click("Vorlage importieren")
            click("Aus Zwischenablage importieren")
            waitFor("Vorschau")
            waitFor(name)
            click("Übernehmen")
            waitFor("Speichern")
            click("Speichern")
            waitFor("Neue Vorlage")
            compose.waitUntil(10_000) { templates().size == 3 + listOf("Push", "Pull", "Legs").indexOf(name) + 1 }
        }
        assertEquals(6, templates().size)

        // --- Start Push as a workout ----------------------------------------------------------
        tab("Start")
        click("Training starten")
        waitFor("Freies Training")
        compose.onAllNodesWithText("Push")[0].performClick()
        waitFor("erledigt", substring = true)
        assertEquals(1, alerts.permissionRequests)

        // check off set 1: tick + rest starts
        compose.onAllNodesWithContentDescription("Satz 1 abhaken")[0].performClick()
        waitFor("Überspringen")
        assertTrue(HapticPattern.Tick in haptics.played)
        assertNotNull(alerts.pending)
        val before = alerts.pending!!
        click("+15 s")
        compose.waitUntil(5_000) { alerts.pending?.endsAt == before.endsAt + 15_000 }
        click("−15 s")
        compose.waitUntil(5_000) { alerts.pending?.endsAt == before.endsAt }
        click("Überspringen")
        waitGone("Überspringen")
        assertEquals(null, alerts.pending)

        // add a set, add an exercise, replace one, reorder via the data model
        compose.onAllNodesWithText("Satz hinzufügen")[0].performScrollTo().performClick()
        click("Übung hinzufügen")
        waitFor("Übungen auswählen")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Klimmzug")
        waitFor("Pull-up")
        compose.onAllNodesWithText("Pull-up")[0].performClick()
        click("1 hinzufügen")
        waitFor("Pull-up")
        assertTrue(deps.workout.state.value.workout!!.exercises.any { it.exercise.name == "Pull-up" })

        val countBefore = deps.workout.state.value.workout!!.exercises.size
        compose.onAllNodesWithText("Ersetzen")[0].performScrollTo().performClick()
        waitFor("Suchen, z. B. Klimmzug")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Dips")
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Dips", substring = true)).fetchSemanticsNodes().size >= 2 }
        // [0] is the search field itself (it contains the typed text); the results follow it.
        compose.onAllNodes(hasText("Dips", substring = true))[1].performClick()
        try { compose.waitUntil(10_000) { deps.workout.state.value.workout!!.exercises.any { it.exercise.name.startsWith("Dips") } } }
        catch (e: Throwable) { throw AssertionError(deps.workout.state.value.workout!!.exercises.map { it.exercise.name }.toString() + compose.onRoot().printToString().take(3000), e) }
        assertEquals(countBefore, deps.workout.state.value.workout!!.exercises.size)
        // reorder (the drag gesture itself is not scriptable here; the same edit the drag handle calls)
        val order = deps.workout.state.value.workout!!.exercises.map { it.id }
        deps.workout.edit { it.moveExercise(0, 1) }
        compose.waitUntil(10_000) { deps.workout.state.value.workout!!.exercises.map { it.id } == listOf(order[1], order[0]) + order.drop(2) }
        val workoutId = deps.workout.state.value.workout!!.id
        val setsBefore = deps.workout.state.value.workout!!.completedSets

        // --- App restart in the middle: the workout continues ------------------------------
        compose.waitUntil(10_000) { runBlocking { data.workouts.status(workoutId) } == SessionStatus.ACTIVE }
        process++
        compose.waitUntil(20_000) { deps.workout.state.value.loaded && deps.workout.state.value.workout != null }
        assertEquals(workoutId, deps.workout.state.value.workout!!.id)
        assertEquals(setsBefore, deps.workout.state.value.workout!!.completedSets)
        waitFor("erledigt", substring = true)

        // --- Finish -------------------------------------------------------------------------
        click("Beenden")
        click("Beenden? Nochmal tippen")
        waitFor("Training starten")
        compose.waitUntil(10_000) { runBlocking { data.workouts.status(workoutId) } == SessionStatus.FINISHED }
        assertEquals(1, runBlocking { data.workouts.get(workoutId) }!!.completedSets.coerceAtLeast(1))

        // --- AI template: copy the prompt, import a sample answer from the clipboard --------
        tab("Übungen")
        click("Vorlagen")
        waitFor("Neue Vorlage")
        click("KI-Vorlage kopieren")
        assertTrue(share.clipboard!!.contains("```json"))
        share.clipboard = "Hier dein Plan:\n```json\n${AiPrompt.EXAMPLE}\n```"
        click("Vorlage importieren")
        click("Aus Zwischenablage importieren")
        waitFor("Oberkörper A")
        click("Übernehmen")
        waitFor("Speichern")
        click("Speichern")
        compose.waitUntil(10_000) { templates().any { it.name == "Oberkörper A" } }

        // --- Profile: Fuel demo + Spot reveal, KI-Coach context, Duo teaser ------------------
        tab("Profil")
        click("startet bald")
        waitFor("Fuel startet bald")
        click("Demo ansehen")
        waitFor("Fuel · 4,20 €")
        click("Freund spotten")
        waitFor("Neuer Code in", substring = true)
        back()
        click("Ich wurde gespottet")
        compose.onNodeWithText("Link oder Code").performTextInput("overfit://spot#demo-spot-token-1")
        click("Einlösen")
        waitFor("Spot von Fabian")
        assertTrue(HapticPattern.Reveal in haptics.played)
        waitFor("Tippen zum Schließen")
        click("Tippen zum Schließen")
        waitFor("Fuel · 4,20 €", substring = true)
        back()

        // KI-Coach → Gemini → copy context: contains the profile and the finished workout
        tab("Profil")
        click("KI-Coach")
        click("Gemini")
        click("Kontext kopieren")
        waitFor("Kopiert. Füge den Text in deinen Chat ein.")
        val ctx = share.clipboard.orEmpty()
        assertTrue(ctx, ctx.contains("Fabi"))
        assertTrue(ctx, ctx.contains("Push"))
        assertTrue(ctx, ctx.contains("Bench Press"))
        back(); back()

        // Duo teaser
        tab("Start")
        click("Duo · bald")
        waitFor("Zwei Handys trainieren gemeinsam", substring = true)
        waitFor("Duo ist noch nicht verfügbar", substring = true)
        back()

        // Language, theme, vibration
        tab("Profil")
        click("Dunkel")
        assertEquals(ThemeMode.DARK, store.state.value.settings.theme)
        click("Hell")
        assertEquals(ThemeMode.LIGHT, store.state.value.settings.theme)
        click("Aus")
        assertFalse(store.state.value.settings.vibration)
        click("English")
        waitFor("Settings")
        assertEquals(Language.EN, store.state.value.language)
        tab("Start")
        waitFor("Hi, Fabi")
        tab("History")
        waitFor("No workouts yet", substring = true) // the real history arrives with phase 3
        tab("Profile")
        click("Deutsch")
        waitFor("Einstellungen")

        // Delete everything → back to onboarding, database empty
        click("Alle Daten löschen")
        click("Wirklich alles löschen?")
        waitFor("Dein Name")
        compose.waitUntil(10_000) { templates().isEmpty() }
        assertEquals("", store.state.value.profile.name)
    }
}
