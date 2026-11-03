package app.overfit.domain

/** An exercise as used by the UI. */
data class Exercise(
    val id: String,
    val name: String,
    val source: ExerciseSource,
    val imageRef: String?,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val equipment: Equipment?,
    val trackingType: TrackingType,
    val defaultRestSec: Int?,
) {
    val isCustom: Boolean get() = source == ExerciseSource.CUSTOM

    /** Short info for lists, e.g. "Brust · Langhantel". */
    fun subtitle(language: Language): String =
        listOfNotNull(primaryMuscles.firstOrNull()?.label(language), equipment?.label(language)).joinToString(" · ")

    /** Initials for the tile without image: two words → first letters, otherwise the first two letters. */
    val initials: String get() = initialsOf(name)

    companion object {
        fun initialsOf(name: String): String {
            val words = buildList {
                val current = StringBuilder()
                for (c in name + ' ') {
                    if (c.isLetterOrDigit()) current.append(c)
                    else if (current.isNotEmpty()) { add(current.toString()); current.clear() }
                }
            }
            val raw = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}"
                words.size == 1 -> words[0].take(2)
                else -> "?"
            }
            return raw.uppercase()
        }
    }
}

/** Template for lists (start screen, exercises → templates). */
data class TemplateSummary(
    val id: Long,
    val name: String,
    val exerciseNames: List<String>,
)

/** A complete template. */
data class Template(
    val id: Long,
    val name: String,
    val note: String?,
    val exercises: List<TemplateExercise>,
)

data class TemplateExercise(
    val exercise: Exercise,
    val restSec: Int?,
    val note: String?,
    val sets: List<PlannedSet>,
)

data class PlannedSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
)
