package app.overfit.i18n

/** Texts for sharing, importing, sample templates and the Duo teaser (part of [Strings.share]). */
data class ShareStrings(
    val shareTemplate: String,
    val shareLink: String,
    val showQr: String,
    val shareFile: String,
    val shareMessage: (name: String, link: String) -> String,
    val qrTitle: String,
    val qrHint: String,
    val qrTooLarge: String,
    val sharePreviewTitle: String,
    val sharePreviewCounts: (exercises: Int, sets: Int) -> String,
    val sharePreviewValues: (example: String) -> String,
    val sharePreviewNoValues: String,
    val importPickFile: String,
    val importFileFailed: String,
    val importIsBackup: String,
    val linkCopied: String,
    val copyLink: String,

    val importTitle: String,
    val importIntro: String,
    val importFromClipboard: String,
    val scanQr: String,
    val scanHint: String,
    val scanCameraDenied: String,
    val scanIosHint: String,
    val importFileHint: String,
    val previewTitle: String,
    val previewCounts: (exercises: Int, newOnes: Int) -> String,
    val previewNewTag: String,
    val previewSets: (Int) -> String,
    val previewNewNote: (List<String>) -> String,
    val importAccept: String,
    val imported: (String) -> String,
    val importEmpty: String,
    val importNotOverfit: String,
    val importTooNew: String,
    val importUnsupportedKind: String,
    val importInvalid: String,
    val importTooLarge: String,
    val importEmptyTraining: String,

    val aiTemplateCopy: String,
    val aiTemplateCopied: String,
    val aiTemplatePrompt: (example: String) -> String,


    val duoSoon: String,
    val duoTitle: String,
    val duoBody: String,
    val duoNote: String,
)

val ShareGerman = ShareStrings(
    shareTemplate = "Teilen",
    shareLink = "Link teilen",
    showQr = "QR-Code zeigen",
    shareFile = "Als Datei teilen",
    shareMessage = { name, link -> "Mein Training „$name“ für Overfit:\n$link" },
    qrTitle = "QR-Code",
    qrHint = "Scannen mit Overfit: Training → „+“ → Importieren → QR-Code scannen. Die normale Kamera-App kann diesen kompakten Code nicht öffnen – dafür gibt es den Link.",
    sharePreviewTitle = "Das wird geteilt",
    sharePreviewCounts = { e, s -> (if (e == 1) "1 Übung" else "$e Übungen") + " · " + (if (s == 1) "1 Satz" else "$s Sätze") },
    sharePreviewValues = { ex -> "Der Empfänger sieht deine Zielwerte, z. B. $ex." },
    sharePreviewNoValues = "Nur Übungen und Satzanzahl, ohne Gewichte und Wiederholungen.",
    importPickFile = "Datei auswählen",
    importFileFailed = "Diese Datei konnte nicht gelesen werden.",
    importIsBackup = "Das ist ein Backup, kein Workout. Du stellst es unter Profil → Backup wieder her.",
    qrTooLarge = "Dieses Workout ist für einen QR-Code zu groß. Teile es als Link oder Datei.",
    linkCopied = "Link kopiert",
    copyLink = "Link kopieren",

    importTitle = "Workout importieren",
    importIntro = "Aus Link, QR-Code, Datei oder Zwischenablage, z. B. von ChatGPT, Claude oder Gemini.",
    importFromClipboard = "Aus Zwischenablage importieren",
    scanQr = "QR-Code scannen",
    scanHint = "Halte die Kamera auf den QR-Code.",
    scanCameraDenied = "Ohne Kamerazugriff kann Overfit keinen QR-Code scannen. Du kannst ihn auch mit der Kamera-App scannen.",
    scanIosHint = "Scanne den QR-Code mit der Kamera-App deines iPhones. Er öffnet Overfit direkt.",
    importFileHint = "Eine .overfit-Datei kannst du auch in Dateien, Downloads oder WhatsApp antippen und mit Overfit öffnen.",
    previewTitle = "Vorschau",
    previewCounts = { n, new -> if (new == 0) "$n Übungen" else "$n Übungen, davon $new neu" },
    previewNewTag = "neu",
    previewSets = { if (it == 1) "1 Satz" else "$it Sätze" },
    previewNewNote = { names -> "Wird neu angelegt: ${names.joinToString()}" },
    importAccept = "Übernehmen",
    imported = { "„$it“ ist jetzt in deinen Workouts" },
    importEmpty = "Die Zwischenablage ist leer.",
    importNotOverfit = "Hier steckt kein Overfit-Training drin.",
    importTooNew = "Dieses Training stammt aus einer neueren Version. Bitte aktualisiere Overfit.",
    importUnsupportedKind = "Dieses Format kann diese Version noch nicht importieren.",
    importInvalid = "Das Training ist beschädigt oder unvollständig.",
    importTooLarge = "Das Training ist zu groß.",
    importEmptyTraining = "Keine Übung ließ sich zuordnen.",

    aiTemplateCopy = "KI-Prompt kopieren",
    aiTemplateCopied = "Prompt kopiert. Füge ihn in ChatGPT, Claude oder Gemini ein und importiere die Antwort danach aus der Zwischenablage.",
    aiTemplatePrompt = { example ->
        """Du bist mein Trainingsplaner. Erstelle mir ein Workout für die Tracking-App Overfit.

Falls ich dir noch nichts zu meinem Ziel (z. B. Muskelaufbau, Kraft, Fettabbau), meinem Equipment (Fitnessstudio, Kurzhanteln, nur Körpergewicht …) und der Zeit pro Training gesagt habe, frag mich das zuerst und warte auf meine Antwort.

Gib danach als Ausgabe genau einen ```json-Block aus (nur diesen einen Block). Format:

```json
$example
```

Regeln:
- "v" ist immer 1, "kind" immer "template". "name" ist der Name des Workouts, "exercises" sind die Übungen in Trainingsreihenfolge (5 bis 8, wenn ich nichts anderes will).
- Übungen gibst du nur per "name" an, mit dem gängigen englischen Namen (z. B. "Bench Press", "Lat Pulldown", "Romanian Deadlift").
- "sets" enthält je Satz die Zielwerte passend zur Übung: Gewicht und Wiederholungen → "kg" und "reps"; nur Wiederholungen (z. B. Klimmzug, Liegestütz) → "reps"; Haltezeit (z. B. Plank) → "sec" in Sekunden. Nur Zahlen, ohne Einheit. Gewichte immer in kg. Kennst du meine Stärke nicht, lass "kg" weg.
- "restSec" ist die Pause nach jedem Satz in Sekunden (Grundübungen 120 bis 180, Isolationsübungen 60 bis 90).
- Optional: "note" je Übung für einen kurzen Ausführungshinweis.
- Höchstens zwei Sätze Erklärung außerhalb des Blocks."""
    },


    duoSoon = "Duo · bald",
    duoTitle = "Duo",
    duoBody = "Zwei Handys trainieren gemeinsam: Ihr hakt Sätze synchron ab und teilt euch einen Pausentimer, auch wenn ihr an verschiedenen Geräten trainiert.",
    duoNote = "Duo ist noch nicht verfügbar. Es werden keine Daten dafür erhoben.",
)

val ShareEnglish = ShareStrings(
    shareTemplate = "Share",
    shareLink = "Share link",
    showQr = "Show QR code",
    shareFile = "Share as file",
    shareMessage = { name, link -> "My workout \"$name\" for Overfit:\n$link" },
    qrTitle = "QR code",
    qrHint = "Scan it with Overfit: Workouts → “+” → Import → Scan QR code. The regular camera app cannot open this compact code – use the link for that.",
    sharePreviewTitle = "What gets shared",
    sharePreviewCounts = { e, s -> (if (e == 1) "1 exercise" else "$e exercises") + " · " + (if (s == 1) "1 set" else "$s sets") },
    sharePreviewValues = { ex -> "The recipient sees your target values, e.g. $ex." },
    sharePreviewNoValues = "Only exercises and number of sets, without weights and reps.",
    importPickFile = "Choose file",
    importFileFailed = "This file could not be read.",
    importIsBackup = "This is a backup, not a workout. Restore it under Profile → Backup.",
    qrTooLarge = "This workout is too large for a QR code. Share it as a link or file.",
    linkCopied = "Link copied",
    copyLink = "Copy link",

    importTitle = "Import workout",
    importIntro = "From a link, QR code, file or the clipboard, e.g. from ChatGPT, Claude or Gemini.",
    importFromClipboard = "Import from clipboard",
    scanQr = "Scan QR code",
    scanHint = "Point the camera at the QR code.",
    scanCameraDenied = "Without camera access Overfit can't scan a QR code. You can also scan it with the camera app.",
    scanIosHint = "Scan the QR code with your iPhone's camera app. It opens Overfit directly.",
    importFileHint = "You can also tap an .overfit file in Files, Downloads or WhatsApp and open it with Overfit.",
    previewTitle = "Preview",
    previewCounts = { n, new -> if (new == 0) "$n exercises" else "$n exercises, $new new" },
    previewNewTag = "new",
    previewSets = { if (it == 1) "1 set" else "$it sets" },
    previewNewNote = { names -> "Will be created: ${names.joinToString()}" },
    importAccept = "Add workout",
    imported = { "\"$it\" is now in your workouts" },
    importEmpty = "The clipboard is empty.",
    importNotOverfit = "There is no Overfit workout in here.",
    importTooNew = "This workout comes from a newer version. Please update Overfit.",
    importUnsupportedKind = "This version can't import this format yet.",
    importInvalid = "The workout is damaged or incomplete.",
    importTooLarge = "The workout is too large.",
    importEmptyTraining = "No exercise could be matched.",

    aiTemplateCopy = "Copy AI prompt",
    aiTemplateCopied = "Prompt copied. Paste it into ChatGPT, Claude or Gemini, then import the answer from the clipboard.",
    aiTemplatePrompt = { example ->
        """You are my workout planner. Create a workout for the tracking app Overfit.

If I haven't told you my goal (e.g. muscle gain, strength, fat loss), my equipment (gym, dumbbells, bodyweight only …) and the time per session yet, ask me first and wait for my answer.

Then answer with exactly one ```json block (only that one block). Format:

```json
$example
```

Rules:
- "v" is always 1, "kind" is always "template". "name" is the workout's name, "exercises" are the exercises in training order (5 to 8 unless I say otherwise).
- Name each exercise only via "name", using the common English name (e.g. "Bench Press", "Lat Pulldown", "Romanian Deadlift").
- "sets" holds the target values per set, matching the exercise: weight and reps → "kg" and "reps"; reps only (e.g. pull-up, push-up) → "reps"; hold time (e.g. plank) → "sec" in seconds. Numbers only, no units. Weights always in kg. If you don't know my strength, leave "kg" out.
- "restSec" is the rest after each set in seconds (compound lifts 120 to 180, isolation 60 to 90).
- Optional: "note" per exercise for a short form cue.
- At most two sentences of explanation outside the block."""
    },


    duoSoon = "Duo · soon",
    duoTitle = "Duo",
    duoBody = "Two phones train together: you tick off sets in sync and share one rest timer, even on different devices.",
    duoNote = "Duo isn't available yet. No data is collected for it.",
)
