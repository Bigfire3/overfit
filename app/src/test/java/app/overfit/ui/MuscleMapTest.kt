package app.overfit.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
import app.overfit.domain.Muscle
import app.overfit.domain.MuscleMap
import app.overfit.domain.SetValues
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.MuscleGerman
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import app.overfit.share.NoopPlatformShare
import app.overfit.ui.muscles.bodyMapPoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Fabian's wish "Training Frequency" in the real app: calendar title → body icon → sheet with the muscles of this
 * week (shaded by sets), period chips (remembered), a tap on a muscle shows its sets; the training detail shows the
 * muscles of that one training. With `SCREENSHOT_DIR` it also writes the training-detail screenshot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class MuscleMapTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ data }, { database })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private val store = AppStore(kv, InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
    private val database = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val data = AppData(
        database, kv,
        readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
        readSample = { File("../shared/src/commonMain/composeResources/files/samples/$it.json").readBytes() },
    )
    private val m = MuscleGerman

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false) {
        try { compose.waitUntil(20_000) { shown(text, substring) } }
        catch (e: Throwable) { throw AssertionError("Waiting for \"$text\"", e) }
    }

    private fun sheetOpen() = compose.onAllNodes(hasTestTag("muscle-sheet")).fetchSemanticsNodes().isNotEmpty()

    /** Taps the middle of [muscle]'s region on the front (or back) figure. */
    private fun tapMuscle(muscle: Muscle, back: Boolean = false) {
        val node = compose.onNodeWithTag("body-map", useUnmergedTree = true)
        val bounds = node.fetchSemanticsNode().size
        val point = bodyMapPoint(muscle, back, Size(bounds.width.toFloat(), bounds.height.toFloat()))!!
        node.performTouchInput { click(point) }
        compose.waitForIdle()
    }

    private fun line(): String = compose.onNodeWithTag("muscle-line", useUnmergedTree = true).fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun calendarSheetAndTrainingDetail() {
        store.setName("Fabi")
        store.completeOnboarding()
        val now = System.currentTimeMillis()
        // "Push" today (first exercise: two sets done), plus a calf workout more than a year ago.
        val pushLoads = runBlocking {
            data.seedCatalog().getOrThrow()
            data.importer.addSamples()
            val push = data.templates.templates.first().first { it.name == "Push" }
            var w = Workout.fromTemplate(data.templates.get(push.id)!!, now - 10 * 60_000, id = "push")
            val sets = w.exercises[0].sets
            w = w.setValues(sets[0].id, SetValues(60.0, 8)).completeSet(sets[0].id, w.startedAt + 60_000)
            w = w.setValues(sets[1].id, SetValues(60.0, 8)).completeSet(sets[1].id, w.startedAt + 120_000)
            data.workouts.finish(w, endedAt = now - 60_000)

            val calf = data.exercises.getActive().first { it.primaryMuscles == listOf(Muscle.CALVES) }
            val old = now - 400L * 24 * 3_600_000
            var o = Workout.empty("Waden", old, id = "old").addExercise(Workout.added(calf, emptyList()))
            o.exercises[0].sets.forEach { s -> o = o.setValues(s.id, SetValues(40.0, 12)).completeSet(s.id, old + 60_000) }
            data.workouts.finish(o, endedAt = old + 1_800_000)
            MuscleMap.ofWorkout(w)
        }
        val top = pushLoads.maxBy { it.value.score }
        assertTrue("Push trains no calves", Muscle.CALVES !in pushLoads)

        val deps = AppDependencies(
            store, Providers(HttpClient(MockEngine { respond("{}") })), data, versionName = "0.1.test", share = NoopPlatformShare(),
            today = { realToday() }, haptics = RecordingHaptics(), restAlerts = RecordingRestAlerts(), kv = kv,
        )
        compose.setContent { OverfitApp(deps) }
        compose.waitForTrainingTab()

        // 1. Calendar → body icon → sheet with "this week" selected.
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("tab:PHOTOS")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag("tab:PHOTOS")).performClick()
        waitFor("Kalender")
        compose.onNodeWithContentDescription(m.open).performClick()
        compose.waitUntilState { sheetOpen() }
        waitFor(m.title)
        compose.onNodeWithText(m.period(app.overfit.domain.MusclePeriod.WEEK)).assertIsSelected()
        waitFor(m.tapHint)

        // 2. Tap the most trained muscle: name and sets; again = deselect.
        tapMuscle(top.key, back = top.key in setOf(Muscle.LATS, Muscle.UPPER_BACK, Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.TRICEPS))
        assertEquals(m.load(m.muscle(top.key), top.value.primary, top.value.secondary), line())
        // Calves: not trained this week.
        tapMuscle(Muscle.CALVES, back = true)
        assertEquals(m.load(m.muscle(Muscle.CALVES), 0, 0), line())

        // 3. "Gesamt": the old calf workout counts; the choice is remembered.
        compose.onNodeWithText(m.period(app.overfit.domain.MusclePeriod.TOTAL)).performClick()
        compose.onNodeWithText(m.period(app.overfit.domain.MusclePeriod.TOTAL)).assertIsSelected()
        assertEquals("total", kv.get("muscles.period"))
        tapMuscle(Muscle.CALVES, back = true)
        assertEquals(m.load(m.muscle(Muscle.CALVES), 3, 0), line())

        // 4. ✕ closes; reopening keeps "Gesamt".
        compose.onNodeWithContentDescription(m.close).performClick()
        compose.waitUntilState { !sheetOpen() }
        compose.onNodeWithContentDescription(m.open).performClick()
        compose.waitUntilState { sheetOpen() }
        compose.onNodeWithText(m.period(app.overfit.domain.MusclePeriod.TOTAL)).assertIsSelected()

        // 5. A tap on the dimmed area above the sheet closes it too.
        val roots = compose.onAllNodes(isRoot())
        roots[roots.fetchSemanticsNodes().size - 1].performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 10f)) }
        compose.waitUntilState { !sheetOpen() }

        // 6. Training detail: "Trainierte Muskeln" of exactly this training.
        compose.openCalendarDay(realToday().dayOfMonth)
        compose.onAllNodes(hasText("Push"))[0].performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("workout-muscles")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("workout-muscles").performScrollTo()
        compose.onNodeWithText(m.title.uppercase()).performScrollTo()
        tapMuscle(top.key, back = top.key in setOf(Muscle.LATS, Muscle.UPPER_BACK, Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.TRICEPS))
        assertEquals(m.load(m.muscle(top.key), top.value.primary, top.value.secondary), line())
        // No period chips in the detail.
        assertTrue(!shown(m.period(app.overfit.domain.MusclePeriod.WEEK)))

        System.getenv("SCREENSHOT_DIR")?.let { dir ->
            compose.waitForIdle()
            File(File(dir, "muskel-heatmap").also(File::mkdirs), "03-trainingsdetail-dunkel-360.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } ?: Unit
    }
}
