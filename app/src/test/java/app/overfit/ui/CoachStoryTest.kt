package app.overfit.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
import app.overfit.i18n.GermanCoachStory
import app.overfit.share.NoopPlatformShare
import app.overfit.ui.coach.CoachStoryScreen
import app.overfit.ui.coach.StoryTarget
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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

/** Coach story: opens once by itself, then via the title; tap right/left, swipe down and X; shortcut buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CoachStoryTest {
    @get:Rule
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG)).also {
        it.setName("Fabi"); it.completeOnboarding()
    }
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(database, kv, readCatalog = { ByteArray(0) })
    private val s = GermanCoachStory

    @After
    fun tearDown() = database.close()

    private fun launch() {
        val deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "t",
            share = NoopPlatformShare(), today = { LocalDate(2026, 11, 3) }, coachStoryAutoAdvance = false,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()
    }

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun await(text: String) = compose.waitUntil(10_000) { shown(text) }
    private fun storyShown() = compose.onAllNodesWithTag("story").fetchSemanticsNodes().isNotEmpty()
    private fun awaitStoryGone() = compose.waitUntil(10_000) { !storyShown() }

    @Test
    fun opensByItselfOnTheFirstVisitOnly() {
        launch()
        assertFalse(store.coachStorySeen())
        compose.onNodeWithTag("tab:COACH").performClick()
        await(s.whatItDoes.title)
        assertTrue(store.coachStorySeen())

        compose.onNodeWithTag("story:close").performClick()
        awaitStoryGone()
        await("Prompts & Vorlagen".uppercase())

        // Away and back: no story by itself anymore.
        compose.onNodeWithTag("tab:HISTORY").performClick()
        compose.onNodeWithTag("tab:COACH").performClick()
        await("Prompts & Vorlagen".uppercase())
        assertFalse(storyShown())
    }

    @Test
    fun titleOpensTheStoryAndTapsMoveThroughFourPages() {
        kv.put(AppStore.KEY_COACH_STORY_SEEN, "1")
        launch()
        compose.onNodeWithTag("tab:COACH").performClick()
        await("Prompts & Vorlagen".uppercase())
        assertFalse(storyShown())

        compose.onNodeWithTag("coach:storyOpen").performClick()
        await(s.whatItDoes.title)
        // Page 1 has no shortcut; the right side goes forward through the ways, the left side back.
        assertTrue(compose.onAllNodesWithTag("story:action").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("story:next").performClick(); await(s.ownKey.title)
        compose.onNodeWithTag("story:next").performClick(); await(s.credit.title)
        compose.onNodeWithTag("story:next").performClick(); await(s.subscription.title)
        compose.onNodeWithTag("story:back").performClick(); await(s.credit.title)
        compose.onNodeWithTag("story:back").performClick(); await(s.ownKey.title)
        compose.onNodeWithTag("story:back").performClick(); await(s.whatItDoes.title)
        // Back on the first page stays there.
        compose.onNodeWithTag("story:back").performClick(); await(s.whatItDoes.title)

        // Past the last page closes it.
        repeat(3) { compose.onNodeWithTag("story:next").performClick() }
        await(s.subscription.title)
        compose.onNodeWithTag("story:next").performClick()
        awaitStoryGone()
        await("Prompts & Vorlagen".uppercase())
    }

    @Test
    fun swipeDownClosesAndShortcutLeadsToTheWay() {
        kv.put(AppStore.KEY_COACH_STORY_SEEN, "1")
        launch()
        compose.onNodeWithTag("tab:COACH").performClick()
        compose.onNodeWithTag("coach:storyOpen").performClick()
        await(s.whatItDoes.title)
        compose.onNodeWithTag("story").performTouchInput { swipeDown() }
        awaitStoryGone()

        compose.onNodeWithTag("coach:storyOpen").performClick()
        await(s.whatItDoes.title)
        compose.onNodeWithTag("story:next").performClick()
        await(s.ownKey.title)
        compose.onNodeWithTag("story:action").performClick()
        awaitStoryGone()
        await("API-Key einfügen")
    }

    @Test
    fun movesOnByItselfAndStopsAtTheLastPage() {
        compose.mainClock.autoAdvance = false
        val closed = mutableStateOf(false)
        compose.setContent {
            app.overfit.ui.theme.OverfitTheme(app.overfit.domain.ThemeMode.DARK) {
                androidx.compose.runtime.CompositionLocalProvider(
                    app.overfit.i18n.LocalCoachStrings provides app.overfit.i18n.GermanCoach,
                ) { CoachStoryScreen(onClose = { closed.value = true }, onTarget = {}, autoAdvance = true) }
            }
        }
        compose.mainClock.advanceTimeBy(app.overfit.ui.coach.STORY_AUTO_MS + 100)
        compose.onNodeWithTag("story:title").assertTextEquals(s.ownKey.title)
        compose.mainClock.advanceTimeBy(app.overfit.ui.coach.STORY_AUTO_MS * 5)
        compose.onNodeWithTag("story:title").assertTextEquals(s.subscription.title)
        assertEquals(false, closed.value)
    }
}
