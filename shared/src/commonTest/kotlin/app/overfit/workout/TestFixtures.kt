package app.overfit.workout

import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType

internal fun exercise(
    id: String,
    type: TrackingType = TrackingType.WEIGHT_REPS,
    rest: Int? = null,
    name: String = "Exercise $id",
) = Exercise(
    id = id,
    name = name,
    source = ExerciseSource.CUSTOM,
    imageRef = null,
    primaryMuscles = emptyList(),
    secondaryMuscles = emptyList(),
    equipment = null,
    trackingType = type,
    defaultRestSec = rest,
)

internal fun planned(weight: Double? = null, reps: Int? = null, duration: Int? = null) = PlannedSet(weight, reps, duration)

internal fun templateOf(vararg entries: TemplateExercise, name: String = "Push") =
    Template(id = 7, name = name, note = null, exercises = entries.toList())

internal fun entry(e: Exercise, restSec: Int? = null, vararg sets: PlannedSet) =
    TemplateExercise(e, restSec = restSec, note = null, sets = sets.toList())
