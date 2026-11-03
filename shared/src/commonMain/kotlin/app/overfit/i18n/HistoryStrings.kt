package app.overfit.i18n

/** Texts for the finish flow, history, workout detail and exercise progress (part of [Strings.history]). */
data class HistoryStrings(
    val chatNotes: String,
    // Finish flow
    val finishTitle: String,
    val duration: String,
    val setsLabel: String,
    val volume: String,
    val exercisesLabel: String,
    val setsOf: (done: Int, total: Int) -> String,
    val exerciseCount: (Int) -> String,
    val deviations: (skipped: Int, added: Int, replaced: Int) -> String,
    val newRecords: String,
    /** [kind] is the metric label (e.g. "est. 1RM") or empty for plain weight, reps and time. */
    val recordLine: (name: String, kind: String, value: String, previous: String) -> String,
    val estimatedOneRm: String,
    val feeling: String,
    val feelingValue: (Int) -> String,
    val note: String,
    val notePlaceholder: String,
    val photos: String,
    val takePhoto: String,
    val pickPhoto: String,
    val photosPrivate: String,
    val removePhoto: String,
    val photoDescription: (Int) -> String,
    val loading: String,

    // History list and detail
    val setCount: (Int) -> String,
    val photoCount: (Int) -> String,
    val notDone: String,
    val markerAdded: String,
    val markerReplaced: String,
    val useAsTemplate: String,
    val shareWorkout: String,
    val workoutMissing: String,

    // Sharing a workout
    val shareWorkoutHint: String,
    val withActual: String,

    // Exercise progress
    val progress: String,
    val bests: String,
    val maxWeight: String,
    val bestE1rm: String,
    val maxReps: String,
    val longestTime: String,
    val recentWorkouts: String,
    val progressEmpty: String,
    val e1rmShort: (String) -> String,
)

val HistoryGerman = HistoryStrings(
    chatNotes = "Notizen aus dem KI-Chat",
    finishTitle = "Training beendet",
    duration = "Dauer",
    setsLabel = "Sätze",
    volume = "Volumen",
    exercisesLabel = "Übungen",
    setsOf = { done, total -> "$done von $total Sätzen" },
    exerciseCount = { if (it == 1) "1 Übung" else "$it Übungen" },
    deviations = { skipped, added, replaced ->
        listOfNotNull(
            if (skipped > 0) (if (skipped == 1) "1 Satz übersprungen" else "$skipped Sätze übersprungen") else null,
            if (added > 0) (if (added == 1) "1 Übung hinzugefügt" else "$added Übungen hinzugefügt") else null,
            if (replaced > 0) (if (replaced == 1) "1 Übung ersetzt" else "$replaced Übungen ersetzt") else null,
        ).joinToString(" · ")
    },
    newRecords = "Neue Bestwerte",
    recordLine = { name, kind, value, previous ->
        "$name · ${if (kind.isEmpty()) "" else "$kind "}$value (vorher $previous)"
    },
    estimatedOneRm = "geschätztes 1RM",
    feeling = "Gefühl",
    feelingValue = { "Gefühl $it von 5" },
    note = "Notiz",
    notePlaceholder = "Wie war das Training?",
    photos = "Fotos",
    takePhoto = "Foto aufnehmen",
    pickPhoto = "Aus Fotos wählen",
    photosPrivate = "Fotos bleiben privat in der App und werden nie geteilt.",
    removePhoto = "Foto entfernen",
    photoDescription = { "Foto $it" },
    loading = "…",

    setCount = { if (it == 1) "1 Satz" else "$it Sätze" },
    photoCount = { if (it == 1) "1 Foto" else "$it Fotos" },
    notDone = "offen",
    markerAdded = "hinzugefügt",
    markerReplaced = "ersetzt",
    useAsTemplate = "Als Workout speichern",
    shareWorkout = "Teilen",
    workoutMissing = "Dieses Training gibt es nicht mehr.",

    shareWorkoutHint = "Teilt Übungen und Sätze – nie Fotos, Notizen oder Gefühl.",
    withActual = "Mit Ist-Werten",

    progress = "Fortschritt",
    bests = "Bestwerte",
    maxWeight = "Höchstgewicht",
    bestE1rm = "Bestes geschätztes 1RM",
    maxReps = "Meiste Wiederholungen",
    longestTime = "Längste Zeit",
    recentWorkouts = "Letzte Trainings",
    progressEmpty = "Noch nicht trainiert. Sobald du diese Übung in einem Training abschließt, siehst du hier deinen Fortschritt.",
    e1rmShort = { "1RM $it" },
)

val HistoryEnglish = HistoryStrings(
    chatNotes = "Notes from the AI chat",
    finishTitle = "Workout done",
    duration = "Duration",
    setsLabel = "Sets",
    volume = "Volume",
    exercisesLabel = "Exercises",
    setsOf = { done, total -> "$done of $total sets" },
    exerciseCount = { if (it == 1) "1 exercise" else "$it exercises" },
    deviations = { skipped, added, replaced ->
        listOfNotNull(
            if (skipped > 0) (if (skipped == 1) "1 set skipped" else "$skipped sets skipped") else null,
            if (added > 0) (if (added == 1) "1 exercise added" else "$added exercises added") else null,
            if (replaced > 0) (if (replaced == 1) "1 exercise replaced" else "$replaced exercises replaced") else null,
        ).joinToString(" · ")
    },
    newRecords = "New records",
    recordLine = { name, kind, value, previous ->
        "$name · ${if (kind.isEmpty()) "" else "$kind "}$value (previously $previous)"
    },
    estimatedOneRm = "est. 1RM",
    feeling = "Feeling",
    feelingValue = { "Feeling $it of 5" },
    note = "Note",
    notePlaceholder = "How was the workout?",
    photos = "Photos",
    takePhoto = "Take photo",
    pickPhoto = "Choose from photos",
    photosPrivate = "Photos stay private in the app and are never shared.",
    removePhoto = "Remove photo",
    photoDescription = { "Photo $it" },
    loading = "…",

    setCount = { if (it == 1) "1 set" else "$it sets" },
    photoCount = { if (it == 1) "1 photo" else "$it photos" },
    notDone = "open",
    markerAdded = "added",
    markerReplaced = "replaced",
    useAsTemplate = "Save as workout",
    shareWorkout = "Share",
    workoutMissing = "This workout no longer exists.",

    shareWorkoutHint = "Shares exercises and sets – never photos, notes or feeling.",
    withActual = "With actual values",

    progress = "Progress",
    bests = "Personal bests",
    maxWeight = "Max weight",
    bestE1rm = "Best estimated 1RM",
    maxReps = "Most reps",
    longestTime = "Longest time",
    recentWorkouts = "Recent workouts",
    progressEmpty = "Not trained yet. Once you complete this exercise in a workout, your progress shows up here.",
    e1rmShort = { "1RM $it" },
)
