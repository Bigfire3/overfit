package app.overfit.share

import app.overfit.domain.PlannedSet
import app.overfit.domain.TemplateExercise
import app.overfit.domain.Workout
import app.overfit.domain.orElse

import app.overfit.data.CustomExerciseInput
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSearch
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Muscle
import app.overfit.domain.Template
import app.overfit.domain.TrackingType

/** One line of the import preview. [exercise] is set for known exercises, [newExercise] for ones that will be created. */
data class PreviewExercise(
    val exercise: Exercise?,
    val newExercise: CustomExerciseInput?,
    val restSec: Int?,
    val note: String?,
    val sets: List<PlannedSet>,
) {
    val displayName: String get() = exercise?.name ?: newExercise?.name.orEmpty()
    val isNew: Boolean get() = exercise == null
    val trackingType: TrackingType get() = exercise?.trackingType ?: newExercise?.trackingType ?: TrackingType.WEIGHT_REPS
}

data class ImportPreview(val name: String, val note: String?, val exercises: List<PreviewExercise>) {
    val newExerciseNames: List<String> get() = exercises.filter { it.isNew }.map { it.displayName }.distinctBy { it.lowercase() }
}

/** Turns templates into the share format and back (resolving exercises against the local catalogue). */
object ShareMapper {

    fun fromTemplate(t: Template): SharedTraining {
        val custom = LinkedHashMap<String, SharedCustom>()
        val exercises = t.exercises.map { te ->
            val e = te.exercise
            val ref = if (e.isCustom) {
                custom.getOrPut(e.id) {
                    SharedCustom(
                        ref = "c${custom.size + 1}",
                        name = e.name,
                        trackingType = e.trackingType.name,
                        muscle = e.primaryMuscles.firstOrNull()?.key,
                        equipment = e.equipment?.key,
                    )
                }.ref
            } else null
            SharedExercise(
                id = if (ref == null) e.id else null,
                ref = ref,
                sets = te.sets.map { SharedSet(it.weightKg, it.reps, it.durationSec) },
                restSec = te.restSec,
                note = te.note,
            )
        }
        return SharedTraining(name = t.name, note = t.note, exercises = exercises, custom = custom.values.toList())
    }

    /**
     * A finished workout as `kind: "workout"`. Sets carry what was done ([withActual], only
     * completed sets) or what was planned (open sets included; a free set without target uses
     * its logged values). Notes, feeling and photos are never shared (SPEC).
     */
    fun fromWorkout(w: Workout, withActual: Boolean): SharedTraining {
        val template = Template(
            id = 0,
            name = w.name,
            note = null,
            exercises = w.exercises.mapNotNull { e ->
                val sets = if (withActual) {
                    e.sets.filter { it.completed }.map { PlannedSet(it.actual.weightKg, it.actual.reps, it.actual.durationSec) }
                } else {
                    e.sets.map { s -> s.target.orElse(s.actual).let { PlannedSet(it.weightKg, it.reps, it.durationSec) } }
                }
                if (withActual && sets.isEmpty()) null else TemplateExercise(e.exercise, e.restSec, null, sets)
            },
        )
        return fromTemplate(template).copy(kind = ShareFormat.KIND_WORKOUT)
    }

    /**
     * Resolves every exercise against [catalog] (all active exercises, including own ones):
     * `ref` → own exercise with the same name or new; `id` → catalogue entry; `name` → best fuzzy
     * match (German or English) or new own exercise.
     */
    fun resolve(training: SharedTraining, catalog: List<Exercise>): ImportPreview {
        val byId = catalog.associateBy { it.id }
        val index = ExerciseSearch.index(catalog)
        val customByRef = training.custom.associateBy { it.ref }
        val lines = training.exercises.map { se ->
            val sets = se.sets
            val custom = se.ref?.let { customByRef[it] }
            val known: Exercise? = when {
                se.ref != null && custom != null -> catalog.firstOrNull { it.isCustom && it.name.equals(custom.name, ignoreCase = true) }
                se.ref != null -> null
                else -> se.id?.let { byId[it] } ?: se.name?.let { findByName(it, catalog, index) }
            }
            val create: CustomExerciseInput? = if (known != null) null else when {
                custom != null -> CustomExerciseInput(
                    name = custom.name,
                    trackingType = parseTracking(custom.trackingType) ?: inferTracking(sets),
                    restSec = se.restSec,
                    muscle = Muscle.fromKey(custom.muscle ?: ""),
                    equipment = Equipment.fromKey(custom.equipment),
                )
                else -> se.name?.let {
                    CustomExerciseInput(it, inferTracking(sets), se.restSec, null, null)
                } ?: se.id?.let { unknownId(it) }?.let { CustomExerciseInput(it, inferTracking(sets), se.restSec, null, null) }
            }
            if (known == null && create == null) return@map null
            val tracking = known?.trackingType ?: create!!.trackingType
            PreviewExercise(
                exercise = known,
                newExercise = create,
                restSec = se.restSec,
                note = se.note,
                sets = sets.map { s ->
                    PlannedSet(
                        weightKg = s.kg?.takeIf { tracking.usesWeight },
                        reps = s.reps?.takeIf { tracking.usesReps },
                        durationSec = s.sec?.takeIf { tracking.usesTime },
                    )
                },
            )
        }.filterNotNull()
        return ImportPreview(training.name, training.note, lines)
    }

    /** Readable name from an unknown catalogue ID like `fedb:Barbell_Curl` → "Barbell Curl". */
    private fun unknownId(id: String): String? =
        id.substringAfter(':').replace('_', ' ').replace('-', ' ').trim().takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { it.uppercase() }

    private fun findByName(name: String, catalog: List<Exercise>, index: List<ExerciseSearch.Indexed>): Exercise? {
        val q = name.trim()
        catalog.filter { it.name.equals(q, ignoreCase = true) }
            .minByOrNull { if (it.source == ExerciseSource.CUSTOM) 0 else 1 }?.let { return it }
        return ExerciseSearch.search(index, ExerciseSearch.Filter(query = q)).firstOrNull()
    }

    private fun parseTracking(s: String?): TrackingType? = TrackingType.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }

    private fun inferTracking(sets: List<SharedSet>): TrackingType {
        val weight = sets.any { it.kg != null }
        val time = sets.any { it.sec != null }
        val reps = sets.any { it.reps != null }
        return when {
            time && weight -> TrackingType.WEIGHT_TIME
            time && !reps -> TrackingType.TIME
            weight -> TrackingType.WEIGHT_REPS
            reps -> TrackingType.REPS
            else -> TrackingType.WEIGHT_REPS
        }
    }

    /** Builds the template to save. [createdIds] maps the lowercase name of each new exercise to its new ID. */
    fun toTemplate(preview: ImportPreview, createdIds: Map<String, String>): Template = Template(
        id = 0,
        name = preview.name,
        note = preview.note,
        exercises = preview.exercises.map { line ->
            val exercise = line.exercise ?: run {
                val c = line.newExercise!!
                Exercise(
                    id = createdIds.getValue(c.name.lowercase()),
                    name = c.name,
                    source = ExerciseSource.CUSTOM,
                    imageRef = null,
                    primaryMuscles = listOfNotNull(c.muscle),
                    secondaryMuscles = emptyList(),
                    equipment = c.equipment,
                    trackingType = c.trackingType,
                    defaultRestSec = c.restSec,
                )
            }
            TemplateExercise(exercise, line.restSec, line.note, line.sets)
        },
    )
}
