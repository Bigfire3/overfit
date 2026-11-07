package app.overfit.i18n

/** One page of the Coach story: big icon, headline, one or two short sentences, optional shortcut button. */
class StoryPage(val title: String, val text: String, val action: String? = null)

/**
 * Texts of the Coach story (opens from the "Coach ⓘ" title of the Coach tab and from the settings). Part of
 * [CoachHubStrings] as `story`. Page 1 = the chat, pages 2–4 = the ways to use it (no invite programme any more).
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
        "Frag deinen Coach",
        "Im Chat: Dein Profil und dein letztes Training sind schon dabei, mit „+“ hängst du Fotos, Workouts oder deinen Verlauf an. Vorschläge übernimmst du mit einem Tipp. Ohne Zugang antwortet eine Demo.",
    ),
    ownKey = StoryPage(
        "Mit eigenem Schlüssel",
        "Anthropic, OpenAI, Google oder OpenRouter: Schlüssel eintragen, testen, Modell wählen. Alles geht direkt an den Anbieter, Overfit sieht weder Schlüssel noch Inhalte.",
        "Schlüssel einrichten",
    ),
    credit = StoryPage(
        "Mit Guthaben",
        "Kein Schlüssel nötig: Claude Haiku, Sonnet oder Opus. Du lädst Guthaben auf, es läuft nicht ab, und jede Anfrage steht mit ihrem echten Preis in der Abrechnung.",
        "Zum Guthaben",
    ),
    subscription = StoryPage(
        "Mit deinem Abo",
        "Kopiere fertige Prompts in Claude, ChatGPT oder Gemini und nutze dein normales Abo. Du findest sie in den Einstellungen unter „Coach“.",
        "Zu den Verbindungen",
    ),
)

val EnglishCoachStory = CoachStoryStrings(
    open = "About the coach",
    close = "Close",
    next = "Next",
    back = "Back",
    progress = { page, total -> "Page $page of $total" },
    whatItDoes = StoryPage(
        "Ask your coach",
        "In the chat your profile and your last training are already attached; with \"+\" you add photos, workouts or your history. Apply suggestions with one tap. Without access a demo answers.",
    ),
    ownKey = StoryPage(
        "With your own key",
        "Anthropic, OpenAI, Google or OpenRouter: enter your key, test it, pick a model. Everything goes straight to the provider; Overfit sees neither your key nor your content.",
        "Set up key",
    ),
    credit = StoryPage(
        "With credit",
        "No key needed: Claude Haiku, Sonnet or Opus. Top up credit, it never expires, and every request shows its real price in the billing list.",
        "Go to credit",
    ),
    subscription = StoryPage(
        "With your subscription",
        "Copy ready-made prompts into Claude, ChatGPT or Gemini and use your normal subscription. You find them in the settings under \"Coach\".",
        "See connections",
    ),
)
