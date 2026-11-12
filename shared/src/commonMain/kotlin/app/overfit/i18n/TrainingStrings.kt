package app.overfit.i18n

/** Texts for the running training and the workout plan (skip, finish, alternatives; part of [Strings.training]). */
data class TrainingStrings(
    /** ⋮ menu of an exercise in the running training: leave it out today. */
    val skipToday: String,
    /** ⋮ menu / card of a skipped exercise: take it back in. */
    val unskip: String,
    /** Label on a skipped exercise (running training, training detail). */
    val skipped: String,
    /** Accessibility: tap on a skipped card brings it back. */
    val skippedCardHint: (exercise: String) -> String,
    /** Floating button once nothing is open any more (green, double tap). */
    val finishTraining: String,
    /** Finish flow: how many exercises were skipped. */
    val skippedExercises: (Int) -> String,
    /** Workout view: changes are stored right away (no "Save"). */
    val autosaved: String,
    /** Footer next to "Undo" in the workout view. */
    val exerciseRemoved: String,
    val exerciseReplaced: String,
    /** Play button on a workout card in the list. */
    val startNamed: (workout: String) -> String,
    /** Editor ⋮: add an alternative (A/B) to this exercise. */
    val addAlternative: String,
    /** Label above an alternative in the editor. */
    val alternative: String,
)

val TrainingGerman = TrainingStrings(
    skipToday = "Heute auslassen",
    unskip = "Nicht auslassen",
    skipped = "Ausgelassen",
    skippedCardHint = { "$it wieder aufnehmen" },
    finishTraining = "Training beenden",
    skippedExercises = { if (it == 1) "1 Übung ausgelassen" else "$it Übungen ausgelassen" },
    autosaved = "speichert automatisch",
    exerciseRemoved = "Übung entfernt",
    exerciseReplaced = "Übung ersetzt",
    startNamed = { "$it starten" },
    addAlternative = "Alternative hinzufügen",
    alternative = "Alternative",
)

val TrainingEnglish = TrainingStrings(
    skipToday = "Skip today",
    unskip = "Don't skip",
    skipped = "Skipped",
    skippedCardHint = { "Take $it back in" },
    finishTraining = "Finish workout",
    skippedExercises = { if (it == 1) "1 exercise skipped" else "$it exercises skipped" },
    autosaved = "saves automatically",
    exerciseRemoved = "Exercise removed",
    exerciseReplaced = "Exercise replaced",
    startNamed = { "Start $it" },
    addAlternative = "Add alternative",
    alternative = "Alternative",
)
