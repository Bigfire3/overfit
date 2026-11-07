package app.overfit.i18n

import app.overfit.domain.Muscle
import app.overfit.domain.MusclePeriod

/** Texts of the body map ("Trainierte Muskeln") in the calendar sheet and in the training detail. */
data class MuscleStrings(
    val title: String,
    val open: String,
    val close: String,
    val front: String,
    val back: String,
    val high: String,
    val low: String,
    val period: (MusclePeriod) -> String,
    val muscle: (Muscle) -> String,
    /** Hint below the figure before anything is tapped. */
    val tapHint: String,
    /** "Brust · 6 Sätze" plus the assisting part, e.g. "· 4 mittrainiert". */
    val load: (muscle: String, primary: Int, secondary: Int) -> String,
    val emptyPeriod: (MusclePeriod) -> String,
    val emptyWorkout: String,
    /** Accessibility summary of the figure: the trained muscles, most first. */
    val summary: (List<String>) -> String,
)

private fun setsDe(n: Int) = if (n == 1) "1 Satz" else "$n Sätze"
private fun setsEn(n: Int) = if (n == 1) "1 set" else "$n sets"

val MuscleGerman = MuscleStrings(
    title = "Trainierte Muskeln",
    open = "Trainierte Muskeln anzeigen",
    close = "Schließen",
    front = "Vorne",
    back = "Hinten",
    high = "Viel",
    low = "Wenig",
    period = {
        when (it) {
            MusclePeriod.WEEK -> "Diese Woche"
            MusclePeriod.MONTH -> "Dieser Monat"
            MusclePeriod.YEAR -> "Dieses Jahr"
            MusclePeriod.TOTAL -> "Gesamt"
        }
    },
    muscle = { it.de },
    tapHint = "Tippe auf einen Muskel für die Satzzahl.",
    load = { m, p, s ->
        when {
            p > 0 && s > 0 -> "$m · ${setsDe(p)} · $s mittrainiert"
            p > 0 -> "$m · ${setsDe(p)}"
            s > 0 -> "$m · ${setsDe(s)} mittrainiert"
            else -> "$m · nicht trainiert"
        }
    },
    emptyPeriod = {
        when (it) {
            MusclePeriod.WEEK -> "Diese Woche noch kein abgeschlossenes Training."
            MusclePeriod.MONTH -> "Diesen Monat noch kein abgeschlossenes Training."
            MusclePeriod.YEAR -> "Dieses Jahr noch kein abgeschlossenes Training."
            MusclePeriod.TOTAL -> "Noch kein abgeschlossenes Training."
        }
    },
    emptyWorkout = "Keine abgeschlossenen Sätze.",
    summary = { if (it.isEmpty()) "Keine Muskeln trainiert" else "Trainiert: " + it.joinToString(", ") },
)

val MuscleEnglish = MuscleStrings(
    title = "Muscles trained",
    open = "Show muscles trained",
    close = "Close",
    front = "Front",
    back = "Back",
    high = "High",
    low = "Low",
    period = {
        when (it) {
            MusclePeriod.WEEK -> "This week"
            MusclePeriod.MONTH -> "This month"
            MusclePeriod.YEAR -> "This year"
            MusclePeriod.TOTAL -> "Total"
        }
    },
    muscle = { it.en },
    tapHint = "Tap a muscle to see its sets.",
    load = { m, p, s ->
        when {
            p > 0 && s > 0 -> "$m · ${setsEn(p)} · $s assisting"
            p > 0 -> "$m · ${setsEn(p)}"
            s > 0 -> "$m · ${setsEn(s)} assisting"
            else -> "$m · not trained"
        }
    },
    emptyPeriod = {
        when (it) {
            MusclePeriod.WEEK -> "No finished training this week yet."
            MusclePeriod.MONTH -> "No finished training this month yet."
            MusclePeriod.YEAR -> "No finished training this year yet."
            MusclePeriod.TOTAL -> "No finished training yet."
        }
    },
    emptyWorkout = "No completed sets.",
    summary = { if (it.isEmpty()) "No muscles trained" else "Trained: " + it.joinToString(", ") },
)
