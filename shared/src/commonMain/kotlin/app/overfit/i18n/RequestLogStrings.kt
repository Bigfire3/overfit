package app.overfit.i18n

import app.overfit.domain.Language

/**
 * Texts of the request log ("Abrechnung"): every AI request with time, purpose, model, tokens, cost and status.
 * Same rule as [Strings]: German and English are both required (a missing translation does not compile).
 */
class RequestLogStrings(
    val title: String,
    /** What is logged (and what never is). */
    val intro: String,
    /** "Letzte 30 Tage: 0,42 € Guthaben". */
    val last30Days: (amount: String) -> String,
    val empty: String,
    /** Server log could not be fetched; the list shows what this device logged. */
    val offline: String,
    val purposeReview: String,
    val purposeChat: String,
    val purposeAutofill: String,
    val purposePlan: String,
    val purposeOther: String,
    /** Name of the credit "provider" in the model line. */
    val providerFuel: String,
    /** Own key: "über OpenRouter". */
    val viaProvider: (provider: String) -> String,
    val costOwnKey: String,
    val costNone: String,
    val statusOk: String,
    val statusFailed: (reason: String) -> String,
    val errInsufficientFunds: String,
    val errUpstreamUnavailable: String,
    val errUpstreamRejected: String,
    val errModelUnavailable: String,
    val errInvalidRequest: String,
    val errTooLarge: String,
    val errImages: String,
    val errInternal: String,
    val errNetwork: String,
    val errTimeout: String,
    val errRateLimit: String,
    val errServer: String,
    val errNoAccess: String,
    val errAuth: String,
    val errBadRequest: String,
    val errInvalidAnswer: String,
    val tokens: (input: String, output: String) -> String,
    val cache: (read: String, write: String) -> String,
    val duration: (seconds: String) -> String,
    /** Failed request: the provider's status and error code (e.g. "HTTP 400 invalid_request_error"), never its text. */
    val providerError: (code: String) -> String,
    /** Fallback: first model did not answer (free), a replacement answered, charged at the cheaper of the two prices. */
    val fallback: (requested: String, reason: String?, served: String) -> String,
    val sourceDevice: String,
    val sourceServer: (id: Long) -> String,
    /** Accessibility: tap to show/hide the details. */
    val showDetails: String,
    val hideDetails: String,
)

val GermanRequestLog = RequestLogStrings(
    title = "Abrechnung",
    intro = "Jede KI-Anfrage mit Zeit, Zweck, Modell, Tokens und Kosten. Inhalte (deine Daten, Fragen, Antworten) werden nie gespeichert.",
    last30Days = { "Letzte 30 Tage: $it Guthaben" },
    empty = "Noch keine KI-Anfragen.",
    offline = "Server nicht erreichbar – du siehst die Liste dieses Geräts.",
    purposeReview = "Coach-Auswertung",
    purposeChat = "Chat",
    purposeAutofill = "Mit KI ausfüllen",
    purposePlan = "Plan",
    purposeOther = "Sonstiges",
    providerFuel = "Guthaben",
    viaProvider = { "über $it" },
    costOwnKey = "über deinen Schlüssel",
    costNone = "nichts berechnet",
    statusOk = "ok",
    statusFailed = { "Fehler: $it" },
    errInsufficientFunds = "Guthaben reichte nicht",
    errUpstreamUnavailable = "Anbieter nicht erreichbar, nichts berechnet",
    errUpstreamRejected = "Anbieter hat die Anfrage abgelehnt, nichts berechnet",
    errModelUnavailable = "Modell gerade nicht verfügbar",
    errInvalidRequest = "Anfrage ungültig",
    errTooLarge = "Anfrage zu groß",
    errImages = "Modell kann keine Bilder lesen",
    errInternal = "Fehler auf dem Overfit-Server, nichts berechnet",
    errNetwork = "Keine Verbindung",
    errTimeout = "Keine Antwort in der Zeit",
    errRateLimit = "Zu viele Anfragen, kurz warten",
    errServer = "Fehler beim Anbieter",
    errNoAccess = "Kein KI-Zugang eingerichtet",
    errAuth = "Schlüssel ungültig oder abgelaufen",
    errBadRequest = "Anbieter hat die Anfrage abgelehnt (z. B. Modell unbekannt)",
    errInvalidAnswer = "Antwort war nicht lesbar",
    tokens = { i, o -> "Tokens: $i ein · $o aus" },
    cache = { r, w -> "Cache: $r gelesen · $w geschrieben" },
    duration = { "Dauer: $it s" },
    providerError = { "Antwort des Anbieters: $it" },
    fallback = { requested, reason, served ->
        "Zuerst $requested: nicht verfügbar${reason?.let { " ($it)" } ?: ""}, nicht berechnet · " +
            "Geantwortet hat $served, berechnet zum Preis des günstigeren der beiden Modelle"
    },
    sourceDevice = "Auf diesem Gerät protokolliert",
    sourceServer = { "Server-Protokoll Nr. $it" },
    showDetails = "Details zeigen",
    hideDetails = "Details ausblenden",
)

val EnglishRequestLog = RequestLogStrings(
    title = "Usage log",
    intro = "Every AI request with time, purpose, model, tokens and cost. Contents (your data, questions, answers) are never stored.",
    last30Days = { "Last 30 days: $it of credit" },
    empty = "No AI requests yet.",
    offline = "Server not reachable – showing this device's list.",
    purposeReview = "Coach review",
    purposeChat = "Chat",
    purposeAutofill = "Fill in with AI",
    purposePlan = "Plan",
    purposeOther = "Other",
    providerFuel = "Credit",
    viaProvider = { "via $it" },
    costOwnKey = "via your key",
    costNone = "nothing charged",
    statusOk = "ok",
    statusFailed = { "Error: $it" },
    errInsufficientFunds = "Not enough credit",
    errUpstreamUnavailable = "Provider not reachable, nothing charged",
    errUpstreamRejected = "Provider rejected the request, nothing charged",
    errModelUnavailable = "Model currently not available",
    errInvalidRequest = "Invalid request",
    errTooLarge = "Request too large",
    errImages = "Model cannot read images",
    errInternal = "Error on the Overfit server, nothing charged",
    errNetwork = "No connection",
    errTimeout = "No answer in time",
    errRateLimit = "Too many requests, wait a moment",
    errServer = "Error at the provider",
    errNoAccess = "No AI access set up",
    errAuth = "Key invalid or expired",
    errBadRequest = "Provider rejected the request (e.g. unknown model)",
    errInvalidAnswer = "Answer could not be read",
    tokens = { i, o -> "Tokens: $i in · $o out" },
    cache = { r, w -> "Cache: $r read · $w written" },
    duration = { "Duration: $it s" },
    providerError = { "Provider response: $it" },
    fallback = { requested, reason, served ->
        "First $requested: not available${reason?.let { " ($it)" } ?: ""}, not charged · " +
            "$served answered, charged at the price of the cheaper of the two models"
    },
    sourceDevice = "Logged on this device",
    sourceServer = { "Server log no. $it" },
    showDetails = "Show details",
    hideDetails = "Hide details",
)

fun requestLogStringsFor(language: Language): RequestLogStrings = when (language) {
    Language.DE -> GermanRequestLog
    Language.EN -> EnglishRequestLog
}
