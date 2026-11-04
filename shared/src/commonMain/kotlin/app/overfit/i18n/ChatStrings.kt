package app.overfit.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.overfit.domain.Language

/**
 * Texts of the KI-Chat-Verbindung (Claude, ChatGPT, Gemini) and the AI coach settings. Same rule as [Strings]: German and
 * English are both required. Own file so parallel work on [Strings] does not collide.
 */
class ChatStrings(
    val language: Language,

    // KI-Coach settings page
    val aiCoach: String,
    val aiCoachIntro: String,
    val waysInApp: String,
    val waysInChat: String,
    val ownKey: String,
    val fuelWay: String,
    val claude: String,
    val chatgpt: String,
    val gemini: String,
    val wayConnected: String,
    val wayNotConnected: String,
    val waySoon: String,
    val wayInAppHint: String,
    val wayInChatHint: String,

    // One chat provider
    val chatTitle: (provider: String) -> String,
    val chatIntro: (provider: String) -> String,
    val serverUrl: String,
    val serverUrlCopy: String,
    val serverUrlCopied: String,
    val soonTitle: String,
    val soonText: String,
    val stepsTitle: String,
    val steps: (provider: String, url: String) -> List<String>,
    val geminiAltTitle: String,
    val geminiAltText: String,

    // Switching on (consent)
    val connectChat: String,
    val consentTitle: String,
    val consentPoints: List<String>,
    val consentPhotos: String,
    val consentPhotosHint: String,
    val consentAgree: String,
    val consentCancel: String,
    val uploading: (done: Int, total: Int) -> String,
    val uploadingNoCount: String,

    // Status and problems
    val chatOn: String,
    val lastSync: (date: String) -> String,
    val neverSynced: String,
    val problemOffline: String,
    val problemQuota: String,
    val problemFailed: String,
    val errNetwork: String,
    val errGeneric: String,
    val errNotConfigured: String,
    val syncNow: String,

    // Pairing
    val pairTitle: String,
    val pairText: String,
    val pairShow: String,
    val pairExpires: (mmss: String) -> String,
    val pairExpired: String,
    val pairNew: String,
    val pairConnected: (client: String) -> String,

    // Connections
    val connections: String,
    val connectionsNone: String,
    val lastUsed: (date: String) -> String,
    val neverUsed: String,
    val disconnect: String,
    val photosToggle: String,
    val photosToggleHint: String,
    val disableChat: String,
    val disableHint: String,

    // Copy context (works without server)
    val contextCopy: String,
    val contextCopied: String,
    val contextHint: String,
    val gemCopy: String,
    val gemCopied: String,
    val gemHint: String,
    val contextIsEmpty: String,

    // Context text
    val ctxIntro: String,
    val ctxProfile: String,
    val ctxMemory: String,
    val ctxRecent: String,
    val ctxWorkouts: String,
    val ctxProgress: String,
    val ctxUnits: String,
    val ctxToday: (String) -> String,
    val ctxSets: (Int) -> String,
    val ctxNoWorkouts: String,
    val ctxFeeling: (Int) -> String,
    /** Marker in the workout line of the context: sauna right after the workout. */
    val ctxSauna: String,
    val ctxTask: (example: String) -> String,
    val gemInstructions: (example: String) -> String,

    // Inbox
    val inboxStart: (count: Int) -> String,
    val inboxTitle: String,
    val inboxEmpty: String,
    val inboxFrom: (client: String) -> String,
    val inboxFull: String,
    val inboxAsNew: String,
    val inboxUpdate: String,
    val inboxDiscard: String,
    val inboxAccept: String,
    val inboxSavedAsNew: String,
    val inboxUpdated: String,
    val inboxInvalid: String,
    val diffTitle: String,
    val diffAdded: (name: String) -> String,
    val diffRemoved: (name: String) -> String,
    val diffChanged: (name: String, old: String, new: String) -> String,
    val diffRenamed: (old: String, new: String) -> String,
    val diffNone: String,
    val diffSets: (count: Int, text: String) -> String,
    val notesTitle: String,
    val notesEmpty: String,
    val notesRating: (Int) -> String,
    val notesDelete: String,
    val noteForWorkout: String,
)

val ChatGerman = ChatStrings(
    language = Language.DE,
    aiCoach = "KI-Coach",
    aiCoachIntro = "Wähle, wie du dich mit einer KI austauschst. Du kannst den Coach in der App nutzen oder dein Training in Claude, ChatGPT oder Gemini besprechen.",
    waysInApp = "Coach in der App",
    waysInChat = "Im Chat (mit deinem KI-Abo)",
    ownKey = "Eigener API-Key",
    fuelWay = "Guthaben",
    claude = "Claude",
    chatgpt = "ChatGPT",
    gemini = "Gemini",
    wayConnected = "Verbunden",
    wayNotConnected = "Nicht verbunden",
    waySoon = "startet bald",
    wayInAppHint = "Die App wertet dein Training aus. Du zahlst nach Verbrauch: mit eigenem Key beim Anbieter, mit Guthaben über Overfit.",
    wayInChatHint = "Dein Chat liest dein Training (nach deiner Freigabe) und legt Workouts und Notizen für dich in Overfit ab.",
    chatTitle = { it },
    chatIntro = { "Nutze Overfit als Erweiterung in $it: Du legst Trainings an, lässt sie auswerten und sprichst über deinen Fortschritt – mit deinem normalen Abo, ohne extra Kosten in Overfit." },
    serverUrl = "Server-Adresse",
    serverUrlCopy = "Adresse kopieren",
    serverUrlCopied = "Adresse kopiert",
    soonTitle = "Startet bald",
    soonText = "Die Verbindung wird gerade eingerichtet. Bis dahin kannst du den Kontext kopieren und selbst in den Chat einfügen (siehe unten).",
    stepsTitle = "So geht's",
    steps = { provider, url ->
        when (provider) {
            "Claude" -> listOf(
                "Öffne claude.ai → Einstellungen → Connectors → „Eigenen Connector hinzufügen“.",
                "Name „Overfit“, Adresse $url, erweiterte Einstellungen leer lassen → Hinzufügen.",
                "„Verbinden“ tippen. Es öffnet sich die Overfit-Anmeldung: Tippe den Kopplungscode aus der App ein (Button „Mit Chat verbinden“ unten).",
                "Im Chat über „+“ den Connector „Overfit“ einschalten. Er ist danach auch in der Claude-App am Handy verfügbar.",
            )
            "ChatGPT" -> listOf(
                "Öffne chatgpt.com → Einstellungen → Apps & Connectors → Erweitert → „Entwicklermodus“ einschalten.",
                "Apps & Connectors → „Erstellen“: Name „Overfit“, Adresse $url, Authentifizierung „OAuth“.",
                "Anmelden: Tippe den Kopplungscode aus der App ein (Button „Mit Chat verbinden“ unten).",
                "Im Chat „+“ → Entwicklermodus → „Overfit“ wählen. Schreibaktionen (Workout, Notiz) fragt ChatGPT vorher ab.",
                "Ohne Entwicklermodus in deinem Tarif: Nutze „Kontext kopieren“ und die GPT-Anleitung weiter unten.",
            )
            else -> listOf(
                "Wo Gemini „Einstellungen → Verbundene Apps → Eigene Apps“ anbietet: „Eigene App hinzufügen“, Adresse $url eintragen.",
                "Anmelden: Tippe den Kopplungscode aus der App ein (Button „Mit Chat verbinden“ unten).",
                "Bei dir nicht verfügbar? Nimm den Ersatzweg mit Gem (siehe unten).",
            )
        }
    },
    geminiAltTitle = "Ersatzweg für Gemini: eigenes Gem",
    geminiAltText = "Wenn Gemini keine eigenen Verbindungen erlaubt (z. B. in Deutschland): 1) Unten „Gem-Anleitung kopieren“ tippen. 2) In Gemini „Gems → Neues Gem“, Name „Overfit Coach“, Anleitung einfügen, speichern. 3) Vor jedem Gespräch hier „Kontext kopieren“ tippen und in den Chat einfügen. Neue Workouts kopierst du aus der Antwort und importierst sie in Overfit (Workouts → „+“ → Aus Link oder QR-Code importieren). Fotos hängst du einzeln an.",
    connectChat = "KI-Chat verbinden",
    consentTitle = "Daten für den KI-Chat freigeben",
    consentPoints = listOf(
        "Hochgeladen werden Profil, Trainings, Workouts und das Coach-Gedächtnis – damit dein Chat sie lesen kann.",
        "Die Daten liegen verschlüsselt auf dem Overfit-Server, getrennt von deinem Guthaben. Sie werden nicht für Werbung oder zum Training von KI genutzt.",
        "Was du im Chat besprichst, geht an den Anbieter deines Chats (Anthropic, OpenAI oder Google) und unterliegt dessen Regeln.",
        "Du kannst jederzeit abschalten: „KI-Chat trennen“ löscht alles auf dem Server.",
    ),
    consentPhotos = "Körperfotos teilen",
    consentPhotosHint = "Standard aus. Nur wenn du es einschaltest, werden deine Fortschrittsfotos hochgeladen, damit der Chat sie ansehen kann. Du kannst das jederzeit wieder ausschalten; dann werden die Fotos auf dem Server gelöscht.",
    consentAgree = "Zustimmen und verbinden",
    consentCancel = "Abbrechen",
    uploading = { done, total -> "Lade hoch … $done von $total" },
    uploadingNoCount = "Lade hoch …",
    chatOn = "KI-Chat ist eingeschaltet",
    lastSync = { "Zuletzt synchronisiert: $it" },
    neverSynced = "Noch nicht synchronisiert",
    problemOffline = "Kein Netz. Die Änderungen werden hochgeladen, sobald du wieder online bist.",
    problemQuota = "Der Speicher für den KI-Chat ist voll. Neue Trainings werden nicht mehr hochgeladen. Schalte „Körperfotos teilen“ aus oder trenne den KI-Chat und verbinde neu.",
    problemFailed = "Das Hochladen hat nicht geklappt. Es wird später erneut versucht.",
    errNetwork = "Keine Verbindung zum Server. Prüfe dein Netz und versuche es erneut.",
    errGeneric = "Das hat nicht geklappt. Bitte versuche es später erneut.",
    errNotConfigured = "Der Server ist noch nicht eingerichtet.",
    syncNow = "Jetzt synchronisieren",
    pairTitle = "Mit Chat verbinden",
    pairText = "Tippe diesen Code in der Overfit-Anmeldung deines Chats ein (siehe Anleitung oben).",
    pairShow = "Code anzeigen",
    pairExpires = { "Läuft ab in $it" },
    pairExpired = "Der Code ist abgelaufen.",
    pairNew = "Neuer Code",
    pairConnected = { "Verbunden mit $it" },
    connections = "Verbundene Chats",
    connectionsNone = "Noch kein Chat verbunden.",
    lastUsed = { "zuletzt genutzt $it" },
    neverUsed = "noch nicht genutzt",
    disconnect = "Trennen",
    photosToggle = "Körperfotos teilen",
    photosToggleHint = "Aus = Fotos werden auf dem Server gelöscht.",
    disableChat = "KI-Chat trennen",
    disableHint = "Löscht alle Daten auf dem Server und trennt alle Chats.",
    contextCopy = "Kontext kopieren",
    contextCopied = "Kopiert. Füge den Text in deinen Chat ein.",
    contextHint = "Funktioniert ohne Verbindung: Profil, letzte Trainings und Fortschritt als Text für jeden Chat.",
    gemCopy = "Gem-/GPT-Anleitung kopieren",
    gemCopied = "Anleitung kopiert. Füge sie in die Anweisungen deines Gems bzw. GPTs ein.",
    gemHint = "Einmalig als Anleitung eines Gems (Gemini) oder eigenen GPTs (ChatGPT) einfügen.",
    contextIsEmpty = "Noch keine Trainings",
    ctxIntro = "Das ist mein Trainingsstand aus der App Overfit. Nutze ihn für unser Gespräch (Auswertung, Fortschritt, Planung).",
    ctxProfile = "Profil",
    ctxMemory = "Gedächtnis des Coaches",
    ctxRecent = "Letzte Trainings",
    ctxWorkouts = "Meine Workouts",
    ctxProgress = "Fortschritt der Hauptübungen (bester Satz je Training, älteste zuerst)",
    ctxUnits = "Einheiten",
    ctxToday = { "Heute: $it" },
    ctxSets = { if (it == 1) "1 Satz" else "$it Sätze" },
    ctxNoWorkouts = "Noch keine abgeschlossenen Trainings.",
    ctxFeeling = { "Gefühl $it/5" },
    ctxSauna = "danach Sauna",
    ctxTask = { example ->
        """Antworte auf Deutsch. Wenn du mir ein neues Workout vorschlägst, gib es als genau einen ```json-Block im Overfit-Format aus, damit ich es in der App importieren kann:

```json
$example
```
Regeln: "v" ist 1, "kind" ist "template". Übungen per "name" mit dem gängigen englischen Namen. Je Satz "kg" und "reps" bzw. "sec" (Haltezeit), nur Zahlen, Gewichte in kg. "restSec" ist die Pause in Sekunden. Höchstens zwei Sätze Erklärung außerhalb des Blocks."""
    },
    gemInstructions = { example ->
        """Du bist mein Trainings-Coach für die Tracking-App Overfit. Ich füge dir zu Beginn eines Gesprächs meinen Trainingsstand als Text ein (Profil, Gedächtnis, letzte Trainings, Fortschritt). Arbeite damit: werte Trainings aus, beurteile den Fortschritt, schlage die nächsten Gewichte vor und plane Einheiten. Sei konkret und ehrlich, nenne Zahlen aus meinen Daten und erfinde keine. Fehlt dir etwas (Ziel, Equipment, Zeit, Beschwerden), frag nach. Bei Schmerzen oder Verletzungen empfiehl einen Arzt oder Physiotherapeuten.

Schlägst du ein neues oder geändertes Workout vor, gib es als genau einen ```json-Block im Overfit-Format aus, damit ich es in der App importieren kann:

```json
$example
```
Regeln: "v" ist 1, "kind" ist "template". Übungen per "name" mit dem gängigen englischen Namen (z. B. "Bench Press", "Lat Pulldown"). Je Satz "kg" und "reps" bzw. nur "reps" bzw. "sec" (Haltezeit in Sekunden), nur Zahlen, Gewichte in kg. "restSec" ist die Pause nach jedem Satz in Sekunden. Optional "note" je Übung. Höchstens zwei Sätze Erklärung außerhalb des Blocks. Antworte in meiner Sprache."""
    },
    inboxStart = { if (it == 1) "1 neuer Eintrag aus dem Chat" else "$it neue Einträge aus dem Chat" },
    inboxTitle = "Neu aus dem Chat",
    inboxEmpty = "Nichts Neues.",
    inboxFrom = { "Von $it" },
    inboxFull = "Dein Eingang ist voll (50 Einträge). Übernimm oder verwirf einige, damit der Chat neue ablegen kann.",
    inboxAsNew = "Als neues Workout",
    inboxUpdate = "Workout aktualisieren",
    inboxDiscard = "Verwerfen",
    inboxAccept = "Übernehmen",
    inboxSavedAsNew = "Als neues Workout gespeichert",
    inboxUpdated = "Workout aktualisiert",
    inboxInvalid = "Dieser Eintrag lässt sich nicht lesen.",
    diffTitle = "Änderungen",
    diffAdded = { "+ $it" },
    diffRemoved = { "− $it" },
    diffChanged = { name, old, new -> "$name: $old → $new" },
    diffRenamed = { old, new -> "Name: $old → $new" },
    diffNone = "Keine Änderungen an den Übungen.",
    diffSets = { count, text -> if (text.isBlank()) "$count×" else "$count× $text" },
    notesTitle = "Notizen aus dem Chat",
    notesEmpty = "Noch keine Notizen.",
    notesRating = { "Bewertung $it/10" },
    notesDelete = "Löschen",
    noteForWorkout = "Notiz aus dem Chat",
)

val ChatEnglish = ChatStrings(
    language = Language.EN,
    aiCoach = "AI coach",
    aiCoachIntro = "Choose how you talk to an AI. Use the coach inside the app, or discuss your training in Claude, ChatGPT or Gemini.",
    waysInApp = "Coach in the app",
    waysInChat = "In a chat (with your AI subscription)",
    ownKey = "Own API key",
    fuelWay = "Credit",
    claude = "Claude",
    chatgpt = "ChatGPT",
    gemini = "Gemini",
    wayConnected = "Connected",
    wayNotConnected = "Not connected",
    waySoon = "coming soon",
    wayInAppHint = "The app analyses your training. You pay as you go: with your own key at the provider, with credit through Overfit.",
    wayInChatHint = "Your chat reads your training (with your permission) and leaves workouts and notes for you in Overfit.",
    chatTitle = { it },
    chatIntro = { "Use Overfit as an extension in $it: create workouts, have them analysed and talk about your progress – with your normal subscription, no extra cost in Overfit." },
    serverUrl = "Server address",
    serverUrlCopy = "Copy address",
    serverUrlCopied = "Address copied",
    soonTitle = "Coming soon",
    soonText = "The connection is being set up. Until then you can copy the context and paste it into the chat yourself (see below).",
    stepsTitle = "How it works",
    steps = { provider, url ->
        when (provider) {
            "Claude" -> listOf(
                "Open claude.ai → Settings → Connectors → “Add custom connector”.",
                "Name “Overfit”, address $url, leave advanced settings empty → Add.",
                "Tap “Connect”. The Overfit sign-in opens: enter the pairing code from the app (button “Connect to chat” below).",
                "In a chat, switch on the “Overfit” connector via “+”. It is then also available in the Claude app on your phone.",
            )
            "ChatGPT" -> listOf(
                "Open chatgpt.com → Settings → Apps & Connectors → Advanced → switch on “Developer mode”.",
                "Apps & Connectors → “Create”: name “Overfit”, address $url, authentication “OAuth”.",
                "Sign in: enter the pairing code from the app (button “Connect to chat” below).",
                "In a chat, “+” → Developer mode → choose “Overfit”. ChatGPT asks before write actions (workout, note).",
                "No developer mode in your plan? Use “Copy context” and the GPT instructions further down.",
            )
            else -> listOf(
                "Where Gemini offers “Settings → Connected apps → Custom apps”: “Add custom app”, enter the address $url.",
                "Sign in: enter the pairing code from the app (button “Connect to chat” below).",
                "Not available for you? Use the Gem fallback (see below).",
            )
        }
    },
    geminiAltTitle = "Fallback for Gemini: your own Gem",
    geminiAltText = "If Gemini doesn't allow custom connections (e.g. in Germany): 1) Tap “Copy Gem/GPT instructions” below. 2) In Gemini “Gems → New Gem”, name “Overfit Coach”, paste the instructions, save. 3) Before each chat tap “Copy context” here and paste it into the chat. Copy new workouts from the answer and import them in Overfit (Workouts → “+” → Import from link or QR code). Attach photos one by one.",
    connectChat = "Connect AI chat",
    consentTitle = "Share data with the AI chat",
    consentPoints = listOf(
        "Your profile, past workouts, saved workouts and the coach memory are uploaded so your chat can read them.",
        "The data is stored encrypted on the Overfit server, separate from your credit. It is not used for advertising or to train AI.",
        "What you discuss in the chat goes to your chat provider (Anthropic, OpenAI or Google) and is subject to their rules.",
        "You can switch it off any time: “Disconnect AI chat” deletes everything on the server.",
    ),
    consentPhotos = "Share body photos",
    consentPhotosHint = "Off by default. Only if you switch it on are your progress photos uploaded so the chat can look at them. You can switch it off again any time; the photos are then deleted from the server.",
    consentAgree = "Agree and connect",
    consentCancel = "Cancel",
    uploading = { done, total -> "Uploading … $done of $total" },
    uploadingNoCount = "Uploading …",
    chatOn = "AI chat is on",
    lastSync = { "Last synced: $it" },
    neverSynced = "Not synced yet",
    problemOffline = "No network. Changes are uploaded as soon as you're back online.",
    problemQuota = "The storage for the AI chat is full. New workouts are no longer uploaded. Switch off “Share body photos” or disconnect the AI chat and connect again.",
    problemFailed = "Uploading failed. It will be tried again later.",
    errNetwork = "No connection to the server. Check your network and try again.",
    errGeneric = "That didn't work. Please try again later.",
    errNotConfigured = "The server isn't set up yet.",
    syncNow = "Sync now",
    pairTitle = "Connect to chat",
    pairText = "Enter this code in the Overfit sign-in of your chat (see the instructions above).",
    pairShow = "Show code",
    pairExpires = { "Expires in $it" },
    pairExpired = "The code has expired.",
    pairNew = "New code",
    pairConnected = { "Connected to $it" },
    connections = "Connected chats",
    connectionsNone = "No chat connected yet.",
    lastUsed = { "last used $it" },
    neverUsed = "not used yet",
    disconnect = "Disconnect",
    photosToggle = "Share body photos",
    photosToggleHint = "Off = photos are deleted from the server.",
    disableChat = "Disconnect AI chat",
    disableHint = "Deletes all data on the server and disconnects all chats.",
    contextCopy = "Copy context",
    contextCopied = "Copied. Paste the text into your chat.",
    contextHint = "Works without a connection: profile, recent workouts and progress as text for any chat.",
    gemCopy = "Copy Gem/GPT instructions",
    gemCopied = "Instructions copied. Paste them into the instructions of your Gem or GPT.",
    gemHint = "Paste once as the instructions of a Gem (Gemini) or your own GPT (ChatGPT).",
    contextIsEmpty = "No workouts yet",
    ctxIntro = "This is my training status from the app Overfit. Use it for our conversation (analysis, progress, planning).",
    ctxProfile = "Profile",
    ctxMemory = "Coach memory",
    ctxRecent = "Recent workouts",
    ctxWorkouts = "My workouts",
    ctxProgress = "Progress of the main exercises (best set per workout, oldest first)",
    ctxUnits = "Units",
    ctxToday = { "Today: $it" },
    ctxSets = { if (it == 1) "1 set" else "$it sets" },
    ctxNoWorkouts = "No finished workouts yet.",
    ctxFeeling = { "Feeling $it/5" },
    ctxSauna = "sauna afterwards",
    ctxTask = { example ->
        """Answer in English. If you suggest a new workout, output it as exactly one ```json block in the Overfit format so I can import it in the app:

```json
$example
```
Rules: "v" is 1, "kind" is "template". Name exercises via "name" using the common English name. Per set "kg" and "reps" or "sec" (hold time), numbers only, weights in kg. "restSec" is the rest in seconds. At most two sentences of explanation outside the block."""
    },
    gemInstructions = { example ->
        """You are my training coach for the tracking app Overfit. At the start of a conversation I paste my training status as text (profile, memory, recent workouts, progress). Work with it: analyse workouts, judge progress, suggest the next weights and plan sessions. Be concrete and honest, cite numbers from my data and don't invent any. If something is missing (goal, equipment, time, complaints), ask. For pain or injuries, recommend a doctor or physiotherapist.

If you suggest a new or changed workout, output it as exactly one ```json block in the Overfit format so I can import it in the app:

```json
$example
```
Rules: "v" is 1, "kind" is "template". Name exercises via "name" using the common English name (e.g. "Bench Press", "Lat Pulldown"). Per set "kg" and "reps", or only "reps", or "sec" (hold time in seconds), numbers only, weights in kg. "restSec" is the rest after each set in seconds. Optional "note" per exercise. At most two sentences of explanation outside the block. Answer in my language."""
    },
    inboxStart = { if (it == 1) "1 new item from the chat" else "$it new items from the chat" },
    inboxTitle = "New from the chat",
    inboxEmpty = "Nothing new.",
    inboxFrom = { "From $it" },
    inboxFull = "Your inbox is full (50 items). Accept or discard some so the chat can leave new ones.",
    inboxAsNew = "As new workout",
    inboxUpdate = "Update workout",
    inboxDiscard = "Discard",
    inboxAccept = "Accept",
    inboxSavedAsNew = "Saved as new workout",
    inboxUpdated = "Workout updated",
    inboxInvalid = "This item can't be read.",
    diffTitle = "Changes",
    diffAdded = { "+ $it" },
    diffRemoved = { "− $it" },
    diffChanged = { name, old, new -> "$name: $old → $new" },
    diffRenamed = { old, new -> "Name: $old → $new" },
    diffNone = "No changes to the exercises.",
    diffSets = { count, text -> if (text.isBlank()) "$count×" else "$count× $text" },
    notesTitle = "Notes from the chat",
    notesEmpty = "No notes yet.",
    notesRating = { "Rating $it/10" },
    notesDelete = "Delete",
    noteForWorkout = "Note from the chat",
)

fun chatStringsFor(language: Language): ChatStrings = when (language) {
    Language.DE -> ChatGerman
    Language.EN -> ChatEnglish
}

val LocalChatStrings = staticCompositionLocalOf { ChatGerman }
