package app.overfit.domain

/** Übung, wie sie die UI verwendet. */
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

    /** Kurzinfo für Listen, z. B. "Brust · Langhantel". */
    val subtitle: String
        get() = listOfNotNull(primaryMuscles.firstOrNull()?.label, equipment?.label).joinToString(" · ")

    /** Initialen für die Kachel ohne Bild: zwei Wörter → je erster Buchstabe, sonst die ersten zwei Buchstaben. */
    val initials: String get() = initialsOf(name)

    companion object {
        fun initialsOf(name: String): String {
            val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            val raw = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}"
                words.size == 1 -> words[0].take(2)
                else -> "?"
            }
            return raw.uppercase()
        }
    }
}

/** Vorlage für Listen (Start, Übungen → Vorlagen). */
data class TemplateSummary(
    val id: Long,
    val name: String,
    val exerciseNames: List<String>,
)

/** Vollständige Vorlage. */
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
