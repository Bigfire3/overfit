package app.overfit.i18n

/** Texts of the body-weight log in the calendar tab and the day view (part of [Strings.bodyWeight]). */
data class BodyWeightStrings(
    val title: String,
    /** Card without any entry yet. */
    val logToday: String,
    val logTodayHint: String,
    /** "−0,6 kg zur Vorwoche"; the argument is already signed and has its unit. */
    val weekChange: (String) -> String,
    val noWeekChange: String,
    /** The latest value is not from today: "Stand 3. Nov." */
    val asOf: (date: String) -> String,
    val today: String,
    val sparkline: String,
    /** Screen-reader text of the card. */
    val cardDescription: (value: String, detail: String) -> String,
    /** Day view. */
    val fieldDescription: (unit: String) -> String,
    val deleteEntry: String,
    val invalid: (min: String, max: String) -> String,
    val dayHint: String,
    /** Calendar legend and the list row. */
    val legend: String,
    val listValue: (String) -> String,
    /** Appended to the screen-reader text of a calendar day. */
    val dayDescription: (String) -> String,
    /** "Today" icon in the calendar title row (screen-reader label). */
    val jumpTodayDescription: String,
)

val BodyWeightGerman = BodyWeightStrings(
    title = "Körpergewicht",
    logToday = "Gewicht eintragen",
    logTodayHint = "Tippe, um dein heutiges Gewicht einzutragen.",
    weekChange = { "$it zur Vorwoche" },
    noWeekChange = "Noch kein Wert aus der Vorwoche",
    asOf = { "Stand $it" },
    today = "Heute",
    sparkline = "Verlauf der letzten 8 Wochen",
    cardDescription = { value, detail -> "Körpergewicht $value, $detail. Tippen, um das heutige Gewicht einzutragen." },
    fieldDescription = { "Körpergewicht in $it" },
    deleteEntry = "Gewicht löschen",
    invalid = { min, max -> "Bitte ein Gewicht zwischen $min und $max eingeben." },
    dayHint = "Ein Wert pro Tag, wird sofort gespeichert. Ein neuer Wert ersetzt den alten.",
    legend = "Gewicht",
    listValue = { "Gewicht $it" },
    dayDescription = { ", Gewicht $it" },
    jumpTodayDescription = "Zu heute springen",
)

val BodyWeightEnglish = BodyWeightStrings(
    title = "Body weight",
    logToday = "Log your weight",
    logTodayHint = "Tap to log today's weight.",
    weekChange = { "$it vs. last week" },
    noWeekChange = "No value from last week yet",
    asOf = { "As of $it" },
    today = "Today",
    sparkline = "Trend of the last 8 weeks",
    cardDescription = { value, detail -> "Body weight $value, $detail. Tap to log today's weight." },
    fieldDescription = { "Body weight in $it" },
    deleteEntry = "Delete weight",
    invalid = { min, max -> "Please enter a weight between $min and $max." },
    dayHint = "One value per day, saved right away. A new value replaces the old one.",
    legend = "Weight",
    listValue = { "Weight $it" },
    dayDescription = { ", weight $it" },
    jumpTodayDescription = "Jump to today",
)
