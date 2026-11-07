package app.overfit.i18n

import app.overfit.coach.TrainingExperience
import app.overfit.coach.TrainingGoal
import app.overfit.domain.Language

/**
 * Texts of the Coach tab chat (new chat, conversation, context sheet, profile popup, earlier chats). Same rule as
 * [Strings]: German and English are both required, the constructor has no defaults (a missing translation does not
 * compile).
 */
class CoachChatStrings(
    val title: String,
    val history: String,
    val newChat: String,
    /** The three suggestions of an empty chat (tap puts the text into the input). */
    val suggestions: List<String>,
    val placeholder: String,
    val send: String,
    val addContext: String,
    /** Screen readers: "Modell: Claude Sonnet 5.5". */
    val modelChip: (label: String) -> String,
    val thinking: String,
    val failed: String,
    val retry: String,
    val demoHint: String,
    /** Own key: "Claude Sonnet 5.5 · eigener Schlüssel". */
    val ownKey: (model: String) -> String,
    val workoutChange: (name: String) -> String,
    val workoutProposal: (name: String) -> String,
    val workoutExercise: (name: String, sets: Int) -> String,
    val moreExercises: (count: Int) -> String,
    val apply: String,
    val chipProfile: String,
    val remove: (label: String) -> String,
    val photoN: (n: Int) -> String,
    val today: String,
    val yesterday: String,
    val close: String,
    val back: String,
    // Context sheet
    val contextTitle: String,
    val photos: String,
    val camera: String,
    val noPhotos: String,
    val selectedPhoto: (n: Int) -> String,
    val rowTraining: String,
    val rowTrainingHint: String,
    val rowWorkout: String,
    val rowWorkoutHint: String,
    val rowHistory: String,
    val rowHistoryHint: String,
    /** Label of the history attachment: "Verlauf · 4 Wochen". */
    val historyLabel: (weeks: Int) -> String,
    val rowExercise: String,
    val rowExerciseHint: String,
    /** Sub line of an exercise: "Bestwert 80×8". */
    val bestValue: (value: String) -> String,
    val searchExercise: String,
    val clearSearch: String,
    val emptyTrainings: String,
    val emptyPlans: String,
    val emptyExercises: String,
    val add: (count: Int) -> String,
    val addNone: String,
    val profileAgain: String,
    // Profile popup
    val profileTitle: String,
    val goal: String,
    val goalLabel: (TrainingGoal) -> String,
    val experience: String,
    val experienceLabel: (TrainingExperience) -> String,
    val days: String,
    val height: String,
    val heightUnit: String,
    val weight: String,
    val weightMissing: String,
    /** Calendar weight: "82,4 kg · Stand 05.11.". */
    val weightAsOf: (weight: String, date: String) -> String,
    val equipment: String,
    val equipmentPlaceholder: String,
    val limitations: String,
    val limitationsPlaceholder: String,
    val save: String,
    // Earlier chats
    val historyTitle: String,
    val historyEmpty: String,
    val deleteChat: String,
    // Model chip without the real wiring
    val noAccess: String,
)

val GermanCoachChat = CoachChatStrings(
    title = "Coach",
    history = "Frühere Chats",
    newChat = "Neuer Chat",
    suggestions = listOf("Wie lief mein Training?", "Was mache ich nächstes Mal?", "Wo trete ich auf der Stelle?"),
    placeholder = "Frag deinen Coach …",
    send = "Senden",
    addContext = "Kontext hinzufügen",
    modelChip = { "Modell: $it" },
    thinking = "Denkt nach …",
    failed = "Keine Antwort bekommen.",
    retry = "Erneut versuchen",
    demoHint = "Demo-Antwort. Verbinde eine KI (eigener Key oder Guthaben), damit der Coach wirklich antwortet.",
    ownKey = { "$it · eigener Schlüssel" },
    workoutChange = { "Änderung für „$it“" },
    workoutProposal = { "Workout-Vorschlag: $it" },
    workoutExercise = { name, sets -> "$name · ${if (sets == 1) "1 Satz" else "$sets Sätze"}" },
    moreExercises = { "+ $it weitere" },
    apply = "Übernehmen",
    chipProfile = "Profil",
    remove = { "$it entfernen" },
    photoN = { "Foto $it" },
    today = "heute",
    yesterday = "gestern",
    close = "Schließen",
    back = "Zurück",
    contextTitle = "Kontext",
    photos = "Fotos",
    camera = "Kamera",
    noPhotos = "Noch keine Fotos.",
    selectedPhoto = { "Gewählt als Nummer $it" },
    rowTraining = "Training",
    rowTrainingHint = "Aus dem Kalender",
    rowWorkout = "Workout",
    rowWorkoutHint = "Gespeicherter Plan",
    rowHistory = "Verlauf",
    rowHistoryHint = "Letzte 4 Wochen zusammengefasst",
    historyLabel = { "Verlauf · $it Wochen" },
    rowExercise = "Übung",
    rowExerciseHint = "Bestwerte und Fortschritt",
    bestValue = { "Bestwert $it" },
    searchExercise = "Übung suchen",
    clearSearch = "Suche löschen",
    emptyTrainings = "Noch keine beendeten Trainings.",
    emptyPlans = "Noch keine Workouts.",
    emptyExercises = "Noch keine Übung trainiert.",
    add = { "$it hinzufügen" },
    addNone = "Hinzufügen",
    profileAgain = "Profil wieder mitschicken",
    profileTitle = "Dein Profil",
    goal = "Ziel",
    goalLabel = {
        when (it) {
            TrainingGoal.MUSCLE -> "Muskelaufbau"
            TrainingGoal.STRENGTH -> "Kraft"
            TrainingGoal.FAT_LOSS -> "Abnehmen"
            TrainingGoal.FITNESS -> "Fitness"
        }
    },
    experience = "Erfahrung",
    experienceLabel = {
        when (it) {
            TrainingExperience.BEGINNER -> "Anfänger"
            TrainingExperience.INTERMEDIATE -> "Fortgeschritten"
            TrainingExperience.ADVANCED -> "Profi"
        }
    },
    days = "Tage pro Woche",
    height = "Größe",
    heightUnit = "cm",
    weight = "Gewicht",
    weightMissing = "Im Kalender eintragen",
    weightAsOf = { w, d -> "$w · Stand $d" },
    equipment = "Equipment",
    equipmentPlaceholder = "z. B. Studio, Kurzhanteln bis 20 kg",
    limitations = "Einschränkungen",
    limitationsPlaceholder = "z. B. linke Schulter",
    save = "Speichern",
    historyTitle = "Frühere Chats",
    historyEmpty = "Noch keine Chats.",
    deleteChat = "Chat löschen",
    noAccess = "Kein KI-Zugang",
)

val EnglishCoachChat = CoachChatStrings(
    title = "Coach",
    history = "Earlier chats",
    newChat = "New chat",
    suggestions = listOf("How did my training go?", "What should I do next time?", "Where am I stuck?"),
    placeholder = "Ask your coach …",
    send = "Send",
    addContext = "Add context",
    modelChip = { "Model: $it" },
    thinking = "Thinking …",
    failed = "No answer received.",
    retry = "Try again",
    demoHint = "Demo answer. Connect an AI (own key or credit) so the coach really answers.",
    ownKey = { "$it · own key" },
    workoutChange = { "Change for ‘$it’" },
    workoutProposal = { "Workout proposal: $it" },
    workoutExercise = { name, sets -> "$name · ${if (sets == 1) "1 set" else "$sets sets"}" },
    moreExercises = { "+ $it more" },
    apply = "Apply",
    chipProfile = "Profile",
    remove = { "Remove $it" },
    photoN = { "Photo $it" },
    today = "today",
    yesterday = "yesterday",
    close = "Close",
    back = "Back",
    contextTitle = "Context",
    photos = "Photos",
    camera = "Camera",
    noPhotos = "No photos yet.",
    selectedPhoto = { "Selected as number $it" },
    rowTraining = "Training",
    rowTrainingHint = "From the calendar",
    rowWorkout = "Workout",
    rowWorkoutHint = "Saved plan",
    rowHistory = "History",
    rowHistoryHint = "Last 4 weeks summarised",
    historyLabel = { "History · $it weeks" },
    rowExercise = "Exercise",
    rowExerciseHint = "Best values and progress",
    bestValue = { "Best $it" },
    searchExercise = "Search exercise",
    clearSearch = "Clear search",
    emptyTrainings = "No finished trainings yet.",
    emptyPlans = "No workouts yet.",
    emptyExercises = "No exercise trained yet.",
    add = { "Add $it" },
    addNone = "Add",
    profileAgain = "Send profile again",
    profileTitle = "Your profile",
    goal = "Goal",
    goalLabel = {
        when (it) {
            TrainingGoal.MUSCLE -> "Muscle gain"
            TrainingGoal.STRENGTH -> "Strength"
            TrainingGoal.FAT_LOSS -> "Fat loss"
            TrainingGoal.FITNESS -> "Fitness"
        }
    },
    experience = "Experience",
    experienceLabel = {
        when (it) {
            TrainingExperience.BEGINNER -> "Beginner"
            TrainingExperience.INTERMEDIATE -> "Intermediate"
            TrainingExperience.ADVANCED -> "Advanced"
        }
    },
    days = "Days per week",
    height = "Height",
    heightUnit = "cm",
    weight = "Weight",
    weightMissing = "Add it in the calendar",
    weightAsOf = { w, d -> "$w · as of $d" },
    equipment = "Equipment",
    equipmentPlaceholder = "e.g. gym, dumbbells up to 20 kg",
    limitations = "Limitations",
    limitationsPlaceholder = "e.g. left shoulder",
    save = "Save",
    historyTitle = "Earlier chats",
    historyEmpty = "No chats yet.",
    deleteChat = "Delete chat",
    noAccess = "No AI access",
)

fun coachChatStringsFor(language: Language): CoachChatStrings = when (language) {
    Language.DE -> GermanCoachChat
    Language.EN -> EnglishCoachChat
}
