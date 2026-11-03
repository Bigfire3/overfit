package app.overfit.i18n

/** One page of the Coach story: big icon, headline, one or two short sentences, optional shortcut button. */
class StoryPage(val title: String, val text: String, val action: String? = null)

/**
 * Texts of the Coach story (opens from the "Coach" title of the Coach tab). Part of [CoachHubStrings] as `story`.
 * Page 1 = what the AI can evaluate, pages 2–4 = the ways to use it.
 */
class CoachStoryStrings(
    /** Accessibility label of the info button next to the title. */
    val open: String,
    val close: String,
    val next: String,
    val back: String,
    val progress: (page: Int, total: Int) -> String,
    val whatItDoes: StoryPage,
    val ownKey: StoryPage,
    val credit: StoryPage,
    val subscription: StoryPage,
)

val GermanCoachStory = CoachStoryStrings(
    open = "Coach erklärt",
    close = "Schließen",
    next = "Weiter",
    back = "Zurück",
    progress = { page, total -> "Seite $page von $total" },
    whatItDoes = StoryPage(
        "Dein Coach wertet aus",
        "Trainings, Fortschritt und Gewichte. Er schlägt dir neue Workouts vor und beantwortet Fragen.",
    ),
    ownKey = StoryPage(
        "Mit eigenem Schlüssel",
        "Du trägst deinen API-Schlüssel ein. Deine Daten gehen direkt an den Anbieter, nicht über Overfit.",
        "Schlüssel einrichten",
    ),
    credit = StoryPage(
        "Mit Guthaben",
        "Kein Schlüssel nötig. Lade Guthaben auf oder lade Freunde ein: Beide bekommen einen Einladungsbonus.",
        "Zum Guthaben",
    ),
    subscription = StoryPage(
        "Mit deinem Abo",
        "Kopiere fertige Prompts oder verbinde Claude, ChatGPT und Gemini. Ohne Extrakosten in Overfit.",
        "Claude verbinden",
    ),
)

val EnglishCoachStory = CoachStoryStrings(
    open = "About the coach",
    close = "Close",
    next = "Next",
    back = "Back",
    progress = { page, total -> "Page $page of $total" },
    whatItDoes = StoryPage(
        "Your coach evaluates",
        "Workouts, progress and weights. It suggests new workouts and answers questions.",
    ),
    ownKey = StoryPage(
        "With your own key",
        "Enter your API key. Your data goes straight to the provider, not through Overfit.",
        "Set up key",
    ),
    credit = StoryPage(
        "With credit",
        "No key needed. Top up credit or invite friends: you both get a referral bonus.",
        "Go to credit",
    ),
    subscription = StoryPage(
        "With your subscription",
        "Copy ready-made prompts or connect Claude, ChatGPT and Gemini. No extra cost in Overfit.",
        "Connect Claude",
    ),
)
