package app.overfit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.ExerciseRepository
import app.overfit.data.InMemoryPhotoFiles
import app.overfit.data.TemplateRepository
import app.overfit.data.WorkoutRepository
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.photoDay
import app.overfit.data.CustomExerciseInput
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.SetValues
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.components.ConfirmArea
import app.overfit.ui.history.WorkoutDetailScreen
import app.overfit.ui.photos.DayScreen
import app.overfit.ui.photos.PHOTO_ADD_LIBRARY_TAG
import app.overfit.ui.theme.OverfitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ways through a calendar day (Fabian 8.11.: "möglichst wenig tippen"): the day shows each training opened (numbers and
 * one line per exercise), its photos and the add tiles on one page; a tap on a training opens the full detail, which
 * shows the same photos of the day (photos belong to the day, DB v13) with the same add tiles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h740dp")
class DayPathsTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ null }, { db })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val workouts = WorkoutRepository(db, photoFiles = InMemoryPhotoFiles())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val start = 1_762_600_000_000L // 8 Nov 2026, morning in Europe
    private val day get() = LocalDate.parse(photoDay(start))

    /** "Push": bench 2 sets done (60 × 8, 62,5 × 6) and one open; plank without a done set. */
    private fun seed(): Workout = runBlocking {
        val exercises = ExerciseRepository(db)
        val bench = exercises.get(exercises.createCustom(CustomExerciseInput("Bank", TrackingType.WEIGHT_REPS, 60)))!!
        val plank = exercises.get(exercises.createCustom(CustomExerciseInput("Plank", TrackingType.TIME, 60)))!!
        var w = Workout.fromTemplate(
            Template(1, "Push", null, listOf(
                TemplateExercise(bench, 60, null, listOf(PlannedSet(60.0, 8), PlannedSet(62.5, 6), PlannedSet(65.0, 4))),
                TemplateExercise(plank, 60, null, listOf(PlannedSet(durationSec = 45))),
            )),
            start, id = "w1",
        )
        val sets = w.exercises[0].sets
        w = w.setValues(sets[0].id, SetValues(60.0, 8)).completeSet(sets[0].id, start + 1000)
        w = w.setValues(sets[1].id, SetValues(62.5, 6)).completeSet(sets[1].id, start + 2000)
        workouts.finish(w, endedAt = start + 3_600_000)
        workouts.setSauna("w1", true)
        workouts.addPhoto(day, byteArrayOf(1, 2, 3), takenAt = start + 4_000_000)
        workouts.getFinished("w1")!!.workout
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        OverfitTheme { CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) { ConfirmArea { content() } } }
    }

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun dayShowsTheTrainingOpenedWithItsExercisesAndOneTapOpensTheDetail() {
        val workout = seed()
        var opened: String? = null
        show {
            val photos by workouts.photos.collectAsState(null)
            val history by workouts.history.collectAsState(null)
            DayScreen(
                day, photos, history, WeightUnit.KG, Language.DE,
                onBack = {}, onOpenPhoto = {}, onOpenWorkout = { opened = it }, onAddPhoto = {}, onRemovePhoto = {},
                workouts = mapOf("w1" to workout),
            )
        }
        waitFor("Push")
        // Numbers of the training without a second tap: sets, sauna, one line per exercise with the best set.
        compose.onNodeWithText("2 Sätze", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sauna", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Bank").assertIsDisplayed()
        compose.onNodeWithText("2 Sätze · 62,5 kg × 6").assertIsDisplayed()
        compose.onNodeWithText("Plank").assertIsDisplayed()
        // The day's photo and the add tiles on the same page.
        compose.onNode(hasContentDescription("Foto 1")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PHOTO_ADD_LIBRARY_TAG).performScrollTo().assertIsDisplayed()
        // One tap on the card: the full detail.
        compose.onNodeWithTag("day-training-w1").performScrollTo().performClick()
        assertEquals("w1", opened)
    }

    @Test fun trainingDetailShowsThePhotosOfItsDayAndOffersAdding() {
        seed()
        show {
            WorkoutDetailScreen(
                "w1", workouts, TemplateRepository(db), WeightUnit.KG, Language.DE, scope,
                onBack = {}, onOpenPhoto = {}, onTemplateCreated = {}, onShare = {},
            )
        }
        waitFor("FOTOS DES TAGES")
        // The photos load in their own flow after the training: wait for the thumbnail, not only the section.
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Foto 1")).fetchSemanticsNodes().isNotEmpty() }
        // The photo has no training link (added to the day) and still shows in the training's detail.
        compose.onNode(hasContentDescription("Foto 1")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PHOTO_ADD_LIBRARY_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fotos gehören zum Tag", substring = true).performScrollTo().assertIsDisplayed()
    }
}
