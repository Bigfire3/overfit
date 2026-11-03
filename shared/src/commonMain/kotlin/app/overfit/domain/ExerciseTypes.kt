package app.overfit.domain

/**
 * Where an exercise comes from. [FREE_DB] is legacy: free-exercise-db was removed in database v5 (used ones became
 * [CUSTOM]); the value only remains so old backups still parse (they are imported as custom exercises).
 */
enum class ExerciseSource { WORKOUT_GUIDE, FREE_DB, CUSTOM }

/**
 * What is logged per set.
 * With [WEIGHT_REPS] the weight may be negative (= assistance, e.g. a band); for bodyweight
 * exercises a positive weight means added load.
 */
enum class TrackingType {
    WEIGHT_REPS,
    REPS,
    TIME,
    WEIGHT_TIME;

    val usesWeight: Boolean get() = this == WEIGHT_REPS || this == WEIGHT_TIME
    val usesReps: Boolean get() = this == WEIGHT_REPS || this == REPS
    val usesTime: Boolean get() = this == TIME || this == WEIGHT_TIME
}

/**
 * Muscle groups of both catalogues, unified (key = value in exercises.json and the database).
 * Both labels are kept here because search matches German and English terms regardless of the UI language.
 */
enum class Muscle(val key: String, val de: String, val en: String) {
    CHEST("chest", "Brust", "Chest"),
    SHOULDERS("shoulders", "Schultern", "Shoulders"),
    BICEPS("biceps", "Bizeps", "Biceps"),
    TRICEPS("triceps", "Trizeps", "Triceps"),
    FOREARMS("forearms", "Unterarme", "Forearms"),
    LATS("lats", "Latissimus", "Lats"),
    UPPER_BACK("upper_back", "Oberer Rücken", "Upper back"),
    LOWER_BACK("lower_back", "Unterer Rücken", "Lower back"),
    CORE("core", "Bauch & Core", "Core & abs"),
    GLUTES("glutes", "Gesäß", "Glutes"),
    QUADS("quads", "Oberschenkel vorn", "Quads"),
    HAMSTRINGS("hamstrings", "Beinbeuger", "Hamstrings"),
    CALVES("calves", "Waden", "Calves"),
    ADDUCTORS("adductors", "Adduktoren", "Adductors"),
    ABDUCTORS("abductors", "Abduktoren & Hüfte", "Abductors & hips"),
    LEGS("legs", "Beine allgemein", "Legs"),
    NECK("neck", "Nacken", "Neck"),
    CARDIO("cardio", "Ausdauer", "Cardio"),
    MOBILITY("mobility", "Mobilität", "Mobility");

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String): Muscle? = byKey[key]
    }
}

/** Equipment of both catalogues, unified. */
enum class Equipment(val key: String, val de: String, val en: String) {
    BODYWEIGHT("bodyweight", "Körpergewicht", "Bodyweight"),
    PULLUP_BAR("pullup_bar", "Klimmzugstange", "Pull-up bar"),
    BARBELL("barbell", "Langhantel", "Barbell"),
    EZ_BAR("ez_bar", "SZ-Stange", "EZ bar"),
    DUMBBELL("dumbbell", "Kurzhantel", "Dumbbell"),
    KETTLEBELL("kettlebell", "Kettlebell", "Kettlebell"),
    CABLE("cable", "Kabelzug", "Cable"),
    MACHINE("machine", "Maschine", "Machine"),
    BAND("band", "Widerstandsband", "Resistance band"),
    PLATE("plate", "Hantelscheibe", "Plate"),
    BENCH("bench", "Bank & Box", "Bench & box"),
    BALL("ball", "Ball", "Ball"),
    CARDIO("cardio", "Cardio-Gerät", "Cardio machine"),
    OTHER("other", "Sonstiges", "Other");

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String?): Equipment? = key?.let { byKey[it] }
    }
}

fun Muscle.label(language: Language): String = if (language == Language.DE) de else en
fun Equipment.label(language: Language): String = if (language == Language.DE) de else en
