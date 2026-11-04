package app.overfit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.ExerciseRepository
import app.overfit.data.CustomExerciseInput
import app.overfit.data.InMemoryPhotoFiles
import app.overfit.data.WorkoutRepository
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.stringsFor
import app.overfit.ui.history.WorkoutEditScreen
import app.overfit.ui.theme.OverfitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editing a finished training afterwards: back with changes needs a second back (discard); nothing is saved. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class WorkoutEditScreenTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown({ null }, { db })

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val workouts = WorkoutRepository(db, photoFiles = InMemoryPhotoFiles())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun finishedWorkout(): String = runBlocking {
        val exercises = ExerciseRepository(db)
        val ex = exercises.get(exercises.createCustom(CustomExerciseInput("Bank", TrackingType.WEIGHT_REPS, 60)))!!
        var w = Workout.fromTemplate(Template(1, "Push", null, listOf(TemplateExercise(ex, 60, null, listOf(PlannedSet(60.0, 8))))), 1000, id = "w1")
        w = w.completeSet(w.exercises[0].sets[0].id, 2000)
        workouts.finish(w, endedAt = 9000)
        workouts.setSauna("w1", true)
        "w1"
    }

    private fun show(id: String, onClose: () -> Unit) = compose.setContent {
        OverfitTheme {
            CompositionLocalProvider(LocalStrings provides stringsFor(Language.DE)) {
                WorkoutEditScreen(id, workouts, WeightUnit.KG, Language.DE, scope, onClose)
            }
        }
    }

    private fun waitFor(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun backWithChangesAsksOnceThenDiscards() {
        val id = finishedWorkout()
        var closed = 0
        show(id) { closed++ }
        waitFor("Statistiken und Bestwerte")
        compose.onNodeWithText("Sauna").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()
        waitFor("Nicht gespeichert")
        assertEquals(0, closed)
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntilState { closed == 1 }
        // Discarded: the stored training still has the sauna.
        assertEquals(true, runBlocking { workouts.getFinished(id)?.sauna })
    }

    @Test fun backWithoutChangesClosesRightAway() {
        val id = finishedWorkout()
        var closed = 0
        show(id) { closed++ }
        waitFor("Statistiken und Bestwerte")
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntilState { closed == 1 }
        assertFalse(compose.onAllNodes(hasText("Nicht gespeichert", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }
}
