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
    /** Alternatives (A/B) of this exercise, resolved the same way. */
    val alternatives: List<PreviewExercise> = emptyList(),
) {
    val displayName: String get() = exercise?.name ?: newExercise?.name.orEmpty()
    val isNew: Boolean get() = exercise == null
    val trackingType: TrackingType get() = exercise?.trackingType ?: newExercise?.trackingType ?: TrackingType.WEIGHT_REPS
}

data class ImportPreview(val name: String, val note: String?, val exercises: List<PreviewExercise>) {
    val newExerciseNames: List<String> get() = exercises.flatMap { listOf(it) + it.alternatives }.filter { it.isNew }.map { it.displayName }.distinctBy { it.lowercase() }
}

/** Turns templates into the share format and back (resolving exercises against the local catalogue). */
object ShareMapper {

    fun fromTemplate(t: Template): SharedTraining {
        val custom = LinkedHashMap<String, SharedCustom>()
        fun shared(te: TemplateExercise): SharedExercise {
            val e = te.exercise
            val ref = if (e.isCustom) {
                custom.getOrPut(e.id) {
                    SharedCustom(
                        ref = "c${custom.size + 1}",
                        name = e.name,
                        trackingType = e.trackingType.name,
                        muscle = e.primaryMuscles.firstOrNull()?.key,
                        equipment = e.equipment?.key,
                        primary = e.primaryMuscles.map { it.key },
                        secondary = e.secondaryMuscles.map { it.key },
                        restSec = e.defaultRestSec,
                        note = e.note,
                    )
                }.ref
            } else null
            return SharedExercise(
                id = if (ref == null) e.id else null,
                ref = ref,
                sets = te.sets.map { SharedSet(it.weightKg, it.reps, it.durationSec) },
                restSec = te.restSec,
                note = te.note,
            )
        }
        val exercises = t.exercises.map { te -> shared(te).copy(alternatives = te.alternatives.map(::shared)) }
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
     * `ref` → own exercise with the same name, catalogue exercise with exactly that name, or new (with all shared fields); `id` → catalogue entry; `name` → best fuzzy
     * match (German or English) or new own exercise.
     */
    fun resolve(training: SharedTraining, catalog: List<Exercise>): ImportPreview {
        val byId = catalog.associateBy { it.id }
        // IDs from compact QR content (QrPayload): hash of a catalogue ID.
        val byHash = catalog.filter { it.id.startsWith("wg:") }.associateBy { QrPayload.hashedId(it.id) }
        val index = ExerciseSearch.index(catalog)
        val customByRef = training.custom.associateBy { it.ref }
        fun line(se: SharedExercise): PreviewExercise? {
            val sets = se.sets
            val custom = se.ref?.let { customByRef[it] }
            val known: Exercise? = when {
                // Own exercise of the sender: reuse an own exercise with the same name, else a catalogue one with exactly
                // that name, so importing never creates a duplicate.
                se.ref != null && custom != null -> catalog.firstOrNull { it.isCustom && it.name.trim().equals(custom.name.trim(), ignoreCase = true) }
                    ?: findExact(custom.name, catalog)
                se.ref != null -> null
                else -> se.id?.let { byId[it] ?: byHash[it] }
                    ?: se.name?.let { findByName(it, catalog, index) }
                    // Unknown catalogue ID (e.g. "fedb:Barbell_Curl" from before free-exercise-db was removed): exact name only.
                    ?: se.id?.let { unknownId(it) }?.let { findExact(it, catalog) }
            }
            val create: CustomExerciseInput? = if (known != null) null else when {
                custom != null -> CustomExerciseInput(
                    name = custom.name,
                    trackingType = parseTracking(custom.trackingType) ?: inferTracking(sets),
                    restSec = custom.restSec ?: se.restSec,
                    primary = custom.primary.mapNotNull(Muscle::fromKey),
                    equipment = Equipment.fromKey(custom.equipment),
                    secondary = custom.secondary.mapNotNull(Muscle::fromKey),
                    note = custom.note,
                )
                else -> se.name?.let {
                    CustomExerciseInput(it, inferTracking(sets), se.restSec)
                } ?: se.id?.let { unknownId(it) }?.let { CustomExerciseInput(it, inferTracking(sets), se.restSec) }
            }
            if (known == null && create == null) return null
            val tracking = known?.trackingType ?: create!!.trackingType
            return PreviewExercise(
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
        }
        val lines = training.exercises.mapNotNull { se -> line(se)?.copy(alternatives = se.alternatives.mapNotNull(::line)) }
        return ImportPreview(training.name, training.note, lines)
    }

    /** Readable name from an unknown catalogue ID like `fedb:Barbell_Curl` → "Barbell Curl". */
    private fun unknownId(id: String): String? =
        if (id.startsWith(QrPayload.HASHED_ID_PREFIX)) null else id.substringAfter(':').replace('_', ' ').replace('-', ' ').trim().takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { it.uppercase() }

    /** Same name ignoring case, spaces and punctuation ("Barbell Curl" = "Barbell-Curl"). */
    private fun findExact(name: String, catalog: List<Exercise>): Exercise? {
        val key = ExerciseSearch.tokenize(name).joinToString("")
        return catalog.filter { ExerciseSearch.tokenize(it.name).joinToString("") == key }
            .minByOrNull { if (it.source == ExerciseSource.CUSTOM) 0 else 1 }
    }

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

    /** Prefix of the temporary ID of an exercise that does not exist yet (created when the editor saves). */
    const val PENDING_PREFIX = "import:"

    /** The exercises of [preview] that do not exist yet, by their temporary ID. */
    fun pendingInputs(preview: ImportPreview): Map<String, CustomExerciseInput> =
        preview.exercises.flatMap { listOf(it) + it.alternatives }.mapNotNull { it.newExercise }.associateBy { PENDING_PREFIX + it.name.trim().lowercase() }

    /** The template for the editor: new exercises carry a temporary ID, nothing is created yet. */
    fun toDraftTemplate(preview: ImportPreview, id: Long = 0): Template = Template(
        id = id,
        name = preview.name,
        note = preview.note,
        exercises = preview.exercises.map { slot ->
            fun planned(line: PreviewExercise): TemplateExercise {
                val exercise = line.exercise ?: line.newExercise!!.let { it.toExercise(PENDING_PREFIX + it.name.trim().lowercase()) }
                return TemplateExercise(exercise, line.restSec, line.note, line.sets)
            }
            planned(slot).copy(alternatives = slot.alternatives.map(::planned))
        },
    )

    /** Builds the template to save. [createdIds] maps the lowercase name of each new exercise to its new ID. */
    fun toTemplate(preview: ImportPreview, createdIds: Map<String, String>): Template = Template(
        id = 0,
        name = preview.name,
        note = preview.note,
        exercises = preview.exercises.map { slot ->
            fun planned(line: PreviewExercise): TemplateExercise {
                val exercise = line.exercise ?: run {
                    val c = line.newExercise!!
                    c.toExercise(createdIds.getValue(c.name.lowercase()))
                }
                return TemplateExercise(exercise, line.restSec, line.note, line.sets)
            }
            planned(slot).copy(alternatives = slot.alternatives.map(::planned))
        },
    )
}
