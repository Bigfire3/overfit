package app.overfit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.ai.Providers
import app.overfit.ai.ProviderId
import app.overfit.coach.CoachErrorKind
import app.overfit.coach.CoachException
import app.overfit.coach.CoachReply
import app.overfit.coach.FakeCoachTransport
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.DeviceDefaults
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.data.InMemorySecretStore
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.SessionStatus
import app.overfit.domain.Language
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
 * the AI template, credit demo, "copy context", language, theme and "delete all data".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DayWithOverfitTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val secrets = InMemorySecretStore()
    private val kv = InMemoryKeyValueStore().apply { put(app.overfit.data.AppStore.KEY_COACH_STORY_SEEN, "1") }
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
    // Coach: offline first (the request waits in the queue), online after the "app restart".
    private var online = false
    private val coachAnswer = """{"rating":8,"summary":"Starkes Training","positives":["Sauber"],"improvements":[],"tips":["Mehr schlafen"],"photo_feedback":null,"next_session":{"exercises":[{"exercise_id":"wg:bench-press","is_new":false,"rest_sec":90,"sets":[{"weight_kg":62.5,"reps":8,"duration_sec":null},{"weight_kg":62.5,"reps":8,"duration_sec":null}],"reason":"Alle Sätze geschafft"}],"note":null},"memory":"Ziel: Kraft."}"""
    // The coach chat answers with a workout proposal in the share format v1 (as a JSON string, see CoachChatSchema).
    private val chatAnswer = kotlinx.serialization.json.buildJsonObject {
        put("reply", kotlinx.serialization.json.JsonPrimitive("Hier ist ein Vorschlag für morgen."))
        put("workout", kotlinx.serialization.json.JsonPrimitive(AiPrompt.EXAMPLE.replace("Oberkörper A", "Coach Push")))
    }.toString()
    private val coachTransport = FakeCoachTransport { prompt, _ ->
        when {
            !online -> throw CoachException(CoachErrorKind.NETWORK, "offline")
            prompt.schemaName == app.overfit.coach.CoachChatSchema.NAME -> CoachReply(chatAnswer, chatAnswer)
            else -> CoachReply(coachAnswer, coachAnswer)
        }
    }
    private var process by mutableIntStateOf(0)
    private lateinit var deps: AppDependencies

    private fun newProcess() = AppDependencies(
        store, providers, data, versionName = "0.1.test", share = share,
        today = { realToday() }, haptics = haptics, restAlerts = alerts, kv = kv,
        coachTransport = { coachTransport },
    ).also { deps = it }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()

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
    private fun tab(name: String) {
        // Tabs by role: the Training tab also has a "Coach" row.
        val tabNode = hasText(name, substring = false) and hasClickAction() and
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
        // Sub-screens hide the tab bar: step back until it is there (screens settle asynchronously).
        repeat(4) {
            if (compose.onAllNodes(tabNode).fetchSemanticsNodes().isNotEmpty()) return@repeat
            // The back button can vanish between the check and the click while a screen settles: then just retry.
            if (compose.onAllNodes(hasContentDescription("Zurück")).fetchSemanticsNodes().isNotEmpty()) {
                try { back() } catch (_: AssertionError) {}
            }
            compose.waitForIdle()
        }
        compose.waitUntil(10_000) { compose.onAllNodes(tabNode).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabNode).performClick()
    }
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
        compose.waitForTrainingTab() // Training tab
        assertTrue(store.state.value.onboardingDone)

        // --- Samples: Push, Pull, Legs are there from the first start on --------------------
        waitFor("Legs"); waitFor("Pull"); waitFor("Push")
        compose.waitUntilState(10_000) { templates().size == 3 }
        val push = templates().first { it.name == "Push" }

        // --- Share every template as a link and import it again from the clipboard ----------
        for (name in listOf("Push", "Pull", "Legs")) {
            tab("Workouts")
            waitFor(name)
            compose.onAllNodes(hasText(name))[0].performScrollTo().performClick()
            waitFor("Starten") // the workout plan screen
            // Share is an icon top right (content description, no text).
            compose.waitUntil(20_000) { compose.onAllNodes(hasContentDescription("Teilen")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Teilen").performClick()
            waitFor("Das wird geteilt") // preview of what the recipient gets, above the compact QR code
            waitFor("Link kopieren")
            click("Link kopieren")
            waitFor("Link kopiert")
            val link = share.clipboard.orEmpty()
            assertTrue(link, link.startsWith("http") || link.startsWith("overfit"))
            back() // share → plan
            back() // plan → Training tab
            compose.waitForTrainingTab()
            compose.onNodeWithContentDescription("Neues Workout").performClick()
            click("Workout importieren")
            click("Aus Zwischenablage importieren")
            waitFor("Vorschau")
            waitFor(name)
            click("Übernehmen")
            waitFor("Starten") // the imported workout opens in its plan screen
            compose.waitUntilState(10_000) { templates().size == 3 + listOf("Push", "Pull", "Legs").indexOf(name) + 1 }
        }
        assertEquals(6, templates().size)

        // --- Start Push as a workout ----------------------------------------------------------
        tab("Workouts")
        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performScrollTo().performClick()
        waitFor("Starten")
        click("Starten")
        waitFor("erledigt", substring = true)
        assertEquals(1, alerts.permissionRequests)

        // check off set 1: tick + rest starts
        compose.onAllNodesWithContentDescription("Satz 1 abhaken")[0].performClick()
        waitFor("Überspringen")
        assertTrue(HapticPattern.Tick in haptics.played)
        assertNotNull(alerts.pending)
        val before = alerts.pending!!
        click("+15 s")
        compose.waitUntilState(5_000) { alerts.pending?.endsAt == before.endsAt + 15_000 }
        click("−15 s")
        compose.waitUntilState(5_000) { alerts.pending?.endsAt == before.endsAt }
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
        // "Replace" sits in the ⋮ menu of the exercise block.
        compose.onAllNodes(hasContentDescription("Weitere Aktionen für", substring = true))[0].performScrollTo().performClick()
        waitFor("Ersetzen")
        compose.onNodeWithText("Ersetzen").performClick()
        waitFor("Suchen, z. B. Klimmzug")
        compose.onNodeWithText("Suchen, z. B. Klimmzug").performTextInput("Dip")
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Dip")).fetchSemanticsNodes().size >= 2 }
        // [0] is the search field itself (it contains the typed text); the exact result "Dip" follows it.
        compose.onAllNodes(hasText("Dip"))[1].performClick()
        try { compose.waitUntilState(10_000) { deps.workout.state.value.workout!!.exercises.any { it.exercise.name == "Dip" } } }
        catch (e: Throwable) { throw AssertionError(deps.workout.state.value.workout!!.exercises.map { it.exercise.name }.toString() + compose.onRoot().printToString().take(3000), e) }
        assertEquals(countBefore, deps.workout.state.value.workout!!.exercises.size)
        // reorder (the drag gesture itself is not scriptable here; the same edit the drag handle calls)
        val order = deps.workout.state.value.workout!!.exercises.map { it.id }
        deps.workout.edit { it.moveExercise(0, 1) }
        compose.waitUntilState(10_000) { deps.workout.state.value.workout!!.exercises.map { it.id } == listOf(order[1], order[0]) + order.drop(2) }
        val workoutId = deps.workout.state.value.workout!!.id
        val setsBefore = deps.workout.state.value.workout!!.completedSets

        // --- Minimize: browse the app while the workout keeps running, reopen from the bar -------
        compose.onNodeWithContentDescription("Minimieren").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Training öffnen", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertNotNull(deps.workout.state.value.workout)
        tab("Kalender")
        tab("Profil")
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Training öffnen", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        tab("Workouts")
        compose.onNode(hasContentDescription("Training öffnen", substring = true)).performClick()
        waitFor("Nächsten Satz loggen")
        assertEquals(workoutId, deps.workout.state.value.workout!!.id)

        // --- App restart in the middle: the workout continues ------------------------------
        compose.waitUntilState(10_000) { runBlocking { data.workouts.status(workoutId) } == SessionStatus.ACTIVE }
        process++
        compose.waitUntilState(20_000) { deps.workout.state.value.loaded && deps.workout.state.value.workout != null }
        assertEquals(workoutId, deps.workout.state.value.workout!!.id)
        assertEquals(setsBefore, deps.workout.state.value.workout!!.completedSets)
        waitFor("erledigt", substring = true)

        store.connectAi(ProviderId.ANTHROPIC, "sk-test") // the coach needs access; "Später einrichten" skipped it

        // --- Finish: summary, feeling, note, photo (fake: no camera in Robolectric), save ----
        click("Beenden")
        compose.onNodeWithTag("confirm:armed").performClick()
        waitFor("Training beendet")
        // Wait via the UI first (the name field appears once the finished workout is stored): the saved
        // workout is updated in a DB transaction at the same time, which a blocking query polled on the
        // test's main thread would stall.
        compose.waitUntil(20_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        assertEquals(SessionStatus.FINISHED, runBlocking { data.workouts.status(workoutId) })
        compose.onNodeWithContentDescription("Gefühl 4 von 5").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Gefühl 4 von 5").assertIsSelected()
        // The name field is the first text field, the note field the second.
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Stark heute")
        runBlocking { data.workouts.addPhoto(workoutId, byteArrayOf(1, 2, 3, 4)) }
        click("Speichern")
        compose.waitForTrainingTab() // back on the Training tab
        compose.waitUntilState(10_000) { runBlocking { data.workouts.getFinished(workoutId)?.note } == "Stark heute" }
        val finished = runBlocking { data.workouts.getFinished(workoutId) }!!
        assertEquals(4, finished.feeling)
        assertEquals(1, finished.photos.size)

        // --- Calendar → day → training detail with note; share the workout and import it as a template -
        compose.openCalendarDay(realToday().dayOfMonth)
        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        waitFor("Stark heute")
        compose.onNodeWithContentDescription("Teilen").performClick()
        waitFor("Link kopieren")
        click("Link kopieren")
        val workoutLink = share.clipboard.orEmpty()
        assertTrue(workoutLink, workoutLink.isNotBlank())
        back(); back()
        // The calendar day shows the photo
        tab("Kalender")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("Tag ${realToday().dayOfMonth}, 1 Foto")).fetchSemanticsNodes().isNotEmpty()
        }

        // --- Coach: the review request waits offline, goes out on the next app start ---------
        compose.openCalendarDay(realToday().dayOfMonth)
        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        waitFor("Coach-Auswertung")
        click("Coach-Auswertung")
        waitFor("Wartet auf Verbindung")
        online = true
        process++ // app restart → the queue is processed
        compose.waitForTrainingTab()
        compose.openCalendarDay(realToday().dayOfMonth)
        waitFor("Push")
        compose.onAllNodes(hasText("Push"))[0].performClick()
        waitFor("Coach-Auswertung")
        click("Coach-Auswertung")
        waitFor("8/10")
        waitFor("Mehr schlafen")
        waitFor("Alle annehmen")
        click("Alle annehmen")
        waitFor("Plan übernommen. Das Workout ist angepasst.")
        compose.waitUntilState(10_000) {
            templates().any { t -> runBlocking { data.templates.get(t.id) }?.exercises?.any { e -> e.sets.firstOrNull()?.weightKg == 62.5 } == true }
        }
        back(); back()

        // --- AI template: copy the prompt, import a sample answer from the clipboard --------
        tab("Workouts")
        compose.waitForTrainingTab()
        compose.onNodeWithContentDescription("Neues Workout").performClick()
        compose.onNodeWithTag("prompt-copy:KI-Prompt kopieren").performScrollTo().performClick()
        assertTrue(share.clipboard!!.contains("```json"))
        share.clipboard = "Hier dein Plan:\n```json\n${AiPrompt.EXAMPLE}\n```"
        click("Workout importieren")
        click("Aus Zwischenablage importieren")
        waitFor("Oberkörper A")
        click("Übernehmen")
        waitFor("Starten")
        compose.waitUntilState(10_000) { templates().any { it.name == "Oberkörper A" } }

        // --- Profile: Fuel demo (balance, ledger), KI-Coach context ------------------
        tab("Profil")
        click("startet bald")
        waitFor("Guthaben startet bald")
        click("Demo ansehen")
        waitFor("4,20 € Guthaben")
        assertTrue(compose.onAllNodes(hasText("einlad", substring = true, ignoreCase = true)).fetchSemanticsNodes().isEmpty())
        click("Buchungen")
        waitFor("Claude Sonnet 5.5 (Anthropic)", substring = true)
        back()
        waitFor("4,20 € Guthaben")

        // Coach tab → prompts show their text → copy context: contains the profile and the finished workout
        tab("Coach")
        waitFor("Prompts & Vorlagen", substring = true)
        compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("prompt:Kontext kopieren")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("prompt:Kontext kopieren").performScrollTo().assertExists()
        compose.onNodeWithTag("prompt-copy:Kontext kopieren").performScrollTo().performClick()
        val ctx = share.clipboard.orEmpty()
        assertTrue(ctx, ctx.contains("Fabi"))
        assertTrue(ctx, ctx.contains("Push"))
        assertTrue(ctx, ctx.contains("Bench Press"))

        // Coach chat: ask, the answer proposes a workout, "Use workout" opens the import preview
        compose.onNode(hasSetTextAction() and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("coach:tabInput"))).performScrollTo().performTextInput("Was soll ich morgen trainieren?")
        click("Senden")
        waitFor("Hier ist ein Vorschlag für morgen.")
        waitFor("Workout-Vorschlag: Coach Push")
        val chatPrompt = coachTransport.prompts.last()
        assertTrue(chatPrompt.dynamicSystem, chatPrompt.dynamicSystem.contains("Fabi"))
        assertTrue(chatPrompt.dynamicSystem, chatPrompt.dynamicSystem.contains("Bench Press"))
        click("Workout übernehmen")
        waitFor("Coach Push")
        click("Übernehmen")
        compose.waitUntilState(10_000) { templates().any { it.name == "Coach Push" } }
        tab("Coach")

        tab("Workouts")

        // Language, theme, notifications (permission denied: "Allow" asks the platform)
        alerts.allowed = false
        tab("Profil")
        waitFor("Benachrichtigungen erlauben")
        assertTrue(shown("Ohne diese Erlaubnis meldet sich der Pausentimer", substring = true))
        compose.onNodeWithText("Benachrichtigungen erlauben").performScrollTo().performClick()
        assertEquals(1, alerts.allowRequests)
        // No theme choice any more (always dark).
        assertFalse(shown("Darstellung"))
        // Two plain switches (no "An/Aus" text): vibration first, then the countdown ticks.
        assertFalse(shown("Aus"))
        compose.onNode(hasText("Vibration") and isToggleable()).performScrollTo().assertIsOn().performClick()
        assertFalse(store.state.value.settings.vibration)
        assertTrue(store.state.value.settings.restTicks)
        compose.onNode(hasText("Ticken vor Pausenende") and isToggleable()).performScrollTo().performClick()
        compose.waitUntilState(10_000) { !store.state.value.settings.restTicks }
        assertFalse(store.state.value.settings.vibration)
        click("English")
        waitFor("ABOUT YOU")
        assertEquals(Language.EN, store.state.value.language)
        tab("Workouts")
        compose.waitForTrainingTab("New workout")
        tab("Calendar")
        waitFor("1 workout")
        tab("Profile")
        click("Deutsch")
        waitFor("ÜBER DICH")

        // Backup round trip: export → delete everything (UI) → restore = same data
        val beforeTemplates = templates().map { it.name }
        val backupBytes = runBlocking { deps.backup.export() }
        click("Alle Daten löschen")
        compose.onNodeWithTag("confirm:armed").performClick()
        waitFor("Dein Name")
        // Fresh start: only the Push/Pull/Legs examples are left (no restart needed).
        compose.waitUntilState(10_000) { templates().map { it.name } == listOf("Push", "Pull", "Legs") }
        assertEquals("", store.state.value.profile.name)
        assertTrue(runBlocking { data.workouts.history.first() }.isEmpty())
        runBlocking { deps.backup.restore(deps.backup.preview(backupBytes).getOrThrow()) }
        assertEquals(beforeTemplates, templates().map { it.name })
        assertEquals("Fabi", store.state.value.profile.name)
        assertEquals(1, runBlocking { data.workouts.history.first() }.size)
        assertEquals(1, runBlocking { data.workouts.getFinished(workoutId) }!!.photos.size)
    }
}
