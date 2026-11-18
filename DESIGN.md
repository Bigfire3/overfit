---
name: Overfit
description: Schwarz-weißes, flächiges Trainings-Designsystem in Compose Foundation, nur Dunkelmodus.
colors:
  background: "#000000"
  foreground: "#FFFFFF"
  muted: "#98989D"
  faint: "#757579"
  hairline: "#262628"
  inverse-background: "#FFFFFF"
  inverse-foreground: "#000000"
  surface: "#1C1C1E"
  surface-pressed: "#2C2C2E"
  input-fill: "#FFFFFF1F"
  positive: "#30D158"
  positive-subtle: "#0F2A17"
  negative: "#FF6961"
  negative-subtle: "#331311"
  armed: "#FF453A"
  armed-fill: "#D32F2F"
  confirm-fill: "#1E7D34"
  accent: "#FFD60A"
  accent-subtle: "#3A3000"
typography:
  hero:
    fontFamily: "Inter"
    fontSize: "56sp"
    fontWeight: 600
    lineHeight: "62sp"
    letterSpacing: "-1.5sp"
  title:
    fontFamily: "Inter"
    fontSize: "34sp"
    fontWeight: 600
    lineHeight: "40sp"
    letterSpacing: "-0.8sp"
  body:
    fontFamily: "Inter"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "Inter"
    fontSize: "17sp"
    fontWeight: 500
    lineHeight: "24sp"
  caption:
    fontFamily: "Inter"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: "18sp"
  number:
    fontFamily: "Inter"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
rounded:
  field: "8dp"
  row: "12dp"
  panel: "16dp"
  block: "20dp"
  card: "24dp"
  button: "24dp"
  sheet: "24dp"
spacing:
  xs: "4dp"
  s: "8dp"
  m: "16dp"
  l: "24dp"
  xl: "40dp"
  xxl: "64dp"
components:
  button-primary:
    backgroundColor: "{colors.inverse-background}"
    textColor: "{colors.inverse-foreground}"
    rounded: "{rounded.button}"
    height: "64dp"
    padding: "0 24dp"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.button}"
    height: "64dp"
  button-small:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.button}"
    height: "44dp"
    padding: "0 16dp"
  button-armed:
    backgroundColor: "{colors.armed-fill}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.button}"
  button-armed-complete:
    backgroundColor: "{colors.confirm-fill}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.button}"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
    padding: "16dp"
  block:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.block}"
    padding: "8dp"
  set-row:
    backgroundColor: "{colors.surface-pressed}"
    rounded: "{rounded.row}"
    height: "56dp"
  set-field:
    backgroundColor: "{colors.input-fill}"
    textColor: "{colors.foreground}"
    typography: "{typography.number}"
    rounded: "{rounded.field}"
    height: "48dp"
  panel:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
  chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted}"
    typography: "{typography.caption}"
    height: "40dp"
  chip-selected:
    backgroundColor: "{colors.inverse-background}"
    textColor: "{colors.inverse-foreground}"
---

# Design System: Overfit

## Overview

**Creative North Star: "Kreide auf Schwarz"**

Overfit sieht aus wie eine Tafel im Studio: schwarzer Grund, weiße Zahlen, nichts, was ablenkt. Hierarchie entsteht nur
aus Schriftgröße, Gewicht, drei Grautönen und Flächen. Farbe ist Bedeutung und taucht sonst nicht auf: grün = erledigt
oder besser, rot = löschen oder schlechter, gelb (Kreidegelb, die gelbe Kreide an der Tafel) = läuft gerade jetzt.
Was sich ändert, bewegt sich: Screens haben Orte, Flächen folgen dem Finger, nichts springt (siehe „Motion“). Die App ist dicht, wo trainiert wird (laufendes
Training: Satzzeilen, große Zahlenfelder), und luftig, wo gelesen wird (Kalendertag, Coach).

Das System ist in `shared/.../ui/theme/Theme.kt` (Tokens) und `ui/components/` (Komponenten) implementiert; dieses Dokument
beschreibt den gebauten Stand. Bewusst abgelehnt: Hell-Modus, Theme-Wahl, Material, Rahmen, Schatten, Ripple, Popups für
Bestätigungen, unterstrichene Text-Links.

**Key Characteristics:**
- Nur Dunkelmodus, schwarz-weiß, Inter.
- Abgrenzung nur über Flächen (`surface` auf Schwarz, verschachtelt `surface-pressed`), nie über Rahmen.
- Ein heller Hauptknopf pro Screen, schwebend über dem Inhalt.
- Doppeltipp statt Bestätigungsdialog (rot = löschen, grün = abschließen).
- Jedes tippbare Element gibt sofort Rückmeldung (Scale, kein Ripple).
- Nichts springt: Was erscheint, verschwindet, wächst oder den Platz wechselt, tut es mit einer Feder (`Motion`).
- Eine Farbe für „jetzt“ (Kreidegelb): laufende Pause, laufendes Training, der Satz, der dran ist.

## Colors

Monochrom mit zwei Signalfarben, die nur Daten und Bestätigungen tragen.

### Primary
- **Kreideweiß** (foreground): Text, Icons, gefüllter Hauptknopf (als inverse-background), Heute-Kreis im Kalender,
  Fortschrittsbalken.

### Neutral
- **Tafelschwarz** (background): Grund jedes Screens, Trennlinien in Panels (die schwarzen 1-dp-Lücken).
- **Grafit** (surface): Karten, Panels, Blöcke, Sheets, Zweitknöpfe, Chips.
- **Grafit gedrückt** (surface-pressed): Fläche auf einer Fläche (Satzzeile im Block, Knopf im Panel), gedrückte Zeile,
  leerer Schalter/Checkbox.
- **Grau** (muted): Sekundärtext, Werte in Zeilen, inaktive Tabs, Chevrons. Erreicht ≥ 4,5 : 1 auf allen drei Flächen.
- **Hellgrau-schwach** (faint): Platzhalter, leere Kalendertage, Zusatzinfos auf Schwarz, Griffleiste. ≥ 4,5 : 1 auf
  Schwarz, ≥ 3 : 1 auf `surface`.
- **Haarlinie** (hairline): Trennlinie über der Tab-Leiste und dem Pausentimer, Spur von Fortschrittsbalken.
- **Eingabe-Schleier** (input-fill): durchscheinendes Weiß hinter Zahlenfeldern, damit sie auf grauer und grüner Zeile
  gleich hervortreten.

### Signal (nur Bedeutung)
- **Grün** (positive / positive-subtle): Bestwert, besser als zuvor; **confirm-fill** ist die abgehakte Satzzeile und der
  scharf geschaltete „Abschließen“-Knopf.
- **Rot** (negative / negative-subtle): schlechter als zuvor, destruktiver Menüeintrag, Text auf rotem Grund.
  **armed** ist das rote Icon eines scharf geschalteten Mülleimers, **armed-fill** die Fläche eines scharf geschalteten
  Text-Knopfs und der Hintergrund beim Satz-Wegwischen (Weiß darauf ≥ 4,5 : 1).

### Named Rules
### Jetzt (nur Bedeutung)
- **Kreidegelb** (accent / accent-subtle): was in diesem Moment läuft oder dran ist. Der Countdown der Pause, der gefüllte
  Teil der Pausen-Insel (accent-subtle), die Nummer des Satzes, der als Nächstes geloggt wird, der Countdown in der
  Mini-Leiste, der ablaufende Kopplungscode im Chat (Countdown) und der ruhende Punkt neben „denkt nach …“ im
  Coach-Chat. Regel: **eine Bedeutung, nicht eine Stelle** – Gelb heißt immer „jetzt“, auch wenn in einer Pause
  Countdown und Satznummer gleichzeitig gelb sind (beide sind „jetzt“: die laufende Pause und der Satz danach). Nie
  Auswahl (bleibt weiß), nie Knopffläche, nie Deko, nie „heute“ (der Heute-Kreis im Kalender bleibt weiß).

### Named Rules
**The Meaning Rule.** Kein Farbton dient der Dekoration oder Marke. Drei Farben, drei Bedeutungen: grün = erledigt/besser,
gelb = jetzt, rot = löschen/schlechter. Wer etwas einfärben will, das keine dieser Bedeutungen hat, lässt es weiß oder grau.

**The Contrast Floor Rule.** Text ≥ 4,5 : 1 auf seiner Fläche, Platzhalter ≥ 3 : 1. Abgesichert in `ContrastTest` und
`ButtonContrastTest`; ein neuer Farbton braucht dort einen Eintrag.

## Typography

**Display Font:** Inter (SemiBold)
**Body Font:** Inter (Regular/Medium)

**Character:** Eine einzige, neutrale Grotesk in wenigen Größen. Zahlen sind das wichtigste Material der App: die großen
Zahlstile (Hero, Title, Number) und jeder laufende Wert (Countdown, Trainingsdauer) stehen in Tabellenziffern (`tnum`),
damit sie beim Hochzählen nicht springen. Fließtext behält proportionale Ziffern.

### Hierarchy
- **Hero** (600, 56sp, 62sp): Countdown im großen Pausentimer, Kennzahlen im Abschluss.
- **Title** (600, 34sp, 40sp, −0,8sp): eine Überschrift pro Screen.
- **Body** (400, 17sp, 24sp): Fließtext, Zeilen.
- **Label** (500, 17sp, 24sp): Namen in Karten, Knopftext (Knöpfe: 18sp fett, kompakt 16sp halbfett).
- **Caption** (500, 13sp, 18sp): Zweitzeilen, Abschnittstitel in Versalien, Chips, Tab-Beschriftung.
- **Number** (600, 20sp, 26sp, tnum): Zahlenfelder der Satzzeilen.

### Named Rules
**The Tabular Rule.** Was sich sichtbar ändert, steht in Tabellenziffern (`Theme.type.tabular(…)` für Body/Label/Caption).
Ein Timer, der beim Zählen wackelt, ist ein Fehler. Knopftexte mit Ziffern („±15 s“) bleiben proportional, sonst wird es
auf 360 dp eng.

**The Shrink-Before-Cut Rule.** Knopftexte schrumpfen bis 12sp, bevor sie gekürzt werden.

## Layout

Ein Spaltenlayout mit 24 dp Seitenrand (`Screen`), Inhalte respektieren `safeDrawing` und die Tastatur (`imePadding`).
Abstände nur aus der Skala 4/8/16/24/40/64 dp. Karten einer Liste stehen 16 dp (Übungsblöcke 8 dp) auseinander,
Abschnittstitel haben 40 dp Luft nach oben. Scrollende Screens lassen den Inhalt unter dem schwebenden Hauptknopf
durchlaufen (sanfter Verlauf, Endabstand in Fußhöhe). Geprüft auf 360 dp und 411 dp Breite und mit 1,3-facher Schrift;
`ListRow` bricht das Steuerelement in die nächste Zeile, statt das Label mitten im Wort zu trennen. Tablets und
Querformat sind nicht Ziel (iPhone-only, Android-Telefone).

## Elevation & Depth

Keine Schatten (auf Schwarz unsichtbar), keine Rahmen (`NoBordersTest`). Tiefe entsteht durch Tonstufen: Schwarz →
`surface` → `surface-pressed` → `input-fill`. Overlays (Sheet, Popup) liegen über einem 60-%-Schwarz-Scrim.

### Named Rules
**The Flat Stack Rule.** Jede Ebene ist eine Stufe heller als die darunter. Wer eine weitere Ebene braucht, nimmt die
nächste Fläche, nie eine Linie.

## Shapes

Großzügig gerundet, konzentrisch verschachtelt: der äußere Radius ist der innere plus Innenabstand. Übungsblock 20 dp mit
8 dp Innenabstand um Satzzeilen mit 12 dp; Zahlenfelder 8 dp in der Zeile. Knöpfe, Karten, Sheets und Popups 24 dp,
Panels 16 dp, Chips und Segmente als Pille, Übungsbilder ein Sechstel ihrer Kantenlänge.

## Motion

Bewegung ist Teil des Systems wie Farbe und Größe: nur aus den Tokens in `ui/theme/Motion.kt`, nur über die Bausteine in
`ui/components/MotionKit.kt`. Ziel ist der Fluss, den man von iOS kennt, ohne dass die App langsamer wird: Rückmeldung
beim Aufsetzen des Fingers, Federn statt fester Zeiten, alles lässt sich mitten in der Bewegung umlenken, nichts blockiert
die Eingabe.

### Tokens (`Motion`)
- **snappy** (Feder, ~180 ms): kleine Zustandswechsel. Füllungen, Schalter, Icons, Loslassen eines Knopfs.
- **smooth** (Feder, ~300 ms): Layout. Etwas wächst, schrumpft, rückt nach; Sheets.
- **screen** (Feder, ~250 ms bis benutzbar): ein ganzer Screen kommt oder geht.
- **bouncy** (Feder mit einem kleinen Überschwinger): selten, für den Moment, in dem etwas geschafft ist (Haken eines Satzes).
- **fadeIn 160 ms / fadeOut 110 ms**: Ein- und Ausblenden an Ort und Stelle; was geht, geht schneller.
- **tick 200 ms linear**: alles, was am 250-ms-Takt des laufenden Trainings hängt (Fortschritt der Pause).

### Bausteine (`MotionKit.kt`)
- **`Reveal(visible)`**: Block in einer Spalte kommt/geht (Hinweis, Rückgängig-Zeile, Inhalt einer Klappkarte). Wächst und
  schrumpft, der Rest rückt nach. `RevealNotNull(value)` hält den letzten Wert, während er geht; `RevealInRow` seitlich.
- **`Appear(visible)`**: kleines Ding taucht an Ort und Stelle auf (Badge, Icon, schwebender Knopf): Blende + Skalierung.
  `PopIn` dasselbe mit Überschwinger.
- **`Swap(state)`**: Inhalt wird an Ort und Stelle ersetzt (Text, Icon, Laden → Inhalt, Schritt → Schritt): alt blendet
  aus, neu ein, die Größe geht weich mit.
- **`AnimatedItems(items, key)`**: Kinder einer normalen Spalte, die dazukommen und wegfallen (Sätze einer Übung).
  **`motionItem()`** dasselbe für Einträge einer Lazy-Liste (braucht stabile `key`s).
- **`RollingText(text)`**: Zahl, die sich sichtbar ändert (Countdown, Zähler): nur die geänderten Ziffern rollen.
  **`rememberCountUp(target)`**: Zahl zählt einmal hoch, wenn sie erscheint (Abschluss).
- **`motionColor(target)`**: Farbe folgt ihrem Zustand weich (gewählter Chip, scharfer Knopf).
  **`ProgressLine(progress)`**: Balken wandert zum neuen Wert.
- **`pressScale` / `pressableRow`**: Druck-Rückmeldung. Ein schneller Tipp geht nie verloren (der Weg nach unten läuft
  immer zu Ende).
- **`rememberHaptics()`**: Haptik nach Bedeutung: `tick` (Auswahl wandert), `toggle` (Schalter), `press` (Hauptaktion),
  `arm` (Doppeltipp scharf), `confirm` (erledigt), `threshold` (Ziehen überschreitet den Punkt, ab dem Loslassen wirkt),
  `reject`.

### Screens (`ScreenMotion.kt`)
- **Tiefer / zurück:** Der neue Screen kommt von der Seite und liegt oben, der alte rückt ein Viertel zur Seite und wird
  dunkler. Zurück ist dasselbe rückwärts; die Zurück-Geste führt den Screen mit dem Finger (linear), losgelassen läuft
  er weiter oder kehrt um.
- **Ebene:** Das laufende Training steigt von unten über den Screen (der tritt etwas zurück) und sinkt beim Minimieren
  dorthin, wo die Mini-Leiste sitzt. Foto-Vollbild und Coach-Story wachsen aus dem Screen heraus.
- **Tabs:** Wechsel ohne Übergang zwischen den Screens; der neue Inhalt kommt kurz aus leichter Abdunklung, die graue
  Marke in der Tab-Leiste gleitet zum neuen Tab. Die Leisten gehören zum Tab-Screen und wandern mit ihm.
- **Sheets:** federn von unten herein, der Scrim dunkelt mit. Sie folgen dem Finger (Griff, Inhalt am Anfang, Fuß);
  weit genug gezogen oder geschnippt schließen sie, sonst federn sie zurück, mit der Geschwindigkeit des Fingers.

### Named Rules
**The No-Jump Rule.** Ein `if (x) { … }`, das Inhalt in einer Spalte ein- oder ausschaltet, ist ein Sprung. Dafür gibt es
`Reveal`, `Appear`, `Swap`, `AnimatedItems`.

**The Rest Rule.** Keine Endlos-Animationen, keine Animation, die sich selbst neu anstößt. Was am Trainingstakt (250 ms)
hängt, nutzt `Motion.tick` und ist vor dem nächsten Takt fertig. Sonst kommt die Oberfläche nie zur Ruhe (Akku, und die
UI-Tests warten ewig).

**The No-Choreography Rule.** Ein Screen, der sich öffnet, ist da: keine gestaffelt einfliegenden Listen, nichts, was
länger als ~350 ms dauert, nichts, das die Eingabe sperrt.

**The Draw-Phase Rule.** Was pro Frame läuft, wird beim Zeichnen gelesen (`graphicsLayer { }`, `drawBehind { }`,
`color = { }`), nicht in der Komposition. Eine Animation darf keine Liste neu zusammensetzen.

**The Reduced-Motion Rule.** Mit „Bewegung reduzieren“ (iOS) bzw. abgeschalteten Animationen (Android) werden
Screen-Wechsel zu kurzen Überblendungen, rollende Ziffern wechseln einfach (`LocalReducedMotion`).

## Components

### Buttons
- **Shape:** großer Radius (24 dp), kein Rand.
- **Primary:** weiß mit schwarzem, fettem Text, 64 dp hoch, Text und optionales Icon mittig. Einer pro Screen, schwebend
  über dem Inhalt.
- **Secondary / Small:** Grafit mit weißem Text; kompakt 44 dp sichtbar, 48 dp Tippfläche. Im Panel eine Stufe heller.
- **Press:** `pressScale` 0,96 (Knöpfe), 0,98 (Karten), 0,88 (Icon-Knöpfe), 70 ms rein (`Motion.press`, `MIN_PRESS_MS`), Feder
  zurück (`Motion.pressRelease`, leicht federnd); Zeilen (`pressableRow`): graue Fläche sofort da, blendet in 260 ms aus
  (`Motion.rowRelease`). Kein Ripple.
- **Confirm (Doppeltipp):** erster Tipp füllt rot (`armed-fill`) bzw. grün (`confirm-fill`) mit weißem Text, zweiter Tipp in
  3 s führt aus; Tipp daneben, Scrollen oder Zeitablauf entschärfen.
- **Disabled:** 30 % Deckkraft, kein Scale.

### Chips
- **Style:** Pille, Grafit, Caption in Grau; ausgewählt invertiert (weiß, schwarzer Text). 40 dp sichtbar, 48 dp Tippfläche.

### Cards / Containers
- **FloatingCard:** eigenständiges Listenelement, Grafit, 24 dp, ganz tippbar.
- **Panel / PanelSection:** zusammengehörige Zeilen (Einstellungen), Grafit, 16 dp, Zeilen durch schwarze 1-dp-Lücken
  getrennt, kurze Erklärung als `Hint` darunter.
- **Block:** eine Übung im Training/Editor, 20 dp, 8 dp Innenabstand, Kopf `ExerciseCardHeader` (Bild links).

### Inputs / Fields
- **SetField:** große Tabellenziffern mittig auf `input-fill`, 48 dp hoch; Platzhalter = Zielwert in Grau, Cursor beim
  Fokussieren ans Ende.
- **BigInput / RowInput / SearchInput:** randlos, Platzhalter in `faint`. Jede Textfläche bekommt `textInputArea()`, damit
  ein Tipp daneben den Cursor löscht.
- **SwitchRow:** Schalter ohne Text (links aus, rechts an), ganze Zeile tippbar.

### Navigation
- **Tab-Leiste:** vier Tabs mit Linien-Icon und Caption, aktiv weiß, inaktiv grau; hinter dem aktiven Tab liegt eine
  graue Marke (`surface`), die beim Wechsel zum neuen Tab gleitet. Ganze Spalte (≥ 48 dp hoch) ist Tippfläche.
- **Screens:** siehe „Motion → Screens“. Taps werden nur in den ersten 300 ms eines Wechsels geschluckt (Doppeltipp).
- **BackHeader:** Zurück links, Aktionen (Teilen, Bearbeiten, Löschen) oben rechts als 48-dp-Icon-Knöpfe.

### Rest Timer (Signature Component)
Eine Insel (`surface`, 24 dp, 60 dp hoch) über dem Hauptknopf des laufenden Trainings, nur während einer Pause: links
`−15` · Countdown in Title-Größe und Kreidegelb (rollende Ziffern) · `+15` als runde Knöpfe, rechts „Überspringen“ über
die restliche Breite. Die Insel füllt sich von links mit `accent-subtle`, so wie die Pause vergeht. Ein Tipp auf den
Countdown scrollt zum Satz, der dran ist. Kein Vergrößern (Owner: nutzlos), nie Vollbild. Was als Nächstes kommt, steht
als zweite Zeile im Hauptknopf („Nächsten Satz loggen“ / „Bench Press · Satz 2 · 60 kg × 8“), auch ohne Pause. Solange
die Tastatur offen ist, weicht der Hauptknopf (abgehakt wird an der Zeile), die Insel bleibt.

## Do's and Don'ts

### Do:
- **Do** Flächen stufen (`surface` → `surface-pressed`), um zu gruppieren.
- **Do** jedem tippbaren Element mindestens 48 × 48 dp Tippfläche geben, auch wenn das Sichtbare kleiner ist.
- **Do** `pressScale` auf Knöpfe und tippbare Karten, `pressableRow` (graue Druckfläche) auf Zeilen in Panels.
- **Do** Leere, Laden und Fehler je Screen gestalten: Leere mit nächstem Schritt, Fehler mit Weg zur Behebung.
- **Do** alle Texte in `Strings.kt` (DE + EN).
- **Do** jeden Wechsel von Inhalt, Größe, Farbe oder Ort über die Bausteine aus „Motion“ führen.
- **Do** Kreidegelb nur für das, was gerade läuft oder dran ist.

### Don't:
- **Don't** `.border(`, `BorderStroke` oder `.shadow(` verwenden (Ausnahme: Sauna-Ring-Symbol).
- **Don't** Material-Komponenten, Ripple oder Popups für Bestätigungen verwenden.
- **Don't** Farbe als Deko oder als einziges Zustandsmerkmal einsetzen.
- **Don't** `faint` für Text auf `surface-pressed` verwenden.
- **Don't** mehr als einen `PrimaryButton` pro Screen zeigen.
- **Don't** eigene Dauern, Easings oder `tween(…)`-Werte in Screens schreiben; nur `Motion` und die Bausteine.
- **Don't** Endlos-Animationen (`rememberInfiniteTransition`) oder gestaffelte Auftritte beim Öffnen eines Screens.
