package app.overfit.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.overfit.coach.CoachErrorKind
import app.overfit.domain.Language

/**
 * Texts of the custom-exercise form (AI autofill, "Did you mean …?", create from the picker). Same rule as [Strings]:
 * German and English are both required. Own file so parallel work on [Strings] does not collide.
 */
class ExerciseFormStrings(
    val language: Language,
    val aiFill: String,
    val aiFilling: String,
    val aiFillHint: String,
    val aiFilled: String,
    val aiNoAccess: String,
    val aiNeedName: String,
    val aiError: (CoachErrorKind) -> String,
    val didYouMean: String,
    val didYouMeanHint: String,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val note: String,
    val notePlaceholder: String,
    val restPlaceholder: String,
    val createInPicker: (String) -> String,
    val createInPickerEmpty: String,
)

val GermanExerciseForm = ExerciseFormStrings(
    language = Language.DE,
    aiFill = "Mit KI ausfüllen",
    aiFilling = "KI füllt aus …",
    aiFillHint = "Aus dem Namen: Erfassungsart, Muskeln, Equipment, Pause, Ausführung",
    aiFilled = "Von der KI ausgefüllt. Prüfe die Felder und ändere, was nicht passt.",
    aiNoAccess = "Ohne KI-Zugang füllst du die Felder selbst aus. Mit eigenem Key oder Fuel (Profil → KI verbinden) " +
        "erledigt das die KI aus dem Namen.",
    aiNeedName = "Erst den Namen eingeben",
    aiError = { kind ->
        when (kind) {
            CoachErrorKind.NETWORK, CoachErrorKind.TIMEOUT -> "Keine Verbindung. Nochmal versuchen oder selbst ausfüllen."
            CoachErrorKind.NO_FUEL -> "Fuel reicht nicht. Bitte selbst ausfüllen."
            CoachErrorKind.AUTH -> "Der API-Key wurde abgelehnt. Prüfe ihn unter Profil → KI verbinden."
            CoachErrorKind.NO_ACCESS -> "Kein KI-Zugang eingerichtet."
            CoachErrorKind.RATE_LIMIT -> "Gerade zu viele Anfragen. Gleich nochmal versuchen."
            else -> "Hat nicht geklappt. Nochmal versuchen oder selbst ausfüllen."
        }
    },
    didYouMean = "Meintest du …?",
    didYouMeanHint = "Diese Übungen gibt es schon. Antippen, um sie zu nehmen.",
    primaryMuscles = "Hauptmuskeln",
    secondaryMuscles = "Hilfsmuskeln",
    note = "Ausführung",
    notePlaceholder = "Kurze Notiz zur Ausführung (optional)",
    restPlaceholder = "Standard",
    createInPicker = { "„$it“ als neue Übung anlegen" },
    createInPickerEmpty = "Neue Übung anlegen",
)

val EnglishExerciseForm = ExerciseFormStrings(
    language = Language.EN,
    aiFill = "Fill in with AI",
    aiFilling = "AI is filling in …",
    aiFillHint = "From the name: tracking, muscles, equipment, rest, how to do it",
    aiFilled = "Filled in by the AI. Check the fields and change what does not fit.",
    aiNoAccess = "Without AI access you fill in the fields yourself. With your own key or Fuel (Profile → Connect AI) " +
        "the AI does it from the name.",
    aiNeedName = "Enter the name first",
    aiError = { kind ->
        when (kind) {
            CoachErrorKind.NETWORK, CoachErrorKind.TIMEOUT -> "No connection. Try again or fill in yourself."
            CoachErrorKind.NO_FUEL -> "Not enough Fuel. Please fill in yourself."
            CoachErrorKind.AUTH -> "The API key was rejected. Check it under Profile → Connect AI."
            CoachErrorKind.NO_ACCESS -> "No AI access set up."
            CoachErrorKind.RATE_LIMIT -> "Too many requests right now. Try again in a moment."
            else -> "That did not work. Try again or fill in yourself."
        }
    },
    didYouMean = "Did you mean …?",
    didYouMeanHint = "These exercises already exist. Tap one to use it.",
    primaryMuscles = "Main muscles",
    secondaryMuscles = "Supporting muscles",
    note = "How to do it",
    notePlaceholder = "Short note on the execution (optional)",
    restPlaceholder = "Default",
    createInPicker = { "Create “$it” as a new exercise" },
    createInPickerEmpty = "Create new exercise",
)

fun exerciseFormStringsFor(language: Language): ExerciseFormStrings = when (language) {
    Language.DE -> GermanExerciseForm
    Language.EN -> EnglishExerciseForm
}

val LocalExerciseFormStrings = staticCompositionLocalOf { GermanExerciseForm }
