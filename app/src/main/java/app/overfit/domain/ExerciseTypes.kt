package app.overfit.domain

/** Herkunft einer Übung. */
enum class ExerciseSource { WORKOUT_GUIDE, FREE_DB, CUSTOM }

/**
 * Was pro Satz erfasst wird.
 * Bei [WEIGHT_REPS] darf das Gewicht negativ sein (= Unterstützung, z. B. Band) und ist bei
 * Körpergewichtsübungen positiv als Zusatzgewicht zu verstehen.
 */
enum class TrackingType(val label: String) {
    WEIGHT_REPS("Gewicht × Wdh."),
    REPS("Wiederholungen"),
    TIME("Zeit"),
    WEIGHT_TIME("Gewicht × Zeit");

    val usesWeight: Boolean get() = this == WEIGHT_REPS || this == WEIGHT_TIME
    val usesReps: Boolean get() = this == WEIGHT_REPS || this == REPS
    val usesTime: Boolean get() = this == TIME || this == WEIGHT_TIME
}

/** Vereinheitlichte Muskelgruppen beider Kataloge (Schlüssel = Wert in exercises.json und DB). */
enum class Muscle(val key: String, val label: String) {
    CHEST("chest", "Brust"),
    SHOULDERS("shoulders", "Schultern"),
    BICEPS("biceps", "Bizeps"),
    TRICEPS("triceps", "Trizeps"),
    FOREARMS("forearms", "Unterarme"),
    LATS("lats", "Latissimus"),
    UPPER_BACK("upper_back", "Oberer Rücken"),
    LOWER_BACK("lower_back", "Unterer Rücken"),
    CORE("core", "Bauch & Core"),
    GLUTES("glutes", "Gesäß"),
    QUADS("quads", "Oberschenkel vorn"),
    HAMSTRINGS("hamstrings", "Beinbeuger"),
    CALVES("calves", "Waden"),
    ADDUCTORS("adductors", "Adduktoren"),
    ABDUCTORS("abductors", "Abduktoren & Hüfte"),
    LEGS("legs", "Beine allgemein"),
    NECK("neck", "Nacken"),
    CARDIO("cardio", "Ausdauer"),
    MOBILITY("mobility", "Mobilität");

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String): Muscle? = byKey[key]
    }
}

/** Vereinheitlichtes Equipment beider Kataloge. */
enum class Equipment(val key: String, val label: String) {
    BODYWEIGHT("bodyweight", "Körpergewicht"),
    PULLUP_BAR("pullup_bar", "Klimmzugstange"),
    BARBELL("barbell", "Langhantel"),
    EZ_BAR("ez_bar", "SZ-Stange"),
    DUMBBELL("dumbbell", "Kurzhantel"),
    KETTLEBELL("kettlebell", "Kettlebell"),
    CABLE("cable", "Kabelzug"),
    MACHINE("machine", "Maschine"),
    BAND("band", "Widerstandsband"),
    PLATE("plate", "Hantelscheibe"),
    BENCH("bench", "Bank & Box"),
    BALL("ball", "Ball"),
    CARDIO("cardio", "Cardio-Gerät"),
    OTHER("other", "Sonstiges");

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String?): Equipment? = key?.let { byKey[it] }
    }
}
