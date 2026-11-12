# Fortschritt

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup, Gerüst, CI, erstes APK | erledigt |
| 0b | Kotlin Multiplatform, Schwarz-Weiß-Design, DE/EN, Onboarding, eigener API-Key, iOS-Hülle | erledigt |
| 1 | Übungskatalog, eigene Übungen, Vorlagen | erledigt |
| 2 | Workout-Modus, Pausentimer | erledigt |
| 4 | KI-Coach | erledigt (Session G): Auswertung, Plan-Diff, Gedächtnis, Fortschritt, Warteschlange, Demo |
| 3 | Beenden-Flow, Foto, Verlauf, Backup | erledigt (Session F) |
| 5 | Feinschliff | Gesamttest „Ein Tag mit Overfit“ (Robolectric) und Morgen-Testliste fertig (Session I); Feinschliff laufend |
| 6 | Store-Release (App Store, Play Store) | offen |
| 7 | Credits-System (Coach-Guthaben) | Backend + API-Vertrag fertig; App-Anbindung (Guthaben, Demo) fertig; Modelle Gemini, Claude, Workers AI über AI Gateway (Session X), echte Modellnamen und volle Kostentransparenz (Session Y); Web-Kauf mit Guthaben-Codes im Testmodus (Session Z, seit Session AB über Stripe, Web 1:1, Store-Aufpreis offen, Aufschlag 28 %); Store-Kauf und echter Web-Kauf offen (braucht Konten, Rechtstexte) |
| 8 | Trainings teilen (Link, Share-Sheet, QR, Import) | erledigt (Vorlagen: Session B, abgeschlossene Trainings: Session F) |
| 9 | ~~Einladen per QR mit Startguthaben~~ | entfernt (7.11.2026, Fabian): kein Empfehlungsprogramm, kein Startguthaben, Guthaben verfällt nie; aus App, Backend, Webseite und Doku entfernt |
| 10 | KI-Chat-Verbindungen (Claude, ChatGPT, Gemini per MCP) | 10a Backend + Konzept fertig (Nacht 3.11.), 10b App-Seite fertig (Session H, ohne Foto-Upload und Coach-Gedächtnis – kommen nach Phase 3/4); Deployment des Backends offen |

## Alternativen (A/B) im Workout (Session BI, 8.11.)
**Erledigt:** Im Workout ⋮ einer Übung → „Alternative hinzufügen“; die Alternative hat eigene Sätze. Im Training zeigt die Karte Chips
mit den Varianten; voreingestellt ist die, die beim letzten Training dieses Workouts nicht dran war (A/B-Wechsel, reihum bei mehr).
DB v15 (`template_exercise.variant`), Teilen/Export (`alt`), Backup und Coach/KI tragen Alternativen; alte Dateien bleiben lesbar.
Screenshots: `docs/screenshots/workout-alternativen/`.

### Bitte auf dem Handy testen (Alternativen)
1. Workout öffnen, bei einer Übung ⋮ → „Add alternative“ → Übung wählen: In derselben Karte steht „Alternative“ mit eigenen Sätzen.
2. Starten: Die Karte zeigt zwei Chips, die Hauptübung ist gewählt. Auf die Alternative tippen: Name und Sätze wechseln.
3. Training mit der Hauptübung beenden, Workout erneut starten: Jetzt ist die Alternative vorgewählt (A/B-Wechsel).
4. Einen Satz abhaken: Chips verschwinden. Workout teilen und auf einem zweiten Gerät/über die Zwischenablage importieren: Alternative ist dabei.


## Kalender, Tag und Fotos (Session BJ, 8.11.)
**Erledigt:** Fabians Feedback vom 8.11. („Kreise gleich groß“, „Menüs nicht so überladen“, „möglichst wenig tippen“, Bild am Workout
oder am Tag „undurchsichtig“):
- **Fotos gehören zum Tag** (PR #100, DB v14, Migration mit Test, auch für alte Backups). Tag, Trainingsdetail und Abschluss-Screen
  zeigen dasselbe Foto-Raster mit Kacheln „Foto aufnehmen“ und „Aus Fotos wählen“. Alles landet am Tag. Alte Trainingsfotos stehen am
  Starttag ihres Trainings.
- **Kalender einheitlich** (Teil 2): Tageszahl im festen Kreis (Heute = gefüllt), quadratische Kachel, darunter eine Markierungszeile.
  Training ●, Sauna ○ und Gewicht ▬ sind alle 8 dp groß und zentriert, die Legende ist identisch. Kopfzeile: Heute, Muskeln und ⋮
  (Liste/Kalender, Fotos vergleichen).
- **Tag auf einer Seite:** Trainings als aufgeklappte Karten (Dauer, Sätze, Volumen, Sauna, je Übung Sätze und bester Satz). Ein Tipp
  auf die Karte öffnet das Detail. Darunter Fotos und Gewicht.
- Tests: Migration, altes Backup, Coach-Fotos des Tages, `CalendarUniformTest` (misst die Größen und Lage der Markierungen nach),
  `DayPathsTest`. Screenshots: `docs/screenshots/kalender-einheitlich/`.

**Offen:**
- Das Zähler-Badge „3“ auf Foto-Tagen im Raster ist bewusst weg. Falls Fabian es vermisst, käme es als Zahl in die Markierungszeile.
- Der KI-Chat-Sync schickt weiter das Aufnahmedatum und nicht den Tag.

### Bitte auf dem Handy testen (Kalender, Tag, Fotos)
1. Kalender: Alle Punkte, Ringe und Striche unter den Tagen sind gleich groß und stehen auf einer Linie. Heute ist ein weißer Kreis um die
   Zahl. Fotos liegen als quadratische Vorschau, ohne Punkte darauf.
2. Kopfzeile: nur Muskel-Icon und ⋮ (dazu „Heute“, wenn du weggescrollt hast). ⋮ → „Als Liste anzeigen“ wechselt zur Liste. ⋮ →
   „Fotos vergleichen“ startet den Vergleich (ab 2 Fotos).
3. Tag mit Training antippen: Das Training steht aufgeklappt da (Übungen mit Sätzen und bestem Satz, Sauna). Ein Tipp auf die Karte öffnet
   das Training.
4. Im Tag „Aus Fotos wählen“ bzw. „Foto aufnehmen“: Das Foto erscheint im Tag und im Trainingsdetail unter „Fotos des Tages“. Fügst du es im
   Trainingsdetail hinzu, erscheint es ebenso im Tag und im Kalender.
5. Alte Fotos (vor dem Update) sind alle noch da, Trainingsfotos am Tag ihres Trainings. Foto entfernen: × antippen (wird rot), nochmal
   tippen.
6. Training beenden → im Abschluss-Screen ein Foto hinzufügen: Es steht danach am Tag des Trainings.
7. Backup exportieren und wieder einspielen: Fotos bleiben an ihren Tagen.
8. App auf Englisch: „Photos of the day“, „More options“, „Compare photos“.

## Wenig tippen: Workout direkt bearbeiten, Start aus der Liste (Session BI, 8.11.)
**Erledigt:** Workout-Ansicht und Editor sind ein Screen: Sätze, Name, Übungen direkt änderbar, wird automatisch gespeichert
(kein Stift, kein Speichern), „Rückgängig“ nach Entfernen/Ersetzen, unten „Starten“. ▶ auf jeder Workout-Karte startet direkt.
Neues Workout öffnet sich nach „Speichern“. Vergangene Trainings bleiben Ansicht + Stift. Tipp-Zählung in DECISIONS.md.
Tests: `FewerTapsTest` (+ angepasste UI-Tests). Screenshots: `docs/screenshots/workout-direkt-bearbeiten/`.

### Bitte auf dem Handy testen (Wenig tippen)
1. Workouts-Tab: rechts auf jeder Karte ein weißer ▶; antippen startet das Training sofort.
2. Karte antippen: Workout mit allen Sätzen als Felder, oben Teilen/Löschen, unten „Start“. Gewicht ändern, Zurück, wieder öffnen: Wert ist gespeichert.
3. Wert ändern und direkt „Start“: Training startet mit dem neuen Wert.
4. ⋮ → „Remove“ bei einer Übung: unten „Exercise removed · Undo“; Undo bringt sie zurück.
5. „+“ → neues Workout erstellen → Save: das neue Workout ist offen, „Start“ unten.

## Übung auslassen, „Training beenden“ unten (Session BI, 8.11.)
**Erledigt:** Im laufenden Training ⋮ einer Übung → „Heute auslassen“: Karte eingeklappt und gedimmt („Ausgelassen“), „Nicht auslassen“
macht es rückgängig. Offene Sätze ausgelassener Übungen zählen nicht als offen. Ist nichts mehr offen, wird der schwebende Knopf unten
zu „Training beenden“ (Doppeltipp, grün beim ersten Tipp). Gespeichert bleibt die Übung als ausgelassen markiert (DB v13), das Workout
behält sie unverändert; Trainingsdetail, Beenden-Screen, Coach und KI-Chat sehen „ausgelassen“. Tests: `SkipExerciseTest`,
`SkipAndFinishTest`, `MigrationTest.migration12to13…`, `WorkoutFlowTest`. Screenshots: `docs/screenshots/training-auslassen/`.

### Bitte auf dem Handy testen (Auslassen, Finish)
1. Workout starten, bei einer Übung ⋮ → „Skip today“: Karte wird eine gedimmte Zeile „Skipped“, oben zählt „x/y done“ ohne ihre Sätze.
2. „Don't skip“ antippen: Übung ist wieder normal mit allen Sätzen.
3. Alle übrigen Sätze abhaken (oder „Log next set“): Unten steht jetzt „Finish workout“; erster Tipp färbt grün, zweiter beendet.
4. Beenden-Screen zeigt „1 exercise skipped“; Kalender → Training: Übung mit „Skipped“. Das Workout enthält die Übung weiterhin.

## Einladungen, Startguthaben und Verfall entfernt (7.11.2026)
**Erledigt:** Fabians Entscheidung (Zitat in DECISIONS.md): Das Empfehlungsprogramm ist komplett aus der App (Android und iOS) entfernt:
„Freunde einladen“, „Einladung einlösen“, QR/Link `overfit://invite`, Reveal-Animation, geschenkte Auswertungen (Zähler, Hinweise,
„geschenkt“ unter Antworten und in der Abrechnung), Gekauft/geschenkt-Aufteilung, „gültig bis“, Erwähnung in der Coach-Story,
Geräteprüfung (Play Integrity / App Attest, inkl. Bibliothek). Kein Startguthaben, Guthaben verfällt nie. Alte Buchungen erscheinen
neutral als „Gutschrift“/„Ausbuchung“. Doku (SPEC, monetization, backend-api, backend/README) und Preisseite angepasst; die D1-Tabellen
bleiben. Tests: `FuelDemoTest` (neue Demo nur mit Claude, ohne Einladung), `DayWithOverfitTest`, `FuelBasicsTest`, `FuelApiTest`,
`RequestLogTest`, `CoachTransportTest`, `OwnKeyPrivacyTest`, `WebCreditTest` angepasst; reine Einladungs-Tests entfernt.
**Offen (Fabian):** Ob der Aufschlag (28 %) sinken soll, nachdem die Rücklage für Geschenktes entfällt (bleibt vorerst als Kulanz-Rücklage).
Alte GitHub-Secrets `INVITE_SIGNING_KEY`, `PLAY_INTEGRITY_SERVICE_ACCOUNT`, `APPLE_APP_ATTEST_TEAM_ID` dürfen gelöscht werden.

### Bitte auf dem Handy testen (Einladungen entfernt)
1. Profil → Guthaben: nur noch „X € Guthaben“, keine geschenkten Auswertungen, kein „gültig bis“, kein Bereich „Einladungen“.
2. Profil → Guthaben → Buchungen: alte Einladungs-/Test-Buchungen (falls vorhanden) heißen „Gutschrift“, nirgends „Einladung“.
3. Coach → „Über den Coach“ → Seite „Mit Guthaben“: kein Wort mehr über Freunde/Einladungsbonus.
4. Ein alter Einladungs-QR (`overfit://invite#…`) öffnet nichts Besonderes mehr (höchstens die Import-Meldung „kein Training“).
5. Coach-Antwort mit Guthaben: darunter immer ein Betrag „… € Guthaben“, nie „geschenkte Auswertung“.
## Muskel-Heatmap „Trainierte Muskeln“ (7.11.)
**Erledigt:** Kalender → neues Körper-Icon in der Titelzeile öffnet ein Bottom-Sheet „Trainierte Muskeln“: eigene, stilisierte
Figur vorne und hinten, jeder Muskel je nach Sätzen im Zeitraum von Dunkelgrau (untrainiert) bis Weiß (am meisten), Legende
„Viel ▬ Wenig“. Zeitraum per Chips: Diese Woche, Dieser Monat, Dieses Jahr, Gesamt (wird gemerkt). Gezählt werden abgehakte
Sätze beendeter Trainings, primäre Muskeln voll, mittrainierte halb. Tipp auf einen Muskel zeigt „Brust · 6 Sätze + 4 Sätze mittrainiert“.
Die Trainingsdetail-Ansicht zeigt dieselbe Figur klein für genau dieses Training. Grafik selbst gezeichnet (keine fremde Vorlage),
keine DB-Migration. Details in DECISIONS.md. Tests: `MuscleMapTest` (shared und App), `MuscleMapShotTest`.
Screenshots: `docs/screenshots/muskel-heatmap/`.
**Offen:** Schulter vorne/seitlich/hinten getrennt bräuchte feinere Muskelgruppen im Katalog.

### Bitte auf dem Handy testen (Muskel-Heatmap)
1. Kalender → Körper-Icon oben rechts: Sheet fährt von unten hoch, „Diese Woche“ ist gewählt, die Muskeln deiner Trainings dieser
   Woche sind hell, untrainierte dunkelgrau.
2. Auf einen Muskel tippen (z. B. Brust, Latissimus, Waden hinten): Darunter Name und Sätze; die anderen Muskeln werden blasser.
   Nochmal tippen hebt es auf.
3. „Dieser Monat“, „Dieses Jahr“, „Gesamt“ durchschalten: Färbung ändert sich. Sheet schließen (✕, Zurück, über dem Sheet tippen
   oder nach unten ziehen) und wieder öffnen: Der zuletzt gewählte Zeitraum ist noch aktiv, auch nach App-Neustart.
4. Kalender → Tag → Training öffnen: Abschnitt „Trainierte Muskeln“ zeigt nur dieses Training, ohne Zeitraum-Chips.
5. App auf Englisch: „Muscles trained“, „This week …“, Legende „High / Low“.

## Coach-Tab als Chat (Session Coach-Chat, 7.11.)
**Erledigt:** Der Coach-Tab ist ein Chat nach Fabians Entwurf: drei Vorschläge, Kontext-Chips „Profil“ (immer dabei, × pro Chat)
und „Letztes Training“ (automatisch), „+“-Sheet (Fotos mit Nummern, Training, Workout, Verlauf 4 Wochen, Übung mit Bestwerten),
Eingabeblock mit Modell-Chip und geschätzten Kosten, Antworten mit Markdown, Modell und Preis, Workout-Karte „Übernehmen“,
„Frühere Chats“ (nur auf dem Gerät, löschen per Doppeltipp). Modell-Sheet „Overfit-Guthaben | Eigener Schlüssel“ mit kostenlosem
Schlüssel-Test über die Modell-Liste des Anbieters, Guthaben-Popup (Kontostand, Aufladen, letzte Abrechnungen). Alles von der alten
Coach-Seite liegt jetzt unter Einstellungen → Coach (Zugang, Guthaben, Standardmodell, automatische Auswertung, Coach-Profil,
Coach-Anweisungen mit „Deine Ergänzung“, Gedächtnis, Auswertungen, Abrechnung, Prompts & Verbindungen, Story). Neue
Grundanweisung (Personal Trainer, antwortet in der Sprache der Frage). Die Auswertung nach dem Training ist ein neuer Chat mit dem
Training und „Werte mein Training aus“. DB v12 (Chat-Anhänge, Modell, Profil aus; Migration + Test). Details in DECISIONS.md.
Screenshots: `docs/screenshots/coach-chat/`.
**Offen:** Kamera-Kachel im „+“-Sheet (braucht Kamera-Zugriff per expect/actual, bis dahin nur Galerie); eine Chat-Frage ohne Netz wartet nicht
wie früher die Auswertung in einer Warteschlange, sondern zeigt „Erneut versuchen“.

### Bitte auf dem Handy testen (Coach-Chat)
1. Coach-Tab: „Coach ⓘ“, rechts Liste und Stift; drei graue Vorschläge. Tipp auf einen Vorschlag setzt nur den Text ins Feld.
2. Nach einem Training: Chips „Profil“ und „Push · heute“ über der Eingabe. Frage senden: Antwort links als Text, darunter Modell
   und Preis (Guthaben) bzw. „… · eigener Schlüssel“. × am Profil-Chip, neue Frage: Profil geht für diesen Chat nicht mehr mit.
3. Tipp auf „Profil“: Popup „Dein Profil“ (Ziel, Erfahrung, Tage, Größe, Gewicht aus dem Kalender, Equipment, Einschränkungen) → Speichern.
4. „+“: Fotos antippen (Nummern 1, 2 …), „Verlauf“ oder ein Training wählen → „N hinzufügen“: Chips erscheinen, gesendete Nachricht
   zeigt sie als kleine Karten über der Blase.
5. „Mach mir ein neues Push-Workout“: Karte mit „Übernehmen“ → Workout wird wie ein Import übernommen.
6. Modell-Chip: Sheet „Overfit-Guthaben | Eigener Schlüssel“. Eigener Schlüssel: Anbieter wählen, Schlüssel einfügen, „Testen“ →
   „Schlüssel funktioniert · N Modelle gefunden“, Vorschläge antippen, „Übernehmen“. Falscher Schlüssel: verständliche Meldung.
7. Mit Guthaben: Chip „x,xx €“ oben rechts → Popup mit Kontostand, „läuft nicht ab“, Aufladen, letzten Anfragen, „Alle Anfragen ansehen“.
   Mit eigenem Schlüssel gibt es den Chip nicht.
8. Liste-Icon: „Frühere Chats“, Chat öffnen, löschen per Doppeltipp (rot). Stift: neuer Chat.
9. Einstellungen → Coach: alle Zeilen da; Coach-Anweisungen: Grundanweisung gekürzt, Ergänzung speichern; „Auf Standard zurück“ (Doppeltipp).
10. Training beenden (automatische Auswertung an): Im Coach-Tab öffnet sich ein Chat „Werte mein Training aus“ mit dem Training.
    Schalter aus: Abschluss zeigt den Hinweis, kein Chat.

## Hotfix: KI-Auswertung mit Guthaben (6.11.)
**Erledigt:** Auswertungen über Guthaben scheiterten auf dem Testserver: Gemini Flash ohne Schlüssel, Claude über das (seit 2026 immer
authentifizierte) Cloudflare AI Gateway mit 401, Lite-Modell bei Cloudflare abgekündigt. Backend geht ohne Gateway-Token direkt zum
Anbieter, 401/403 weicht auf die andere Stufe aus, Lite = Llama 3.3 70B. App lädt bei `tier_unavailable` die Config neu und zeigt
Server-Fehler verständlich. Details in DECISIONS.md. Tests: `tiers.test.ts`, `CoachTransportTest`.
**Offen (Fabian):** GitHub-Secret `GEMINI_API_KEY` (seit 7.11. nicht mehr nötig, Guthaben nur Claude); optional
`AI_GATEWAY_TOKEN` (Gateway-Protokolle). Repo-Variable `BACKEND_DEPLOY_MODE=test` für Auto-Deploy nach jedem Backend-Merge.

### Bitte auf dem Handy testen (Hotfix KI)
1. Training beenden → Coach-Auswertung mit Guthaben: Auswertung kommt (zurzeit von Claude Sonnet 5.5, weil Gemini Flash fehlt), Guthaben sinkt um wenige Cent (ca. 3–10 ct).
2. Profil → Guthaben → Modell: Gemini 3.6 Flash erscheint erst, wenn `GEMINI_API_KEY` gesetzt und der Server neu deployt ist.

## Letzte Werte im Workout, Trainings umbenennen (7.11.)
**Erledigt:** In einem gespeicherten Workout klappt ein Tipp auf eine Übung die Karte auf: „Letztes Mal · Datum“ und jeder Satz
wie eingetragen (Gewicht × Wdh., Zeit … je Tracking-Typ). Bevorzugt aus diesem Workout, sonst „Letztes Mal in einem anderen
Workout“; nie trainiert: „Noch nicht trainiert“. Tipp aufs Bild öffnet weiter die Übungsdetails. Vergangene Trainings lassen sich
umbenennen: In der Trainingsdetail-Ansicht auf den Namen tippen (oder Stift) → Name oben ändern → Speichern. Nur das Training wird
umbenannt, das Workout behält seinen Namen. Keine DB-Migration nötig (Trainings hatten schon ein eigenes Namensfeld). Details in
DECISIONS.md. Tests: `LastTimeAndRenameTest` (ganze App), `WorkoutEditRepositoryTest`, `BackupTest`. Screenshots:
`docs/screenshots/letzte-werte-umbenennen/`.

### Bitte auf dem Handy testen (Letzte Werte, Umbenennen)
1. Workouts → ein Workout öffnen, das du schon trainiert hast → auf eine Übung (Name, nicht Bild) tippen: Karte klappt auf mit Datum
   und deinen Sätzen vom letzten Mal; nochmal tippen klappt zu. Bild antippen öffnet weiter die Übungsdetails.
2. Eine Übung, die du nur in einem anderen Workout gemacht hast: „Letztes Mal in einem anderen Workout · Datum“. Nie gemacht: „Noch nicht trainiert“.
3. „Direkt starten“, ein paar Sätze, beenden. Kalender → Tag → Training öffnen → auf den Namen tippen → Name ändern → Speichern:
   Detail, Tagesansicht und Kalender zeigen den neuen Namen; das gespeicherte Workout heißt weiter wie vorher.
4. Bearbeiten ohne Namensänderung bzw. Name ganz löschen und speichern: Der alte Name bleibt.

## Trainings-Benachrichtigung rot/grün (6.11.)
**Erledigt:** Während des ganzen Trainings gibt es jetzt eine Benachrichtigung (Foreground-Service): grün im Satz (Übung,
„Satz 2/4 · 80 kg × 5“, Trainingszeit, „Satz fertig“), rot in der Pause (Countdown, „Nächste: …“, +15 s, Überspringen).
Beim Wechsel Satz → Pause und Pause → Satz meldet sie sich neu (aufpoppen, Vibration nach Einstellung, kein Ton), sonst still.
Wegwischen: auf dem Sperrbildschirm gar nicht, entsperrt (ab Android 14 erlaubt Android das) kommt sie sofort zurück, jetzt oben
statt im Abschnitt „Lautlos“. Ursache des alten Verhaltens und Typ-Begründung in DECISIONS.md. Tests: `WorkoutNotificationTest`,
`WorkoutControllerTest`.
**Offen:** iOS hat keine dauerhafte, farbige Benachrichtigung (bräuchte eine Live Activity, eigenes Feature); dort wie bisher nur
die Meldung zum Pausenende. Play: `specialUse`-Begründung in der Play Console angeben (Text in DECISIONS.md).

### Bitte auf dem Handy testen (Trainings-Benachrichtigung)
1. Training starten, App verlassen: Benachrichtigung „Laufendes Training“ ist da, grün, mit Übung, „Satz 1/x · Gewicht × Wdh.“,
   laufender Trainingszeit und „Satz fertig“. Beim Start poppt nichts auf.
2. In der Benachrichtigung „Satz fertig“ tippen: Sie wird rot, poppt kurz auf und vibriert (falls Vibration an), Countdown läuft,
   „Nächste: …“, +15 s und Überspringen funktionieren (+15 s: still, kein erneutes Aufpoppen).
3. Pause auslaufen lassen (App im Hintergrund, Spotify läuft): Ticken wie bisher, dann wird sie grün mit „Pause vorbei“, poppt auf
   und vibriert. Eine separate „Pause vorbei“-Meldung gibt es nicht mehr.
4. In der App Satz abhaken / Pause endet: nichts poppt über der App auf, die App vibriert selbst; Benachrichtigung wechselt trotzdem die Farbe.
5. Wegwischen (im Satz und in der Pause, mehrmals): Sie ist sofort wieder da, oben in der Liste. Auf dem Sperrbildschirm lässt sie sich nicht wegwischen.
6. Training beenden oder verwerfen: Benachrichtigung verschwindet und kommt nicht wieder.
7. Profil → Vibration aus: Übergänge poppen still auf (ohne Vibration).
8. Overfit aus den „Letzten Apps“ wischen während eines Trainings: Benachrichtigung bleibt, Aktionen funktionieren.

## Hotfix: kaputte Übungsbilder (Hotfix-Session Bilder, 3.11.)
**Erledigt:** Seit 0.1.257 waren fast alle Workout-Guide-Bilder (Standbilder und Animation) verzerrt. Ursache: Das Runden der
Pfaddaten im Import hat kompakte Zahlen wie `8.002.392` zu `8.4` verschmolzen. Import jetzt mit echtem Pfad-Parser, 2 Nachkommastellen,
Rückprüfung jeder Datei; alle 906 Frames neu erzeugt. Regressionstest `ExerciseSvgIntegrityTest` (parst alle Dateien, prüft viewBox,
rendert 15 Frames und vergleicht mit den Originalen). Details in DECISIONS.md.

### Bitte auf dem Handy testen (Hotfix Bilder)
1. Tab „Übungen“: Die Strichgrafiken sind wieder vollständige Figuren (keine Flecken, Dreiecke oder fehlenden Bilder), auch im Picker,
   im Workout-Editor und im laufenden Training.
2. Übung antippen (z. B. Klimmzug, Ab Wheel): Das große Bild bewegt sich ruhig 1→2→3→2→1, jeder Frame ist eine saubere Figur.
3. Hell- und Dunkelmodus: Bilder in der Theme-Farbe (schwarz bzw. weiß).

## Übungsbild → Details mit Animation (Session AH, 3.11.)
**Erledigt:** Tipp auf ein Übungsbild öffnet überall die Übungsdetails (Katalog, Picker, Editor, laufendes Training); im Training
schreibgeschützt, Timer und Pausenleiste laufen weiter, Zurück führt ins Training. Detailansicht mit bildschirmbreitem Bild, das
die 3 Workout-Guide-Frames ruhig durchläuft (1→2→3→2→1, 700 ms). Import bündelt alle Frames (SVGs leicht verkleinert). Details in
DECISIONS.md. Offen: „Animationen reduzieren“ (Systemeinstellung) braucht `expect/actual`. Tests: `ExerciseImageTapTest`, `ExerciseFramesTest`.

### Bitte auf dem Handy testen (Session AH)
1. Training starten, auf das Übungsbild im Block-Kopf tippen: Detailseite mit großem Bild, das sich ruhig bewegt; kein „Speichern“,
   keine Tracking-/Pausen-Einstellung. Zurück: Training, Pausenleiste und Timer liefen weiter.
2. Workout bearbeiten: Tipp aufs Bild (mit „?“) öffnet die Details, dort „Speichern“ wie bisher; Zurück ins Bearbeiten.
3. Übung hinzufügen: Tipp aufs Bild einer Zeile = Details, Tipp auf den Namen = auswählen. Übungen (Katalog): Bild und Zeile öffnen Details.
4. In Listen bleiben die Bilder Standbilder; eine eigene Übung ohne Bild zeigt weiter die Initialen (keine Animation).
5. App-Größe der APK im Release prüfen (siehe PR), Profil → Über Overfit: Workout-Guide-Hinweis nennt die Rundung.

## Ticken vor Pausenende (Session AG, 3.11.)
**Erledigt:** In den letzten 3 Sekunden der Pause tickt es (3, 2, 1), danach das gewohnte Pausenende. Schalter „Ticken vor Pausenende“
im Profil unter „Vibration“, Standard an. Funktioniert im Hintergrund, in anderen Apps und bei gesperrtem Bildschirm (Android: eigener
exakter Alarm, Ton über die Medienlautstärke, Musik wird nur kurz leiser; iOS: Benachrichtigung mit eigenem Ton, im Vordergrund spielt
die App selbst). Ton synthetisch erzeugt. Details und Lautlos-Verhalten in DECISIONS.md. Tests: `RestTicksTest`, `RestTickAlarmTest`
(Robolectric), Controller- und AppStore-Tests.
Zusatz: Die Pausen-Benachrichtigung (Countdown) kommt nach dem Wegwischen sofort zurück, solange die Pause läuft
(`RestNotificationTest`). Neuer Einstellungs-Abschnitt „Benachrichtigungen“ (Berechtigung mit „Erlauben“, exakte Alarme,
Vibration, Ticken, Hinweis zum Countdown).
**Offen:** iOS im Lautlos-Modus und App im Hintergrund: kein Ticken (Systemgrenze, nur Vibration). Eine Benachrichtigung über das
ganze Training (auch zwischen den Pausen) gibt es weiterhin nicht – eigenes Feature, falls gewünscht.

### Bitte auf dem Handy testen (Session AG)
1. Spotify laufen lassen, Training starten, Satz abhaken (kurze Pause, z. B. 15 s Standardpause im Profil). In eine andere App
   wechseln: bei 3, 2, 1 s drei Klicks, Musik wird kurz leiser und läuft weiter, dann die „Pause vorbei“-Benachrichtigung.
2. Dasselbe mit gesperrtem Bildschirm und mit Handy auf lautlos: Klicks trotzdem hörbar (Medienlautstärke > 0).
3. App offen lassen: Klicks genau einmal (nicht doppelt), Timer läuft im Takt aus.
4. Während der Pause +15 s / −15 s / Überspringen: Klicks kommen nur zum neuen Ende bzw. gar nicht. Pause auf unter 3 s kürzen: kein Ticken.
5. Profil → „Ticken vor Pausenende“ aus: keine Klicks mehr, Vibration/Benachrichtigung wie gehabt.
6. Falls „Genaue Pausen-Benachrichtigungen“ nicht erlaubt ist: Ticken kann entfallen (bewusst, lieber kein als falsches Ticken).
7. Während einer Pause die Countdown-Benachrichtigung wegwischen: Sie ist sofort wieder da (mehrmals probieren). Nach dem Überspringen
   oder am Pausenende verschwindet sie normal, nach „Training beenden“ ebenso.
8. Profil → Abschnitt „Benachrichtigungen“: „Benachrichtigungen erlauben · Erlaubt“, Vibration, Ticken. Benachrichtigungen in den
   Android-Einstellungen für Overfit ausschalten, zurück in die App: „Erlauben“ mit Hinweis; antippen → Dialog oder Einstellungen,
   danach wieder „Erlaubt“.

## Feedback-Runde 4 (Session AF, 3.11.)
**Erledigt:** Beispiele (Push/Pull/Legs) kommen nach „Daten löschen“ sofort zurück; kein schwarzer Screen mehr nach Workout löschen
(doppelter Pop behoben); Kalender immer sichtbar, Trainingstage ohne Foto als gefüllter Punkt, bei Foto + Training kleiner Punkt;
Gewichte nur noch Plus (± entfernt). Details in DECISIONS.md. Tests: `SamplesSeedTest`, `DiaryScreenTest`, `UiFeedbackTest` erweitert.

### Bitte auf dem Handy testen (Session AF)
1. Profil → Alle Daten löschen → Onboarding durchlaufen: Push, Pull, Legs sind sofort da. Ein Beispiel löschen, App neu starten: bleibt weg.
2. Workout öffnen → Mülleimer → bestätigen: Du landest in der Workout-Liste, nicht auf schwarzem Screen. Auch Training im Verlauf, eigene Übung, Foto löschen.
3. Fotos-Tab ohne Foto: Kalender ist da. Tag mit Training ohne Foto: gefüllter Punkt. Tag mit Foto + Training: Bild mit kleinem Punkt.
4. Satz-Eingabe (Editor und Training): kein ±-Knopf, im Gewichtsfeld ist kein Minus möglich.
5. Training starten (Weighted Pull-up, Englisch und Deutsch): Satzzeile einzeilig, Felder breit genug für „102.5“, Platzhalter „–“ statt senkrechtem „kg“, Zeit-Übung (Plank) ebenso.

## Feedback-Runde 3: UI (Session AE, 3.11.)
**Erledigt** (enthält PR #56: Push/Pull/Legs ab dem ersten Start, keine „Beispiel-Workouts hinzufügen“-Zeile mehr):
- Übergänge: Ursache (700-ms-Standardüberblendung, Leisten springen, Doppel-Taps wirken) behoben; Tabs sofort, sonst kurzes Fade-through,
  Taps während des Wechsels gesperrt. Details in DECISIONS.md.
- Teilen/Löschen/Bearbeiten als Icons oben rechts (Workout, Training im Verlauf, eigene Übung, Foto); im Bearbeiten-Modus weder Teilen
  noch Löschen. Workout-Screen: unten „Starten“, darunter gleich groß „Bearbeiten“.
- Keine unterstrichenen Buttons mehr (`SmallButton`); Bestätigungs-Buttons ohne Rahmen, nur einer scharf, Tipp woanders entschärft.
- Duo-Teaser und „Letzte Coach-Tipps“ entfernt; „Über overfit“: eigene MIT-Lizenz + Repo-Link, Bibliotheken getrennt (Apache 2.0).
- Workout-Editor und Workout-Modus im Block-Layout (Fabians Screenshot): Übung als graue Fläche mit Bild, „?“, ⋮-Menü; Sätze als eigene
  Zeilen mit großen Feldern; Satz nach links wischen löscht, mit „Rückgängig“.
- Tests: `UiFeedbackTest` (Bestätigen, Wischen, Editor + Rückgängig, Übergang, Tab-Wechsel), bestehende Tests angepasst.

### Bitte auf dem Handy testen (Session AE)
1. Zwischen Screens wechseln (Workout öffnen, zurück, Verlauf → Training): kurzes ruhiges Ausblenden/Einblenden, kein Springen, kein
   Flackern. Tabs wechseln sofort. Schnell doppelt auf „Zurück“ tippen: nur ein Screen zurück. Zurück-Wischgeste ausprobieren.
2. Workout öffnen: oben rechts Teilen- und Mülleimer-Icon; Mülleimer → rote Pille „Wirklich löschen?“, darunter Hinweis zum Verlauf.
   Unten großes „Starten“, darunter großes „Bearbeiten“.
3. „Bearbeiten“: Kopf mit Name + Stift und „Bearbeitungsmodus“, kein Teilen/Löschen. Übungen als graue Blöcke (Chevron klappt, ⋮ mit
   Ersetzen/Verschieben/Entfernen, „?“ am Bild öffnet Details). Satz nach links wischen → rot, löscht; „Rückgängig“ unten holt ihn zurück.
4. Training starten: gleiche Blöcke und Satzzeilen, Satz wischen löscht, Haken weiter rechts.
5. Irgendeinen roten oder grünen Bestätigungs-Button antippen (z. B. „Beenden“, „Training verwerfen“): kein Rahmen; danach woanders
   tippen → zurückgesetzt; einen zweiten Bestätigungs-Button antippen → der erste setzt zurück.
6. Kleine Buttons (Pausentimer −15 s/+15 s/Überspringen, Coach, Guthaben „Erneut versuchen“ …) sind graue Pillen, nichts unterstrichen.
7. Verlauf → Training: Teilen/Löschen oben rechts. Fotos → Foto öffnen: Teilen/Löschen oben rechts. Eigene Übung: Stift + Mülleimer oben.
8. Training-Tab: kein Duo, keine Coach-Tipps. Profil → Über overfit: „Open Source unter der MIT-Lizenz“, Link zum Repo, Bibliotheken separat.

## Einladung intern, Guthaben vs. echte Euro, Bonus als Auswertungen (Session AD, 3.11.)
**Erledigt:**
- **„Spot“ intern entfernt:** API `/v1/invite/*`, Env `INVITE_*`, D1-Migration `0007_invite.sql` (Tabellen, Index, Ledger-Typen umbenannt,
  Daten erhalten, Migrationstest), Kotlin `Invite*`, Dateien `invite.ts`/`InviteLink.kt`/`InviteScreens.kt`. Nur `overfit://invite#…` wird gelesen.
  Deploy übernimmt das alte Signier-Secret automatisch.
- **Einladungsbonus = geschenkte Coach-Auswertungen:** Neuer 5, Einladender 5 (nach der ersten Anfrage des Freundes). Eigener Zähler,
  nur für Coach-Auswertungen mit Gemini Flash, vor dem Guthaben verbraucht, Verfall nach 180 Tagen. Chat und Claude zahlen aus dem Guthaben.
  Grenzen: 1000 Auswertungen/Monat global, harte Grenze 50 € echte KI-Kosten/Monat, 0,25 € je Anfrage; alte Grenzen bleiben.
  Kosten: docs/monetization.md „Einladungsbonus“ (0,014 € je Auswertung, ab 2027 0,028 €).
- **Missbrauchsschutz Kontoumzug:** Geschenkte Einladungs-Auswertungen des aufgegebenen Kontos verfallen, Gekauftes wandert mit (Test).
- **Guthaben vs. echte Euro:** App zeigt „4,20 € Guthaben · 5 geschenkte Auswertungen“, darunter „Gekauft: … € Guthaben · geschenkt: … €
  Guthaben“, Verfall je Posten, „Geschenktes wird zuerst verbraucht.“ Preise nur als „Preis: 10,00 €“ bzw. „6,99 € (inkl. Store-Gebühr)“.
  Buchungen: „bezahlt / geschenkt / verbraucht / erstattet / verfallen“, geschenkte Auswertungen als „+5 Auswertungen“ / „−1 Auswertung“.
- **Admin-Report** (`/v1/admin/usage` → `finance`, `scripts/usage-report.mjs`): Einnahmen je Kanal brutto und nach Gebühren, offenes
  Guthaben gekauft/geschenkt, KI-Kosten, Rohgewinn. Geschenktes zählt nie als Einnahme.
- Tests: Backend 134 (u. a. `invite.test.ts`, `migration0007.test.ts`, Finanzbericht), Shared/App-Tests angepasst; `OwnKeyPrivacyTest` grün.
**Offen:** echte Einladung zwischen zwei Handys braucht weiter Attestierung (Store-Build) oder den Testserver.

### Von Fabian zu tun (Session AD)
1. **Actions → Backend deployen → Run workflow**, `mode` = test (spielt Migration 0007 ein). Sonst nichts: Secrets bleiben, das alte
   Signier-Secret wird automatisch übernommen.
2. Neues APK aus „Overfit latest“ installieren.

### Bitte auf dem Handy testen (Session AD)
1. **Profil → Guthaben:** oben „X € Guthaben“; falls du noch Euro-Einladungsboni oder Test-Gutschrift hast, darunter „Gekauft: … ·
   geschenkt: …“ und „Geschenktes wird zuerst verbraucht.“
2. **Buchungen:** jede Zeile sagt „bezahlt“, „geschenkt“ oder „verbraucht“ vor dem Datum; Beträge als „… € Guthaben“.
3. **Freunde einladen:** Text „Ihr bekommt beide 5 Coach-Auswertungen geschenkt …“ und der Hinweis auf Gemini 3.6 Flash.
4. **Einladung einlösen** (zweites Handy oder Demo): Reveal zählt bis **5** hoch, darunter „Coach-Auswertungen geschenkt“; danach im
   Guthaben „… € Guthaben · 5 geschenkte Auswertungen“ und „5 Auswertungen gültig bis …“.
5. **Auswertung mit Gemini 3.6 Flash** nach dem Einlösen: Kostenzeile „geschenkte Auswertung“, Zähler sinkt auf 4, Euro bleibt gleich.
   Ein Chat kostet dagegen Guthaben. Der Einladende hat danach 5 geschenkte Auswertungen.
6. **Coach-Tab und Einstellungen:** Zugang zeigt „… € Guthaben“ (mit geschenkten Auswertungen, falls vorhanden).

## Stripe statt Paddle, Store-Aufpreis offen, Aufschlag 28 % (Session AB, 3.11.)
**Erledigt:**
- **Stripe Checkout** (Testmodus) hinter `PaymentProvider` (`backend/src/payments/stripe.ts`), Paddle entfernt. Gehostete Checkout-Seite,
  Zahlarten aus dem Stripe-Dashboard, kein Stripe Tax. Webhook mit eigener Signaturprüfung (WebCrypto, 300 s, mehrere `v1`, `livemode`-Abgleich):
  `checkout.session.completed`/`async_payment_succeeded`, `charge.refunded` (kumulierte Teil-Erstattungen), `charge.dispute.created`,
  idempotent. Codes laufen ebenfalls über Stripe. Migration `0006_stripe.sql` (nur neue Spalten: PaymentIntent, Land, bezahlter Betrag).
- **Kleinunternehmer:** „Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.“ (EN sinngemäß) auf Kaufseite, im Stripe-Checkout und in der
  Bestätigung; optional Stripe-Rechnung mit Fußzeile (`STRIPE_INVOICES=on`). Schwellen-Hinweis in docs/monetization.md; `webRevenue` im
  Admin-Usage-Report und in `scripts/usage-report.mjs` (Web-Umsatz je Jahr, Land, gesamt, Warnung ab 80 %).
- **Preise je Kanal:** Web 1:1, Pakete **5/10/20/50 €** (10 € vorgewählt). Store mit offenem Aufpreis: **5 € Guthaben für 6,99 €, 10 € für 13,99 €**.
  **`MARKUP_BP = 2800` (28 %)** statt 69 %. Rechnung mit Tabellen: docs/monetization.md „Rechnung (Session AB)“; `pricing.test.ts` prüft jedes
  Paket ≥ 5 % vom Netto (Store bei 19 % und 27 % USt), keine Zahlart im Verlust, Preisstufen und den Aufschlag.
- **App:** Guthaben zeigt je Kanal beide Werte: „5,00 € Guthaben · 6,99 € (inkl. Store-Gebühr)“ (App Store und Google Play) und unter „Im Web“
  „10,00 € Guthaben · 10,00 €“ (nur wo „Im Web aufladen“ per Flag sichtbar ist). Texte ohne „69 %“, der Prozentwert kommt aus `/v1/config`.
- **Webseite:** Kaufseite leitet auf Stripe weiter (Abbruch kehrt mit gemerktem Kauflink zurück), `web/preise/` mit Beispiel Web und Store.
- **Deploy:** Secrets `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` (Produktion: `STRIPE_LIVE_…`), Zusammenfassung nennt Webhook-URL und
  Ereignisse; fehlen sie oder passt der Schlüssel nicht zum Modus, bleibt der Web-Kauf aus, mit Hinweis.
- Tests: Backend 126 (web, pricing, revenue, money, transparency u. a.), `web/test/guthaben.mjs` (26 Prüfungen), `WebCreditTest`,
  `FuelDemoTest`, `WebPurchaseTest`, `CostEstimateTest`; `OwnKeyPrivacyTest` unverändert.
**Offen:** echter Lauf gegen Stripe im Testmodus (gemockt geprüft); Live: Kontoaktivierung, Rechtstexte, juristische Prüfung.

### Von Fabian zu tun (Session AB, alles im Browser)
Ausführlich mit Pfaden: backend/README.md → „Web-Kauf (Stripe, Testmodus)“.
1. **dashboard.stripe.com/register** → Konto anlegen, E-Mail bestätigen. Oben **Testmodus** einschalten.
2. **Einstellungen → Unternehmen → Öffentliche Unternehmensangaben:** Name `overfit`, Support-E-Mail, Website `https://bigfire3.github.io/overfit/`,
   Statement descriptor `OVERFIT`.
3. **Einstellungen → Billing → Rechnungsvorlage → Standard-Fußzeile:** `Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.` → Speichern.
   **Einstellungen → Unternehmen → Kunden-E-Mails:** „Erfolgreiche Zahlungen“ an.
4. **Einstellungen → Zahlungen → Zahlungsmethoden:** Karten an; optional Apple Pay, Google Pay, Link, Klarna, SEPA, EPS, iDEAL, Bancontact.
5. **Entwickler → API-Schlüssel:** Geheimschlüssel `sk_test_…` kopieren.
6. **Entwickler → Webhooks → Endpunkt/Ziel hinzufügen:** URL = Repo-Variable `OVERFIT_BACKEND_URL` + `/v1/webhooks/payment`; Ereignisse
   `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `charge.refunded`, `charge.dispute.created` → Speichern →
   Signaturgeheimnis `whsec_…` kopieren.
7. **GitHub → Settings → Secrets and variables → Actions → New repository secret:** `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`.
   Die alten `PADDLE_API_KEY`, `PADDLE_WEBHOOK_SECRET`, `PADDLE_CLIENT_TOKEN` und die Variable `PADDLE_ENV` löschen.
8. **Actions → Backend deployen → Run workflow**, `mode` = test. Zusammenfassung: „Web-Kauf: An, Testmodus (Stripe)“, Webhook-URL = Schritt 6.
9. **Actions → Web veröffentlichen (GitHub Pages) → Run workflow.**
10. Neues APK aus „Overfit latest“ installieren, dann testen (unten).

**Für echte Zahlungen später:** Stripe-Konto aktivieren (Einzelunternehmen, Identität, Bankkonto, Steuernummer, Kleinunternehmer), im
Live-Modus Schritte 2–6 wiederholen, GitHub-Secrets `STRIPE_LIVE_SECRET_KEY` und `STRIPE_LIVE_WEBHOOK_SECRET`, Backend deployen mit
`mode` = production; Impressum, Datenschutz, AGB/Widerruf (Platzhalter auf der Kaufseite), juristische Prüfung; Schwellen mit dem
Steuerberater klären (`scripts/usage-report.mjs` warnt ab 80 %).

### Bitte auf dem Handy testen (Session AB, nach den Schritten oben)
1. Profil → Guthaben: unter „App Store und Google Play“ „5,00 € Guthaben · 6,99 € (inkl. Store-Gebühr)“ und „10,00 € Guthaben · 13,99 €
   (inkl. Store-Gebühr)“; unter „Im Web“ 5/10/20/50 € je „… Guthaben · …“ 1:1. Darunter der Satz mit „28 % Aufschlag“.
2. „Wie setzen sich die Preise zusammen?“ → Seite zeigt Web-Beispiel (10 € → 0,74 € Gewinn) und Store-Beispiel (6,99 €).
3. Coach-Einstellungen → Modell: Schätzung ≈ 1,8 ct je Auswertung (Gemini 3.6 Flash), Hinweis nennt 28 %.
4. „Im Web aufladen“ → Kaufseite: 5/10/20/50 €, 10 € vorgewählt, „Gemäß § 19 UStG …“, „Testmodus“. 5 € wählen → „Weiter zur Zahlung“ →
   Stripe-Seite (Testmodus-Banner, §-19-Satz unter dem Bezahlknopf) → Karte `4242 4242 4242 4242`, Datum in der Zukunft, CVC beliebig →
   „Danke! 5 € Guthaben …“ mit §-19-Satz → „Zurück zur App“ → Saldo +5,00 €, Buchung „Aufladung im Web“.
5. Abbruch: auf der Stripe-Seite „Zurück“ → Kaufseite „Zahlung abgebrochen, es wurde nichts berechnet.“, weiter „Guthaben aufladen“.
6. Erstattung: Stripe → Zahlungen → Kauf → Erstatten (z. B. 2 €) → Saldo −2 €, Buchung „Erstattung“; Rest erstatten → nochmal −3 € (nie unter 0).
7. Am Rechner Kaufseite ohne App → Code kaufen → Code in der App einlösen (wie Session Z).

## Marge bei 1:1-Guthaben (Session AA, 3.11.)
**Erledigt:**
- Rechnung in docs/monetization.md („Rechnung“, mit Tabellen): USt 19 %, Paddle 5 % + 0,50 € (an paddle.com/pricing geprüft), Store 15 % auf Netto nach USt, Einladungsboni/Startguthaben als 10 % vom Netto, Zielgewinn 7 %. Ergebnis **`MARKUP_BP = 6900` (69 %)**, ein Wert für alle Modelle; Store ist der Engpass.
- **Web-Pakete 10/20/50 €** (5 € hätte den Aufschlag auf 74,5 % gehoben); Store-Pakete `overfit.fuel.5`/`.10` bleiben. **Gutschrift überall = Preis**, Store-Formel (doppelter Aufschlag, ohne USt) entfernt, `STORE_FEE_BP` entfällt.
- App: Kostenschätzungen kommen weiter aus `/v1/config` (jetzt mit 69 %); unter Guthaben ein ehrlicher Satz plus Link „Wie setzen sich die Preise zusammen?“ → `web/preise/` (DE+EN).
- Tests: `backend/test/pricing.test.ts` (Break-even je Kanal/Paket, Schätzung), Config-, Kauf- und Abrechnungstests angepasst.

**Bitte auf dem Handy testen (nach Merge, neue APK):**
1. Profil → Guthaben: Pakete zeigen „Paket 5 € → 5 € Guthaben“, darunter der Satz zum 69-%-Aufschlag und der Link „Wie setzen sich die Preise zusammen?“ → öffnet die Seite (erst nach dem Pages-Deploy erreichbar).
2. Coach-Einstellungen → Modell: Schätzung „≈ 2,4 ct“ je Auswertung (Gemini 3.6 Flash), Hinweistext nennt 69 %.
3. „Im Web aufladen“ (nur Testmodus/Sideload): Kaufseite zeigt 10/20/50 €.

## Guthaben im Web kaufen, Testmodus (Session Z, 3.11.)
**Erledigt:**
- **Backend:** `POST /v1/purchase/web-link` (signiert, Token 15 min, einmalig), öffentliche Endpunkte der Kaufseite
  (`/v1/web/session`, `/v1/web/checkout`, `/v1/web/orders/<id>`), `POST /v1/webhooks/payment` mit Paddle-Signaturprüfung,
  idempotente Buchung (Quelle `web`, Paddle-Transaktions-ID), Rückerstattung/Chargeback bis höchstens 0 mit Vorfall-Markierung,
  Guthaben-Codes (`POST /v1/codes/redeem`). `/v1/config` → `webPurchase` (an/aus, Testmodus, Kanäle, Pakete). Migration `0004_web_purchase.sql`.
  Paddle hinter `PaymentProvider`. Pakete 5/10/20 €, **Guthaben = Preis** (Begründung und offene Margenfrage: DECISIONS, monetization.md).
- **Webseite** `web/guthaben/`: Paketwahl, „Testmodus – keine echte Zahlung“, Paddle-Checkout, Erfolgsseite mit „Zurück zur App“
  (`overfit://guthaben`), ohne App „Guthaben-Code kaufen“ (Code auf der Erfolgsseite), Rechtstexte als „folgt vor dem Start“ markiert.
- **App:** Guthaben → „Im Web aufladen“ (öffnet den Kauflink im Browser; Kontostand lädt neu beim Zurückkommen und per Deep Link),
  „Code einlösen“, neue Buchungsarten (Aufladung im Web, Code eingelöst, Erstattung, Rückbuchung). Knopf nur, wenn `/v1/config` ihn für
  den Kanal freigibt: seitlich installierte APK an, Store-Builds (Play-Installer, iOS) aus.
- **Deploy-Workflow:** Secrets `PADDLE_API_KEY`, `PADDLE_WEBHOOK_SECRET`, `PADDLE_CLIENT_TOKEN` (fehlen sie, bleibt der Web-Kauf aus,
  Hinweis in der Zusammenfassung); Zusammenfassung nennt Webhook-URL und Status. Pages-Workflow schreibt die Backend-Adresse in die Kaufseite.
- Tests: `backend/test/web.test.ts` (Token, Signatur, Idempotenz, Rückerstattung, Chargeback, Codes, Config, Umzug),
  `web/test/guthaben.mjs` (Kaufseite im Browser), `WebPurchaseTest` (common), `WebCreditTest` + `CreditReturnLinkTest` (Robolectric);
  `OwnKeyPrivacyTest` unverändert grün.
**Offen:** echter Lauf gegen die Paddle-Sandbox (nur gemockt geprüft); Live-Betrieb braucht Paddle-Live-Konto, Rechtstexte,
juristische Prüfung und die Margenentscheidung.

### Von Fabian zu tun (Session Z, alles im Browser)
1. **sandbox-login.paddle.com/signup** → Sandbox-Konto anlegen (kostenlos, keine Prüfung).
2. Paddle → **Checkout → Checkout settings** → *Default payment link*: `https://bigfire3.github.io/overfit/guthaben/` → Save.
3. Paddle → **Developer tools → Authentication**: *API keys* → **New API key** (Name `overfit-test`, Recht *Transactions: Write*) → Wert kopieren.
   Dann *Client-side tokens* → **New client-side token** → Wert kopieren.
4. Paddle → **Developer tools → Notifications → New destination**: Typ *Webhook*, URL = deine Worker-Adresse (Repo-Variable
   `OVERFIT_BACKEND_URL`) + `/v1/webhooks/payment`, Ereignisse `transaction.completed`, `adjustment.created`, `adjustment.updated` → Save →
   Ziel öffnen → *Secret key* kopieren.
5. GitHub → Settings → Secrets and variables → Actions → **New repository secret**: `PADDLE_API_KEY`, `PADDLE_CLIENT_TOKEN`, `PADDLE_WEBHOOK_SECRET`.
6. Actions → **Backend deployen** → Run workflow → `mode` = test. Zusammenfassung: „Web-Kauf: An, Testmodus“ und die Webhook-URL (= Schritt 4).
7. Actions → **Web veröffentlichen (GitHub Pages)** → Run workflow (Kaufseite bekommt die Backend-Adresse).
8. Neues APK aus „Overfit latest“ installieren, dann testen (unten). Testkarte: `4242 4242 4242 4242`, Ablauf in der Zukunft, CVC `100`.

### Bitte auf dem Handy testen (Session Z, nach den Schritten oben)
1. Profil → Guthaben (Opt-in falls eigener Key) → unter „Aufladen“ steht „Im Web aufladen“ mit „Testmodus – keine echte Zahlung“.
2. Antippen → Browser öffnet die Kaufseite: „Guthaben aufladen“, Testmodus-Hinweis, 5/10/20 € (10 € vorgewählt). 5 € wählen →
   „Weiter zur Zahlung“ → Paddle-Fenster → Testkarte → bezahlen → „Danke! 5 € Guthaben sind auf deinem Konto.“
3. „Zurück zur App“ → Guthaben-Screen öffnet sich, Saldo +5,00 €. Buchungen: „Aufladung im Web +5,00 €“.
4. Gleichen Link nochmal öffnen (Browser-Verlauf): „Mit diesem Kauflink wurde schon bezahlt“ und Code-Kauf angeboten.
5. Am Rechner `https://bigfire3.github.io/overfit/guthaben/` öffnen → „Guthaben-Code kaufen“ → bezahlen → Code wird angezeigt →
   in der App Guthaben → „Code einlösen“ → Code eingeben → „5,00 € Guthaben gutgeschrieben.“ Zweites Mal: „schon eingelöst“.
6. Erstattung: Paddle-Sandbox → Transactions → Kauf öffnen → Refund (voll) → nach Freigabe sinkt der Saldo um 5 € (nie unter 0),
   Buchung „Erstattung“.
## Ehrliche Begriffe und volle Kostentransparenz (Session Y, 3.11.)
**Erledigt:**
- **„Fuel“ heißt jetzt „Guthaben“** (EN „Credit“) in UI, README, SPEC, Store-Texten, backend/README, API-Doku und Workflow-Meldungen. Code-Bezeichner (`Fuel*`) und Store-Produkt-IDs bleiben (Begründung in DECISIONS.md).
- **Echte Modellnamen statt Standard/Pro/Lite:** Coach → Einstellungen → „Modell für deinen Coach“ listet „Gemini 3.6 Flash (Google)“ und „Claude Sonnet 5.5 (Anthropic)“ mit geschätzten Kosten je Auswertung und Chat-Nachricht (in Cent), dazu „Mit deinem Guthaben (…) reicht das für etwa N Auswertungen“ und der Preis von „Mit KI ausfüllen“ (Llama 3.1 8B (Cloudflare)). Namen, Anbieter, Preise und typische Token-Mengen kommen aus `GET /v1/config`; Rechenweg in DECISIONS.md „Kostenschätzung“.
- **Unter jeder Antwort** (Coach-Auswertung und Chat): Modell · echte Kosten · Guthaben. Buchungen zeigen das Modell. Bei Ausweichung steht, welches Modell geantwortet hat und dass der Preis des gewählten berechnet wurde.
- **Foto-Anfragen im AI Gateway:** laut Doku geht „nur Metadaten“ (`cf-aig-collect-log-payload: false`): keine Inhalte, aber Tokens/Kosten/Modell/Dauer/Status erscheinen. Umgestellt.
- **Inhaltsfreie Auswertung:** Migration `0004` (Anbieterkosten, Ausweichung, Foto je Buchung), `GET /v1/admin/usage?from=&to=` (ADMIN_SECRET), Skript `backend/scripts/usage-report.mjs`. Marge in docs/monetization.md offen beschrieben.
- Room v7 (AutoMigration 6→7: `servedModel`, `fallbackFrom`), Migrationstest. Tests: Kostenschätzung, Formatierung, Modellnamen, Transport/Ausweichung, Backend (Config-Namen, Admin-Usage, Gateway-Header, Ausweichungs-Meta), UI-Tests auf „Guthaben“ umgestellt; OwnKeyPrivacyTest grün.

**Offen:**
- Beim Deploy: Migration `0004` wird vom Deploy-Workflow wie die anderen angewendet. Für `usage-report.mjs` muss `ADMIN_SECRET` im Worker gesetzt sein.
- Die Schätzwerte (Token je Aufgabe) sind aus den Prompt-Bausteinen berechnet; bei großen Prompt-Änderungen neu messen.

**Bitte auf dem Handy testen (Session Y):**
1. Profil → Guthaben: Titel „Guthaben“, „Guthaben aufladen“, Demo ansehen → Buchungen: Modellnamen (kein Standard/Pro), die oberste Buchung nennt das Ersatzmodell
2. Coach → Einstellungen (mit Guthaben als KI): „Modell für deinen Coach“, beide Modelle mit Cent-Kosten, „reicht für etwa N Auswertungen“, Auswahl umschalten (Haken wandert)
3. Eine Auswertung bzw. Chat-Frage mit Guthaben: unter der Antwort „Modell · Kosten · Guthaben“
4. „Mit KI ausfüllen“ bei einer eigenen Übung: nennt Llama 3.1 8B in den Einstellungen mit Preis
5. Nirgends mehr „Fuel“, „Standard“ (als Modell), „Pro“, „Lite“ sichtbar

## Fuel mit mehreren KI-Anbietern und AI Gateway (Session X, 3.11.)
**Erledigt:**
- **Stufen statt fester Anbieter:** `standard` = Gemini 3.6 Flash (Voreinstellung: Coach-Auswertung, Chat, Vorschläge), `pro` = Claude Sonnet 5.5, `lite` = Llama 3.1 8B bei Workers AI („Mit KI ausfüllen“). Die App schickt weiter das Anthropic-Format mit `model` = Stufe, das Backend übersetzt (Gemini native API mit JSON-Schema und Bildern, Workers AI über das `AI`-Binding, nur Text) und antwortet im Anthropic-Format. `lite` weist Bilder mit `400 images_not_supported` ab.
- **Abrechnung:** Preistabelle in µ€ je MTok in `backend/src/providers.ts` (Workers AI aus Neurons umgerechnet, Gemini-Preiserhöhung zum 1.1.2027 hinterlegt). Reservieren/Abrechnen, Marge und Store-Gebühr unverändert. Jede Verbrauchsbuchung hat Modell und Stufe (Migration `0003_ledger_tier.sql`).
- **Ausweichen:** Ausfall (5xx, 429, Zeitüberschreitung, Netzwerk) → `standard` ↔ `pro`, `lite` → `standard`. Der Nutzer zahlt nie mehr als die gewählte Stufe.
- **AI Gateway:** alle Anbieter-Aufrufe laufen über `overfit` bzw. `overfit-test` (Protokolle, Kosten, Rate-Limit 600/min). Fotos-Anfragen werden nicht protokolliert. Der Deploy-Workflow prüft die Token-Rechte und legt das Gateway idempotent an; fehlen Rechte, bricht er mit Anleitung ab.
- **Secrets:** neu `GEMINI_API_KEY`; `ANTHROPIC_API_KEY` ist optional (fehlt er, gibt es kein Pro). Mindestens einer ist Pflicht.
- **App:** Coach-Tab → Coach-Einstellungen → „Coach-Qualität: Standard / Pro“ mit Preishinweis (nur mit Fuel; Pro nur, wenn der Server es anbietet). „Mit KI ausfüllen“ nutzt mit Fuel `lite`. Eigener Key: unverändert, null Backend-Anfragen (OwnKeyPrivacyTest grün).
- Tests: `backend/test/tiers.test.ts` (jede Stufe, Fehler, Ausweichen, Rückerstattung, Gateway, Config, Preistabelle), App: `CoachTransportTest` (Stufenauflösung, Cache), `ExerciseAutofillTest`.
**Offen:** Echte Läufe gegen Gemini, Workers AI und das Gateway sind nur gemockt geprüft; der erste Deploy-Lauf zeigt, ob alles passt.

### Von Fabian zu tun (Session X)
1. **Cloudflare-Token erweitern:** dash.cloudflare.com → Profil → My Profile → API Tokens → beim bestehenden Deploy-Token **Edit** → unter Permissions zwei Zeilen ergänzen: **Account · Workers AI · Read** und **Account · AI Gateway · Edit** → Continue to summary → Update Token. Der Wert bleibt gleich, kein GitHub-Secret ändern. (Neuer Token: alle sechs Rechte, siehe `backend/README.md`.)
2. **Gemini-Key:** aistudio.google.com/apikey → Create API key (Projekt mit Abrechnung, Ausgabenlimit im Google-Cloud-Projekt setzen) → GitHub → Settings → Secrets and variables → Actions → New repository secret `GEMINI_API_KEY`.
3. `ANTHROPIC_API_KEY` kann bleiben (dann gibt es Pro) oder fehlen (nur Standard und Lite).
4. Actions → **Backend deployen** → Run workflow → `mode` = test. Die Zusammenfassung nennt Gateway und verfügbare Stufen.
5. Kosten und Protokolle: dash.cloudflare.com → AI → AI Gateway → `overfit-test`.

### Bitte auf dem Handy testen (Session X, nach dem Deploy)
1. Fuel als KI-Zugang, Coach-Tab → Coach-Einstellungen: „Coach-Qualität“ mit Standard (vorgewählt) und Pro, darunter der Preishinweis („Pro kostet … etwa das 3-Fache“). Ohne Anthropic-Key steht dort nur „Mit Fuel antwortet Gemini Flash (Standard).“
2. Workout auswerten lassen (Standard): Auswertung kommt, Kosten ≈ 1–2 ct. Dann Pro wählen, nochmal: Kosten höher (≈ 4 ct). Fuel → Buchungen zeigt beide Abbuchungen.
3. Coach-Chat mit Standard: Antwort kommt, Kosten darunter. Mit Körperfoto (Foto-Freigabe an): Antwort bezieht sich aufs Foto.
4. Eigene Übung → „Mit KI ausfüllen“: Felder werden gefüllt, Kosten im Bruchteil-Cent-Bereich.
5. Eigener Key statt Fuel: alles wie vorher, Coach-Einstellungen zeigen das Modellfeld statt „Coach-Qualität“.

## Deploy-Knopf und Datenschutz-Garantie (Session V, 3.11.)
**Erledigt:**
- **Deploy-Knopf** `.github/workflows/backend-deploy.yml` (Actions → „Backend deployen“ → Run workflow, `mode` test | production): legt D1 und R2 an, falls sie fehlen, spielt Migrationen ein, deployt, überträgt Secrets, prüft `/v1/health` und `/v1/config`, nennt die Worker-URL in der Zusammenfassung. Idempotent, `database_id` nur zur Laufzeit. Pflicht-Secret fehlt → Abbruch mit Anleitung.
- **Testbetrieb** (`mode = test`): keine Geräteprüfung, 1 € Test-Guthaben je neuem Konto, Eingabefeld `credit_eur` für mehr, niedrige Limits, `/v1/config` meldet `"mode":"test"`, die App zeigt „Testserver“ unter Fuel. Eigener Worker und eigene Datenbank, nie in Produktion. Tests: `backend/test/testmode.test.ts`.
- **Garantie eigener Key:** Zwei Abweichungen behoben (Profilzeile „Fuel“ und Fuel-Screen riefen das Backend schon beim Öffnen), Test `OwnKeyPrivacyTest` zählt Backend-Anfragen (= 0), Hinweis „Deine Daten gehen direkt an <Anbieter>, nicht über overfit-Server.“ im Coach-Tab.
- Optional: automatisches Deploy bei Änderungen unter `backend/`, nur mit Repo-Variable `BACKEND_DEPLOY_MODE`.
**Offen:** Store-Prüfer für Produktion (Phase 6/7). Der Workflow selbst ist nur gegen `wrangler deploy --dry-run` und Hilfsskripte geprüft, der erste echte Lauf braucht Fabians Cloudflare-Konto.

### Von Fabian zu tun: Backend im Browser deployen (Details: `backend/README.md`)
1. cloudflare.com → Konto anlegen (Free). Dashboard → **Workers & Pages** → workers.dev-Subdomain wählen. **R2 Object Storage** aktivieren (Free).
2. **Account ID** kopieren (Workers & Pages, rechts). **API-Token**: Profil → API Tokens → Create Custom Token mit *Workers Scripts: Edit*, *D1: Edit*, *Workers R2 Storage: Edit*, *Account Settings: Read*, *Workers AI: Read*, *AI Gateway: Edit* → kopieren.
3. GitHub → Settings → Secrets and variables → Actions → Secrets: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `GEMINI_API_KEY` und/oder `ANTHROPIC_API_KEY` (Session X).
4. Actions → **Backend deployen** → Run workflow → `mode` = test → in der Zusammenfassung die Worker-URL kopieren.
5. Settings → Secrets and variables → Actions → **Variables** → `OVERFIT_BACKEND_URL` = diese URL.
6. Nächsten App-Build abwarten (Merge nach main oder Actions → Android → Run workflow), APK aus „Overfit latest“ installieren.
7. Mehr Test-Guthaben: Run workflow mit `credit_eur` = 5.
Kosten: Cloudflare Free reicht; KI-Kosten nur bei Fuel (Test: höchstens 20 € Guthaben insgesamt); eigener Key kostet dich nichts.

### Bitte auf dem Handy testen (Session V)
1. Eigener Key (Profil → KI verbinden), Backend-URL gesetzt: Coach-Tab → „KI-Zugang“ zeigt „Eigener Key · …“ und darunter „Deine Daten gehen direkt an <Anbieter>, nicht über overfit-Server.“
2. Profil → „Fuel“ zeigt keinen Saldo; Tippen zeigt „Fuel ist optional …“ mit „Fuel ansehen“; erst das verbindet mit dem Server.
3. Mit Testserver: Fuel ansehen → „Testserver“-Hinweis, 1,00 € Saldo; „Fuel nutzen“ im Coach → Antwort kostet Cent-Beträge. Einladung einlösen klappt ohne Store-Konto.

## main wieder grün: Wackel-Tests nach #39 (Session S, 3.11.)
**Erledigt:**
- Ursache der roten Android-CI gefunden (siehe DECISIONS „Wackel-Tests nach #39“): Es waren zwei Rennen in den Tests. Ein App-Fehler war nicht die Ursache. Die Übung landete im Training, nur zu spät für den Test.
- Workout-Detail zeigt „Starten“ erst, wenn das Workout geladen ist (vorher war der Button kurz sichtbar, aber deaktiviert, und frühe Taps verpufften).
- Übungen aus dem Picker (auch frisch angelegte) fügt jetzt der WorkoutController in seinem eigenen Scope hinzu. Das Hinzufügen bricht also nicht mehr ab, wenn man das Training sofort minimiert.
- `CustomExerciseFlowTest` wartet auf den Kopf „1/5 erledigt“, den nur das Training mit beiden Übungen zeigt, und prüft dann den State. Neue Tests: `addPicked_*` im `WorkoutControllerTest`.
**Offen:** –

### Bitte auf dem Handy testen
1. Training starten, Satz 1 abhaken, „Übung hinzufügen“ → Suchtext eingeben → „… als neue Übung anlegen“ → Speichern: Du bist zurück im Training, die neue Übung steht unten, der Haken und die Pause laufen weiter. Direkt danach minimieren: Beim Öffnen ist die Übung trotzdem da.

## Tab „Coach“: Chat, sichtbare Prompts, KI-Zugang an einem Ort (Session P, 3.11.)
Erledigt:
- Neuer Tab **Coach** (Training, Verlauf, Coach, Fotos, Profil). Oben die Statuszeile KI-Zugang (eigener Key mit Anbieter, Fuel mit Saldo oder „Kein KI-Zugang“) → Einrichten/Ändern/Trennen.
- Auswertungen: letzte Auswertung, „Letztes Training auswerten“, „Fortschritt einschätzen“, alle Auswertungen, Gedächtnis mit Vorschau, Coach-Einstellungen.
- **Chat** mit dem Coach: mehrere Nachrichten hin und her, eigener Key oder Fuel; der Coach kennt Profil, Workouts, letzte Trainings, Fortschritt und Gedächtnis. Workout-Vorschläge erscheinen als Karte „Workout übernehmen“ → Import-Vorschau. Verlauf lokal (Room v6), „Neuer Chat“, „Chat löschen“ (zweimal tippen), frühere Chats, Kosten je Antwort bei Fuel, Demo ohne KI-Zugang, Fotos nur über „Foto anhängen“.
- **Prompts & Vorlagen** mit vollständigem Text (aufklappbar), „Kopieren“ und „Im Chat verwenden“: Workout per KI erstellen, Training auswerten lassen, Fortschritt der letzten 8 Wochen, Nächste Woche planen, Plateau durchbrechen, Kontext kopieren, Gem-/GPT-Anleitung. Dieselbe Anzeige beim Import („KI-Prompt kopieren“) und bei den KI-Chat-Verbindungen.
- KI-Chat-Verbindungen (Claude, ChatGPT, Gemini) und Notizen aus dem Chat sind im Coach-Tab; im Profil bleibt der Verweis „Coach & KI“ und Fuel.
- Tests: `CoachChatParserTest`, `CoachChatPromptTest`, `CoachPromptLibraryTest`, Verlauf in `CoachTransportTest`, `ChatContextTest` (common); `CoachTabTest`, Migration v5→v6, `DayWithOverfitTest` mit Chat-Schritt (Robolectric); bestehende UI-Tests auf den Tab umgestellt.

### Bitte auf dem Handy testen: Tab „Coach“
1. Tab-Leiste: Training, Verlauf, **Coach** (Sprechblase), Fotos, Profil.
2. Coach-Tab oben: „KI-Zugang“ zeigt „Eigener Key · Anthropic“ (bzw. dein Anbieter), „Fuel · x,xx €“ oder „Kein KI-Zugang“. Antippen → Einrichtung; „Trennen“ braucht zwei Tipps.
3. Darunter: letzte Auswertung, „Letztes Training auswerten“, „Fortschritt einschätzen“, „Coach-Gedächtnis“ (zeigt den Anfang des Textes).
4. Chat: „Was soll ich morgen trainieren?“ eingeben → „Senden“ → Chat öffnet sich, „Coach schreibt …“, dann die Antwort. Nachfrage stellen („und kürzer?“) → der Coach bezieht sich auf die erste Antwort.
5. „Erstell mir ein Push-Workout“ → Karte „Workout-Vorschlag: …“ → „Workout übernehmen“ → Import-Vorschau → „Übernehmen“ → steht im Training-Tab.
6. Mit Fuel: unter jeder Antwort klein „Kosten: …“. Ohne KI-Zugang (Key trennen): Hinweis und Demo-Antwort mit Beispiel-Workout.
7. „Foto anhängen“ → letzte Fotos erscheinen → eins antippen (Rahmen) → senden → „1 Foto angehängt“ steht an der Nachricht; ohne Auswahl geht kein Foto mit.
8. „Neuer Chat“ startet leer; zurück im Tab stehen frühere Chats. „Chat löschen“ → „Wirklich löschen?“ → weg.
9. App während „Coach schreibt …“ beenden und neu öffnen → die Antwort zeigt „Erneut versuchen“.
10. „Prompts & Vorlagen“: jeder Prompt zeigt seinen Text, „Mehr“ klappt ihn ganz auf; „Kopieren“ wird kurz zu „Kopiert“; in ChatGPT einfügen = genau der angezeigte Text. „Im Chat verwenden“ öffnet einen neuen Chat mit der Frage im Eingabefeld.
11. Training → „+“ → „KI-Prompt kopieren“ zeigt den Prompt ebenfalls an. Coach → Claude/ChatGPT/Gemini → „Kontext kopieren“ und Gem-Anleitung mit Text.
12. Profil: nur noch „Coach & KI“ und Fuel im Abschnitt KI.

## iPhone testbar machen (Session U, 3.11.)
**Erledigt:**
- iOS-CI startet die App im Simulator (iPhone 17 Pro) und fährt sie per XCUITest: Smoke-Flow (Workout starten, Satz abhaken, minimieren, wieder öffnen, beenden), Zurückwischen vom linken Rand, Training übersteht das Beenden der App. Dazu Screenshots aller Hauptscreens hell und dunkel (Artefakt `ios-screenshots`, Kopie in `docs/screenshots/ios/`), Homescreen-Icon und Launch Screen.
- QR-Scanner in der App auch auf dem iPhone (AVFoundation), damit lassen sich die kompakten `OF1:`-Codes importieren.
- Tipp auf die Pausen-Mitteilung öffnet das maximierte Training (auch wenn die App dabei startet).
- Statusleiste, Tastatur, Share-Sheet und Picker folgen dem App-Theme (Dunkel bei hellem iPhone und umgekehrt).
- `.overfit`-Dateien aus Dateien/WhatsApp/Mail werden als Kopie geöffnet. Anzeigename „overfit“.
- TestFlight-Workflow `ios-testflight.yml` (manuell, kein Mac nötig), Anleitung in der README unter „iPhone: TestFlight“.
- Paritäts-Audit Android ↔ iOS in DECISIONS.md.
**Offen:** +15 s/Überspringen direkt in der iOS-Mitteilung (Android hat das). Universal Links für den https-Link brauchen eine eigene Domain. Der Simulator hat keine Kamera, der Scanner ist nur auf dem echten iPhone prüfbar.

### Was Fabian für TestFlight tun muss (nur Browser)
1. developer.apple.com/programs → Enroll (99 €/Jahr), Bestätigung abwarten.
2. developer.apple.com/account → Membership details → Team ID notieren.
3. appstoreconnect.apple.com → Apps → „+“ → Neue App: iOS, Name „overfit“ (falls vergeben: „overfit Training“), Sprache Deutsch, Bundle-ID `app.overfit` (fehlt sie in der Liste: developer.apple.com → Identifiers → „+“ → App IDs → Explicit `app.overfit` anlegen), SKU `overfit`.
4. App Store Connect → Benutzer und Zugriff → Integrationen → App Store Connect API → Team-Schlüssel „+“, Zugriff **Admin** → `.p8` laden, Schlüssel-ID und Issuer-ID notieren.
5. GitHub → Repo → Settings → Secrets and variables → Actions → New repository secret: `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_P8` (Inhalt der .p8-Datei), `APPLE_TEAM_ID`.
6. GitHub → Actions → „iOS TestFlight“ → Run workflow. Nach 5–30 Minuten ist der Build in App Store Connect → TestFlight.
7. TestFlight → Interne Tests → Gruppe anlegen, dich hinzufügen, Build zuordnen. Am iPhone die App „TestFlight“ laden, anmelden, overfit installieren.

### Bitte auf dem iPhone testen (sobald TestFlight läuft)
1. Homescreen: Icon „of“; Start zeigt kurz den schwarzen Launch Screen mit „of“. Onboarding: Tastatur verdeckt „Weiter“ nicht.
2. Training starten → Mitteilungen erlauben. Satz abhaken → Haptik, Pause läuft. App in den Hintergrund, Bildschirm sperren: zum Pausenende kommt eine Mitteilung mit Ton/Vibration. Mitteilung antippen → das Training ist maximiert offen.
3. Im Training vom linken Rand nach rechts wischen → Training minimiert, Leiste unten. Leiste antippen → wieder offen. Übungskatalog öffnen und per Wischen zurück.
4. App aus dem App-Umschalter wischen und neu öffnen → das laufende Training ist da.
5. Profil → Darstellung Dunkel bei hellem iPhone: Statusleiste weiß, Tastatur dunkel; Hell entsprechend.
6. Workout teilen → QR. Zweites Gerät: Training → „+“ → Importieren → QR-Code scannen (Kamera erlauben) → Vorschau → Übernehmen.
7. „Als Datei teilen“ → an dich per WhatsApp/AirDrop → Datei antippen → Teilen → overfit → Vorschau. Ebenso aus der Dateien-App und „Datei auswählen“ im Import.
8. Beenden-Flow: Foto aufnehmen (Kamera) und aus der Mediathek wählen; Fotos-Tab zeigt es.
9. Coach → Neuer Chat → Eingabefeld: Tastatur schiebt das Feld nach oben, nichts verdeckt.
10. iPhone auf Englisch, overfit neu installieren → die App startet auf Englisch (danach in Profil umschaltbar).

## Teilen und Import verbessern, Beispiel-Workouts standardmäßig (Session Q, 3.11.)
**Erledigt:**
- Import: „Datei auswählen“ (System-Dateiauswähler); Android öffnet `.overfit`-Dateien aus Dateien/Downloads/WhatsApp (VIEW) und nimmt sie über „Teilen mit …“ an (SEND), danach dieselbe Vorschau. Backup-ZIP im Import → Hinweis.
- Schalter heißt „Gewichte und Wiederholungen mitsenden“, mit Erklärung und Vorschau „Das wird geteilt“.
- QR deutlich kleiner: Push 93→57, Pull 89→53, Legs 85→49 Module (ECC L, Base45/Alphanumeric, Binärform, Hash-IDs), runde Module, 200 dp. Generisches `QrView(text)`. Scanner liest alten Link und neues `OF1:`. Kurz-Code (`OFS:`) vorbereitet, Backend-Vertrag in docs/backend-api.md.
- Push/Pull/Legs sind beim ersten Start da (und einmalig bei bestehenden Installationen), löschbar, ohne Wiederkehr.
- QR-Generator-Fix: Maskenwahl wie ISO/ZXing (manche Symbole waren nicht lesbar).
- Tests: Base45/Payload (commonTest), `QrSizeTest`, `QrCompareTest`, `QrZxingTest`, `IncomingShareTest` (Intent), `SamplesSeedTest`, `ShareFlowTest` (Erststart + Löschen), `ShareImportTest`.
**Offen:** Kurz-Code braucht das deployte Backend; iOS ohne In-App-QR-Scanner (siehe DECISIONS „Zur Prüfung durch Fabian“).

### Bitte auf dem Handy testen: Teilen und Import
1. Frisch installiert (oder App-Daten löschen): Training-Tab zeigt Push, Pull, Legs. Pull öffnen → Bearbeiten → löschen (zweimal tippen); App komplett schließen und neu öffnen: Pull kommt nicht wieder. „+“ → Beispiel-Workouts hinzufügen bringt Pull zurück.
2. Workout öffnen → Teilen: oben „Das wird geteilt“, QR ist kleiner und rund; mit dem Scanner in Overfit (zweites Handy: Training → „+“ → Importieren → QR-Code scannen) lesen und importieren.
3. Verlauf → Training → Teilen: Schalter „Gewichte und Wiederholungen mitsenden“ an/aus, Erklärung und Vorschau wechseln mit.
4. „Als Datei teilen“ → an dich per WhatsApp senden → Datei in WhatsApp antippen → „Öffnen mit Overfit“ (oder Teilen → Overfit) → Vorschau erscheint. Genauso aus Downloads.
5. Importieren → „Datei auswählen“ → die .overfit-Datei wählen → Vorschau. Backup-ZIP wählen → Hinweis „Das ist ein Backup“.

## UI-Feinschliff (Session R, 3.11.)
**Erledigt:** Button-Hierarchie (Primary/Secondary/Tertiary/Destructive/Confirm), 48/40-dp-Höhen, Chevron + Pressed-State für klickbare Zeilen, Chips/Segmente auf `surface`, schlankere Workout-Ansicht (ein Kopf, kompakter Fuß, 48-dp-Haken in Grün), Bestätigungsmuster mit Farbe überall, Diary zeigt mindestens 4 Monate inkl. leere. Vorher/Nachher-Screenshots: `docs/screenshots/before|after` (Test `ScreenshotTest`, nur mit `SCREENSHOT_DIR=…`). Mängelliste in DECISIONS.md.

### Bitte auf dem Handy testen: UI-Feinschliff
1. Training-Tab: Workout-Zeilen und Coach/Duo (beide entfernt 3.11., Fabian) haben Chevron; beim Drücken färbt sich die Zeile grau.
2. Workout öffnen: „Bearbeiten“ und „Teilen“ sind graue Buttons.
3. Training starten: Kopf ist eine schmale Zeile (Minimieren, Name + Uhr, „Beenden“). Mehr Platz für Sätze, oben kaum Leerraum.
4. Satz abhaken: großer Kreis, danach grün. „Beenden“ antippen: Button wird grün mit „Beenden? Nochmal tippen“, Name weicht kurz.
5. Ganz unten „Training verwerfen“ ist grau, nach Tipp rot mit „Nochmal tippen“. Gleiches bei Löschen (Training, Workout, Übung) und Profil → Reset.
6. Fotos-Tab: auch bei nur einem Training erscheinen die letzten 3 Monate plus der aktuelle, leere Monate inklusive.
7. Profil: Sprache/Darstellung (Segmente) liegen auf grauer Fläche; in Hell und Dunkel prüfen.

## Foto-Tagebuch im Stil von BeReal „Memories“ (Session M, 3.11.)
**Erledigt:** `ui/photos` umgebaut, keine Schemaänderung.
- Monatsweises Kalender-Raster (7 Spalten, Wochenstart DE Montag / EN Sonntag), neuester Monat zuerst, Monatsname groß.
- Tag mit Foto: Hochformat-Kachel mit runden Ecken (zentriert zugeschnitten), Tageszahl darüber, Zähl-Badge bei mehreren Fotos. Training ohne Foto: graue Kachel (`surface`). Sonst nur Zahl in `faint`; heute mit Haarlinie.
- Kopf: Trainings gesamt, Fotos gesamt, Serie in Wochen. Leerer Zustand mit Hinweis aufs Foto beim Beenden.
- Vollbild: Wischen links/rechts = nächster/vorheriger Foto-Tag, mehrere Fotos eines Tages vertikal (mit Punkten). Darunter Datum, Training, Dauer, Volumen, neue Bestwerte, „Training ansehen“, „Foto teilen“ (neu), „Foto löschen“ (Bestätigungsmuster).
- „Vergleichen“ bleibt (wählt das letzte Foto eines Tages).
- Logik in `domain/DiaryCalendar.kt` (Raster, Wochenstart, Serie), Tests: `DiaryCalendarTest` (common), `DiaryScreenTest` (Robolectric).

### Bitte auf dem Handy testen: Foto-Tagebuch
1. Fotos-Tab: Monatsraster, aktueller Monat oben; heute hat eine Haarlinie. Kopf zeigt Trainings, Fotos, Wochen-Serie.
2. Tage mit Foto zeigen die Kachel, mit Training ohne Foto grau; Tipp auf grauen Tag öffnet das Training.
3. Tipp auf Foto-Tag: Vollbild. Nach links/rechts wischen springt zum nächsten/vorherigen Foto-Tag; bei mehreren Fotos senkrecht blättern.
4. Unter dem Foto: Dauer, Volumen, ggf. neue Bestwerte. „Training ansehen“ führt in den Verlauf; „Foto teilen“ öffnet das Share-Sheet; Löschen braucht zwei Tipps.
5. Sprache auf English: Woche beginnt am Sonntag.
6. Scrollen durch viele Monate ruckelt nicht.

## Tab „Training“: Workouts statt Vorlagen (Session O, 3.11.)
Erledigt: Tab 1 heißt „Training“ (EN „Workouts“) und zeigt die eigenen Workouts mit „zuletzt vor N Tagen“; „+“ → „Workout erstellen“ oder „Leer starten“, darunter Import/KI-Prompt/Beispiele; Workout-Seite mit „Starten“, „Bearbeiten“, „Teilen“. „Vorlage“ ist aus allen Texten raus. Beim Beenden oder Verwerfen übernimmt das Workout die Struktur des Trainings automatisch, ein leer gestartetes Training wird ein Workout (Name im Beenden-Flow). Übungen sind kein Tab mehr, sondern über das Hantel-Icon im Training-Tab erreichbar. Tests: `WorkoutPlanTest`, `WorkoutControllerTest`, `RepositoryTest`, neuer `TrainingTabTest`, bestehende UI-Tests angepasst.
Offen: Abstimmung mit Session L (Mini-Leiste, „Training beenden“ auf dem Tab) beim Zusammenführen.

### Bitte auf dem Handy testen: Tab „Training“
1. Erster Tab heißt „Training“ (Blitz-Icon). Leer: „Noch keine Workouts …“ und „Beispiel-Workouts hinzufügen“ → Push/Pull/Legs erscheinen mit „noch nie trainiert“
2. „+“ oben rechts → „Workout erstellen“: Name, Übungen, Sätze → Speichern → steht in der Liste, kein Training gestartet
3. „+“ → „Leer starten“ → Übungen hinzufügen, Sätze abhaken → „Beenden“ zweimal → oben Name „Workout vom 3. Nov.“ ändern (z. B. „Brust“) → Speichern → „Brust“ steht in der Liste mit „zuletzt heute“
4. „Brust“ antippen → „Starten“ → Übungen sind da, Werte von eben vorausgefüllt
5. Im Training eine Übung hinzufügen, einen Satz hinzufügen, umsortieren → Beenden → Workout öffnen: neue Struktur ist übernommen, ohne Nachfrage
6. Workout öffnen → „Bearbeiten“ → „Workout löschen“ (zweimal tippen) → Verlauf zeigt die alten Trainings weiterhin
7. Tab-Leiste hat nur noch Training, Verlauf, Fotos, Profil. Hantel-Icon oben im Training-Tab → Übungskatalog (suchen, „Eigene Übung“, Übung → Fortschritt), Zurück führt zum Training. Nirgends steht mehr „Vorlage“ (auch Teilen, Import, Coach, KI-Chat), in Englisch nirgends „template“

## Feedback-Runde 3.11. – Session K: Katalog ohne free-exercise-db, Übungen per KI, eigene Übungen im Workout

Erledigt:
- free-exercise-db komplett entfernt: Katalog nur noch Workout Guide (302 Übungen mit SVG), Backend-Katalog neu,
  „Alle Bilder offline laden“ und Bilder aus dem Netz weg, Lizenzhinweise aktualisiert (Workout Guide/Everkinetic, CC BY-SA 4.0).
- Datenbank v5: benutzte `fedb:`-Übungen bleiben (gleiche ID) als eigene Übung ohne Bild, unbenutzte sind gelöscht.
  Migrationstest `migration4to5ConvertsUsedFreeDbExercisesAndDeletesUnusedOnes`.
- Eigene Übung: Haupt- und Hilfsmuskeln, Equipment, Erfassungsart, Pause, Ausführungsnotiz; „Mit KI ausfüllen“ (eigener
  Key oder Fuel, Structured Output), „Meintest du …?“ gegen Dubletten.
- Übungs-Picker (Vorlage und Workout-Modus): „Neue Übung anlegen“ mit demselben Formular; die Übung landet im Katalog
  und direkt im Training bzw. in der Vorlage.
- Teilen: eigene Übungen übertragen alle Felder (abwärtskompatibel), beim Import keine Dublette bei gleichem Namen.
  Alte Links mit `fedb:`-IDs: Fallback über den Namen (docs/share-format.md).

### Bitte auf dem Handy testen (Session K)
1. Update installieren, App öffnen: Übungen → Katalog zeigt nur noch Grafik-Übungen (302). Hattest du eine
   free-exercise-db-Übung in einer Vorlage oder einem Training: sie ist noch da, als „Eigene Übung“ mit Buchstaben-Kachel.
2. Profil → „Alle Bilder offline laden“ gibt es nicht mehr; Profil → Über: nur noch Workout Guide als Bildquelle.
3. Übungen → „Eigene Übung“ → Name „Bizepscurl am Kabel“ eingeben → unter „Meintest du …?“ erscheinen ähnliche Übungen
   (antippen öffnet sie). Zurück, Name wieder eingeben → „Mit KI ausfüllen“ (mit Key oder Fuel) → nach ein paar Sekunden
   sind Erfassungsart, Haupt-/Hilfsmuskeln, Equipment, Pause und Ausführung gefüllt → etwas ändern → Speichern →
   Übung öffnen: Notiz steht unter den Muskeln.
4. Ohne KI-Zugang (KI trennen): im Formular steht statt des Knopfs ein Hinweis, alles lässt sich von Hand ausfüllen.
5. Training starten → „Übung hinzufügen“ → suchen („Turkish Get-up“) → letzte Zeile „„Turkish Get-up“ als neue Übung
   anlegen“ → ausfüllen (gern mit KI) → Speichern → die Übung steht sofort im laufenden Training; Pausentimer und
   Benachrichtigung laufen dabei weiter. Dasselbe im Vorlagen-Editor.
6. Vorlage mit dieser eigenen Übung teilen → auf zweitem Gerät (oder nach Löschen der Übung) importieren → die Übung
   kommt mit Muskeln, Equipment, Erfassungsart, Pause und Notiz an; nochmal importieren → keine zweite gleichnamige Übung.

## 3.11. – KI-Kontext, Auswertung je Übung, Gemini (Session N)
Erledigt:
- **Profil überall:** Name, Alter und aktuelles Körpergewicht (Profil-Einheit) gehen über jeden KI-Weg mit: Coach (eigener Key und Fuel), „Kontext kopieren“, „KI-Vorlage kopieren“, Gem-/GPT-Anleitung (Zeile „Über mich“) und KI-Chat (jede MCP-Tool-Antwort beginnt mit `user`). Alle Wege nutzen eine gemeinsame Funktion (`aiProfileFacts`), fehlende Werte fallen weg.
- **Auswertung je Übung:** kurzes Gesamtfazit oben, darunter je Übung heute gegen letztes Mal (bester Satz, Sätze, Volumen, e1RM bzw. Wdh./Zeit). Dazu farbige Deltas, ein Badge (Besser grün, Schlechter rot, Gleich grau), ein KI-Kommentar und „Nächstes Mal: kg × Wdh.“. Die Zahlen rechnet die App lokal, deshalb erscheinen sie auch offline, ohne KI und in der Demo. Keine Schemaänderung (Room bleibt v4, v6 nicht gebraucht).
- „Neue Bestwerte“ beim Beenden zeigen das Plus grün, der Fortschritt je Übung zeigt Deltas zum vorherigen Training in Grün/Rot.
- **Gemini als Anbieter:** eigener Google-AI-Studio-Key (`AIza…`), Key-Prüfung ohne Token-Kosten, Standardmodell `gemini-3.6-flash`, Structured Output über den OpenAI-kompatiblen Endpunkt (Begründung in DECISIONS.md).
Offen: Optik auf dem Handy prüfen. Mit echtem Gemini-Key ist die Strecke nur über Tests (gemockte Antworten) geprüft.

### Bitte auf dem Handy testen (Session N)
1. Profil → KI verbinden → „Gemini“ → Key von aistudio.google.com/apikey einfügen → „Verbunden“. Ein falscher Key zeigt „ungültig“.
2. Ein Training (z. B. Push) zweimal beenden, beim zweiten Mal bei einer Übung mehr Gewicht, bei einer weniger → „Senden“ → Coach-Auswertung: oben 1–2 Sätze, darunter „Je Übung“ mit Heute/Letztes Mal, grünem „Besser“, rotem „Schlechter“, grauem „Gleich“, Kommentar und „Nächstes Mal: … kg × …“.
3. Flugmodus an, Training beenden und senden → „Wartet auf Verbindung“, darunter trotzdem „Je Übung“ mit den Zahlen.
4. KI trennen → Verlauf → Training → Coach-Auswertung → „Demo-Auswertung ansehen“: je Übung mit Demo-Kommentar.
5. Beenden-Zusammenfassung: „Neue Bestwerte“ mit grünem „+x“. Übungen → Übung → Fortschritt: Deltas grün/rot.
6. Profil → KI-Coach → „Kontext kopieren“, „Gem-/GPT-Anleitung kopieren“ und Übungen → „KI-Vorlage kopieren“ jeweils in Notizen einfügen: oben Name, Alter und Gewicht.
7. Mit Backend: im verbundenen Chat „Wer bin ich?“ fragen → Name, Alter und Gewicht kommen ohne weiteres Zutun.

## Session L: Workout minimieren (3.11.)
Erledigt: Minimieren-Knopf im Workout (Pfeil runter) und System-Zurück minimieren; Leiste über der Tab-Leiste mit Training, Übung/Satz, Zeit oder Pausen-Countdown (tabellarische Ziffern); Tipp/Wischen öffnet; Start-Tab zeigt bei laufendem Training „Zum laufenden Training“ / „Laufendes beenden“; Tipp auf Benachrichtigung öffnet das maximierte Training. Tests: `MinimizeWorkoutTest`, `DayWithOverfitTest` (Minimier-Schritt).
**Bitte auf dem Handy testen:**
1. Training starten, Satz abhaken (Pause läuft) → Pfeil oben links tippen → Start-Tab: unten über den Tabs die Leiste mit Countdown
2. Durch Verlauf, Übungen (Katalog, Vorlagen), Fotos, Profil tippen: Leiste bleibt; Pausenende vibriert wie bisher
3. Leiste antippen und noch einmal minimieren, dann nach oben wischen → Training offen. System-Zurück-Geste im Training → minimiert, Training läuft weiter
4. Pause beenden: Leiste zeigt Übung und Satz statt Countdown. Start-Tab: „Laufendes beenden“ zweimal tippen → Beenden-Flow
5. Minimiert in anderen Tab, Bildschirm sperren, Benachrichtigung tippen → maximiertes Training. App aus den letzten Apps wischen und neu öffnen → Training maximiert

## Morgen-Testliste für Fabian (3.11.)
Reihenfolge wie ein echter Tag. Die Einzel-Abschnitte weiter unten haben mehr Details. Ohne Backend gehen alle Punkte außer 34–35 (und echtes Guthaben); die Demo in 36–38 braucht keinen Server.
Der automatische Test „Ein Tag mit Overfit“ (`DayWithOverfitTest`) läuft diese Kette im Emulator durch (ohne Kamera, Notifications, echtes Netz).

**Installieren und starten**
1. github.com am Handy einloggen → Releases → „Overfit latest“ → `overfit.apk` laden und installieren (Play Protect: „Trotzdem installieren“)
2. Onboarding: Name, Alter, Gewicht (kg/lb umschaltbar), KI: „Später einrichten“ → Start-Tab zeigt „Hallo, <Name>“
3. Profil: Sprache Deutsch/English umschalten (alle Tabs prüfen), Hell/Dunkel/System, wieder Deutsch

**Vorlagen (Push/Pull/Legs)**
4. Start → „Beispielvorlagen hinzufügen“ → Push, Pull, Legs erscheinen; Profil → Beispielvorlagen nochmal → „schon da“, nichts doppelt
5. Übungen → Katalog: suchen („Klimmzug“ findet Pull-up), Filter, Übung öffnen, Erfassungsart ändern, „Eigene Übung“ anlegen
6. Übungen → Vorlagen → Push öffnen: Sätze/Pause ändern, Übung ziehen (zwei Striche), ersetzen, Speichern

**Trainieren**
7. Start → „Training starten“ → Push. Benachrichtigungen erlauben (Android 13+)
8. Satz abhaken → leichte Vibration, Pausenleiste; antippen (groß/klein), −15 s, +15 s, Überspringen
9. Werte ändern („92,5“, „±“), Satz hinzufügen/duplizieren/löschen, Übung hinzufügen/ersetzen/entfernen/umsortieren (ziehen)
10. Pause starten, Bildschirm sperren: Countdown in der Benachrichtigung, am Ende drei Pulse; +15 s/Überspringen in der Benachrichtigung.
    Falls Profil „Pausenende sekundengenau → Erlauben“ zeigt: erlauben
11. App aus den letzten Apps wischen, neu öffnen → Training (und Pause) läuft weiter
12. Profil → Vibration „Aus“ → Abhaken vibriert nicht; wieder „An“

**Beenden, Verlauf, Fotos**
13. „Beenden“ zweimal tippen → Zusammenfassung (Dauer, Sätze, Volumen); Gefühl 1–5, Notiz schreiben
14. „Foto aufnehmen“ (Kamera erlauben) und „Aus Fotos wählen“ → Vorschau; eins entfernen; Android-Zurück-Taste speichert Notiz/Gefühl
15. „Speichern“ → Start. Gleiches Training nochmal mit mehr Gewicht → „Neue Bestwerte“ nennt die Übung
16. Verlauf-Tab: Training öffnen → Sätze, Notiz, Fotos; Foto → Vollbild; „Als Vorlage übernehmen“; Übung → „Fortschritt“
17. Fotos-Tab: Zeitachse, „Vergleichen“ → zwei Fotos nebeneinander; Foto löschen (zweimal tippen); Fotos nicht in der Galerie

**Coach (mit eigenem Key oder Fuel; ohne Key nur Demo)**
18. Profil → KI verbinden → Anthropic + eigener Key (oder Fuel). Kein Key? Dann Punkt 24 (Demo)
19. Training beenden: im Abschnitt „Coach“ ist „Senden“ gewählt → „Speichern“ → nach 10–40 s Auswertung (Verlauf → Training → „Coach-Auswertung“
    oder Start → „Coach“)
20. „Nächstes Mal“: einzelne Änderungen abwählen → „N übernehmen“ / „Alle annehmen“ → Vorlage öffnen: Werte geändert; Start zeigt „Letzte Coach-Tipps“
21. Flugmodus an → Training beenden und senden → „Wartet auf Verbindung“; Flugmodus aus, App schließen/öffnen → Auswertung kommt von selbst
22. Profil → Coach → „Fortschritt bewerten“; Profil → Coach-Gedächtnis ansehen/ändern/Versionen/löschen (zweimal tippen)
23. Profil → KI-Coach → Coach-Einstellungen: Modell, Fotos an Coach (Standard aus), Anweisungen ändern/zurücksetzen
24. KI trennen → Verlauf → Training → „Coach-Auswertung“ → „Kein KI-Zugang“ → „Demo-Auswertung ansehen“ (als Demo markiert)

**Teilen und Importieren**
25. Vorlage → „Teilen“: QR, „Link teilen“ (WhatsApp/Notizen), „Link kopieren“; QR mit zweitem Handy scannen → Web-Seite/„In Overfit öffnen“
26. Übungen → Vorlagen → „Vorlage importieren“: „QR-Code scannen“ (Kamera erlauben), „Aus Zwischenablage importieren“ → Vorschau → „Übernehmen“
27. Verlauf → Training → „Teilen“ (Schalter „Mit Ist-Werten“) → Link kopieren → importieren: wird eine Vorlage
28. „Als Datei teilen“ → `.overfit` antippen → Overfit öffnet die Vorschau
29. „KI-Vorlage kopieren“ → in ChatGPT/Claude/Gemini einfügen, Fragen beantworten, ```json-Antwort kopieren → „Aus Zwischenablage importieren“
30. Leere Zwischenablage / beliebiger Text → verständliche Fehlermeldung
31. (entfernt 3.11., Fabian; später evtl.) Start ganz unten: „Duo · bald“ → Erklärtext, keine Eingabe

**KI-Chat, Kontext kopieren**
32. Profil → KI-Coach → Gemini (oder Claude/ChatGPT): „Kontext kopieren“ → in Notizen einfügen: Profil, letzte Trainings, ```json-Block
33. „Gem-/GPT-Anleitung kopieren“ → einfügen und lesen
34. Mit Backend: „KI-Chat verbinden“ → Zustimmen → Kopplungscode, Connector im Chat anlegen, „Plane mir ein Push-Training“ → Eingang auf dem Start-Tab → Vorschau → Übernehmen
35. „Trennen“ und „KI-Chat trennen“ (zweimal tippen) → Chat hat keinen Zugriff mehr

**Guthaben**
36. Profil → „Guthaben · startet bald“ → „Demo ansehen“: „4,20 € Guthaben“, Pakete („Bald im Store“), Buchungen (nur Claude-Modelle), Wiederherstellungscode
37. (entfällt: Einladungen sind seit 7.11.2026 entfernt)
38. „Demo beenden“ → Guthaben zeigt wieder „startet bald“. (Echtes Guthaben erst mit Backend, Punkt 1 unten)

**Backup und Löschen**
39. Profil → „Backup exportieren“ → in Dateien/Drive sichern (Datei lässt sich als ZIP öffnen, kein API-Key drin)
40. Profil → „Alle Daten löschen“ (zweimal tippen) → Onboarding; durchklicken, Profil → „Backup importieren“ → Datei wählen → Vorschau → „Alle Daten ersetzen“ (zweimal)
    → Verlauf, Fotos, Vorlagen, Coach-Gedächtnis, Name wieder da
41. Update-Test: später ein neues APK aus „latest“ drüberinstallieren → alle Daten bleiben

### Von Fabian zu tun (nach Wichtigkeit, dedupliziert)
Vor dem Testen am Handy ist nichts nötig. Sortiert nach Wichtigkeit:
1. **Cloudflare-Backend deployen** – jetzt per Deploy-Knopf im Browser, siehe Abschnitt „Session V“ oben bzw. `backend/README.md`. (Alter Weg per Terminal: Konto, `wrangler login`, `wrangler d1 create overfit` (ID in
   `wrangler.jsonc`), Migrationen (inkl. `0002_chat.sql`), Secrets `ANTHROPIC_API_KEY`, `SPOT_SIGNING_KEY`, `SYNC_ENCRYPTION_KEY`
   (32 Zufallsbytes Base64url, nie ändern), R2 aktivieren + `wrangler r2 bucket create overfit-chat-photos`, `npm run deploy`. Danach
   GitHub → Settings → Secrets and variables → **Variables** → `OVERFIT_BACKEND_URL` = Worker-URL. Ohne das zeigt die App „startet bald“ + Demo
   (Guthaben und KI-Chat-Verbindung bleiben dann lokal/ohne Server.)
2. **GitHub Pages aktivieren** (Repo → Settings → Pages → Source „GitHub Actions“; oder Actions → „Web veröffentlichen“ → Run workflow):
   Web-Fallback `https://bigfire3.github.io/overfit/` und Teilen-Seite `/t` für Links ohne App
3. **KI-Chat-Connectoren eintragen** (nach 1.): Claude → Connectors → Custom Connector, ChatGPT → Entwicklermodus → Connector,
   Gemini → Eigene Apps, jeweils `https://<worker>/mcp`; Schritte stehen in der App unter Profil → KI-Coach
4. **Zu prüfen (Annahmen von Session G):** Standardmodelle OpenAI `gpt-5` und OpenRouter `anthropic/claude-sonnet-5.5` sind geraten – in
   Profil → KI-Coach → Coach-Einstellungen mit echtem Key ausprobieren und ggf. korrigieren; „Zur Prüfung durch Fabian“ in `docs/DECISIONS.md` lesen
5. **Datenschutzerklärung vor dem Store-Start** um KI-Chat-Verbindung (Art. 9 DSGVO, Opt-in) und Daten an Chat-Anbieter ergänzen
6. **Store-Konten (Phase 6/7):** Google-Play-Console und Apple-Developer-Konto, Play Billing / StoreKit,
   Server-Prüfer in `backend/src/verifiers.ts`. Store-Texte DE/EN liegen als Entwurf in `docs/store/store-listing.md`
7. **Eigene Domain** (optional, später): Android App Links / iOS Universal Links (`/.well-known/`, Associated Domains), Web-Seite für Links
   ohne installierte App
8. iOS: Kamera, Fotoauswahl, Dateiauswahl und Haptik sind nur in der iOS-CI gebaut, nicht auf einem echten iPhone getestet

## Nacht 3.11. – KI-Chat-Verbindungen, Teil 2: App-Seite (Session H, Phase 10b)
Fertig:
- **Profil → KI-Coach**: die fünf Wege (eigener Key, Fuel | Claude, ChatGPT, Gemini) mit Status; je Chat Kurzanleitung, Server-Adresse
  `<Backend>/mcp` zum Kopieren, Gemini zusätzlich Gem-Ersatzweg. Ohne Backend-URL: „startet bald“, aber alles Lokale geht
- **KI-Chat verbinden**: Einwilligungsdialog (Hochladen, verschlüsselt, Abschalten löscht, Daten gehen an den Chat-Anbieter) mit
  Häkchen „Körperfotos teilen“ (Standard aus) → `enable` → Erst-Upload (`reset`, Pakete ≤ 500, Fortschritt)
- **ChatSync** (`shared/.../chat/`): Fingerabdruck-Abgleich je Dokument (profile, template, workout; memory vorbereitet), gebündelt
  höchstens alle 10 s, sofort beim Beenden und beim Wechsel in den Hintergrund, Löschungen über `deletes`, Offline-Warteschlange
  (nichts geht verloren, nächster Lauf sendet die Differenz), Hinweis bei `sync_quota_exceeded`
- **Mit Chat verbinden**: Kopplungscode groß (Inter, tabellarisch), 10-min-Countdown, „Neuer Code“, Abfrage bis „Verbunden mit …“;
  Verbindungsliste mit „Trennen“, Foto-Schalter nachträglich, „KI-Chat trennen“ (zweimal tippen, löscht alles)
- **Eingang**: bei Start und Rückkehr in den Vordergrund; Start-Tab zeigt „N neue Einträge aus dem Chat“. Vorlagen → vorhandene
  Import-Vorschau mit Chat-Nachricht, bei `replacesTemplateId` „Vorlage aktualisieren“ (Diff) neben „Als neue Vorlage“;
  Notizen übernehmen/verwerfen, Liste „Notizen aus dem Chat“ (Profil); `ack` auch für Verworfene (offline nachgeholt)
- **Kontext kopieren** + **Gem-/GPT-Anleitung kopieren** (Ersatzweg ohne Server, DE/EN je App-Sprache)
- Tests: Mapping App→Sync-Dokument, Batches, Sync (virtuelle Zeit, Offline, Quota, Reset, Löschen, Fotos), Client gegen MockEngine,
  Inbox → Vorschau + Diff, Kontext-Text, UI-Durchlauf (ohne und mit gemocktem Backend)

Offen / Hinweise:
- **Foto-Upload** (Phase 3, Session F): Schnittstelle `ChatDataSource.photos()/photoJpeg()` und `ChatApi.putPhoto` stehen, im Sync
  verdrahtet (nur mit Häkchen); nach F's Merge `AppChatSource` an `WorkoutRepository.photos`/`readPhoto` anschließen (verkleinern ~1500 px)
- **Coach-Gedächtnis** (Phase 4, Session G): `ChatFeature(memory = …)` und `sync.markMemory()` anschließen, sobald es gemergt ist
- **Notizen am Training**: `ChatFeature.notes.forWorkout(workoutId)` liefert die Notizen; im Verlauf (Phase 3) anzeigen
- Workout-/Vorlagen-IDs: Training = Session-UUID, Vorlage = lokale Nummer als Text (kommt als `replacesTemplateId` zurück)

### Von Fabian zu tun (KI-Chat, App-Seite)
Nichts Neues; Voraussetzung bleibt das Backend-Deployment aus Teil 1 (Worker-URL als GitHub-Variable `OVERFIT_BACKEND_URL`, R2-Bucket,
`SYNC_ENCRYPTION_KEY`). Danach die Chat-Connectoren eintragen (Schritte stehen in der App unter Profil → KI-Coach).

### Bitte auf dem Handy testen (KI-Chat, App-Seite)
Ohne Backend (geht sofort):
1. Profil → **KI-Coach**: oben „Eigener API-Key“/„Fuel“, unten Claude, ChatGPT, Gemini mit „startet bald“
2. **Gemini** öffnen: Text „Startet bald“, Anleitung, Ersatzweg „eigenes Gem“. **Kontext kopieren** → in ein Notizfeld einfügen: Profil,
   letzte Trainings, Fortschritt, Antwortformat als ```json-Block. **Gem-/GPT-Anleitung kopieren** → einfügen und lesen
3. In Gemini/ChatGPT/Claude den Kontext einfügen, um eine Vorlage bitten; Antwort kopieren → Übungen → Vorlagen → Importieren → Zwischenablage
Mit Backend (nach Deployment):
4. Claude → **KI-Chat verbinden** → Hinweise lesen, Foto-Schalter bleibt aus → „Zustimmen und verbinden“ → Fortschritt, dann „KI-Chat ist eingeschaltet“
5. „Code anzeigen“ → großer Code + Countdown; in claude.ai den Connector mit der Adresse aus der App anlegen, Code eintippen → in der App „Verbunden mit Claude“
6. Im Chat „Plane mir ein Push-Training“ → Vorlage erscheint auf dem Start-Tab („1 neuer Eintrag aus dem Chat“) → Vorschau → Übernehmen
7. Ein Training beenden → nach wenigen Sekunden sieht der Chat es („Wie war mein letztes Training?“). Flugmodus an, Satz abhaken, Flugmodus aus, App öffnen → Hinweis verschwindet, Daten kommen an
8. **Trennen** einer Verbindung; **KI-Chat trennen** (zweimal tippen) → Server leer, Chat hat keinen Zugriff mehr

## Phase 4: KI-Coach (Session G, Nacht 3.11.)
Fertig:
- Coach-Auswertung eines beendeten Trainings: Bewertung 1–10, Zusammenfassung, Gut / Besser machen / Tipps, Foto-Feedback
  (nur mit Einwilligung), **Plan fürs nächste Mal als Diff** („Bench Press: 3 × 8 · 60 kg → 3 × 8 · 62,5 kg“, Neu, Entfällt,
  Pause) mit Alle annehmen / einzeln / Ablehnen → Vorlage wird angepasst; freies Training → „Als Vorlage speichern“
- „Fortschritt bewerten“: Einschätzung der letzten 8 Wochen inkl. Verlauf der Hauptübungen (e1RM)
- Anbieter: eigener Key (Anthropic direkt, OpenAI, OpenRouter) und Fuel (Kosten + Saldo unter der Auswertung);
  Structured Outputs, robuster Parser, Prompt-Caching, Antwortsprache = App-Sprache
- Warteschlange: ohne Netz/Timeout/Überlastung wartet die Anfrage und geht beim nächsten Öffnen der App automatisch raus;
  sonst Fehlertext mit „Erneut versuchen“ (Rohantwort einsehbar)
- Coach-Gedächtnis: lesen, bearbeiten, Versionen wiederherstellen, löschen; der Coach überarbeitet es nach jeder Auswertung
- Coach-Einstellungen: Modell je Anbieter, „Körperfotos an Coach senden“ (Standard aus), Coach-Anweisungen editierbar;
  erreichbar über Profil → KI-Coach (Seite der fünf Wege) und Profil → Coach
- Coach-Gedächtnis geht bei verbundener KI-Chat-Verbindung als `memory/coach` mit (Sync nach jeder Änderung)
- Ohne KI-Zugang: Hinweis mit „KI verbinden“ / „Fuel nutzen“ und eine als Demo markierte Beispiel-Auswertung
- Einstieg: am Ende des Beenden-Flows „An Coach senden: Senden / Später“ (Senden beim Speichern), Verlauf → Training →
  „Coach-Auswertung“, Start: „Letzte Coach-Tipps“ + Zeile „Coach“, Profil → Coach
- Backup enthält Coach-Gedächtnis (alle Versionen), Auswertungen und Coach-Einstellungen (keine Keys)
- Room v4 (`coach_review`, `coach_memory`, nur neue Tabellen, Migrationstest 3→4)
- Tests: Parser (kaputte/abweichende Antworten), Request-Aufbau, Plan-Diff + Anwenden, Provider gegen MockEngine,
  Warteschlange/Service mit Room, UI-Durchlauf mit Fake-Provider

Offen / Hinweise:
- Kein Senden bei geschlossener App (kein WorkManager), siehe DECISIONS
- Fotos gehen nur mit Einwilligung mit (Profil → KI-Coach → Coach-Einstellungen), erstes Foto des Trainings + Vergleichsfoto ≥ 4 Wochen

## Bitte auf dem Handy testen: KI-Coach (Phase 4)
1. Profil → KI verbinden → Anthropic + eigener Key (oder Fuel)
2. Training aus einer Vorlage (z. B. Push) machen, ein paar Sätze abhaken, „Beenden“ → unten im Abschnitt „Coach“ steht
   „Senden“ ausgewählt → „Speichern“
3. Verlauf → das Training → „Coach-Auswertung“ (oder Start → „Coach“ → „Letztes Training auswerten“) → nach 10–40 s
   Bewertung, Tipps und „Nächstes Mal“ mit Änderungen
4. Einzelne Änderungen abwählen (Kreis rechts) → „N übernehmen“ (oder „Alle annehmen“) → Vorlage öffnen: Werte geändert
5. Start zeigt unter „Letzte Coach-Tipps“ die Tipps
6. Flugmodus an → noch ein Training beenden und senden → „Wartet auf Verbindung“; Flugmodus aus, App schließen und
   öffnen → Auswertung kommt von selbst
7. Profil → Coach → „Fortschritt bewerten“
8. Profil → Coach-Gedächtnis: Text ansehen, ändern, Speichern; Versionen wiederherstellen; löschen (zweimal tippen)
9. Profil → KI-Coach → Coach-Einstellungen: Modell, Fotos (Standard aus), Anweisungen ändern/zurücksetzen
10. KI trennen → Verlauf → Training → „Coach-Auswertung“ → Hinweis „Kein KI-Zugang“ → „Demo-Auswertung ansehen“
12. Backup exportieren, „Alle Daten löschen“, Backup importieren → Coach-Gedächtnis und Auswertungen wieder da
11. Mit Fuel: unter der Auswertung „Fuel · 0,0x € · Saldo …“
## Phase 3: Beenden-Flow, Fotos, Verlauf, Backup (Session F, Nacht 3.11.)
Fertig:
- Nach „Beenden“: Zusammenfassung (Dauer, Sätze, Volumen, Übungen, Abweichungen), neue Bestwerte (Gewicht, geschätztes 1RM,
  Wdh., Zeit), Gefühl 1–5, Notiz, Körperfotos (Kamera / Foto-Auswahl, nur app-privat), Speichern. Platz für den Coach (Phase 4)
- Verlauf-Tab: Liste, Detail mit allen Sätzen, Notiz, Gefühl, Fotos; „Als Vorlage übernehmen“, „Teilen“ (`kind: "workout"`,
  mit/ohne Ist-Werte, nie Fotos/Notizen/Gefühl), Löschen. Übungsdetail: Fortschritt (Bestwerte, letzte Trainings, e1RM)
- Fotos-Tab: Zeitachse nach Monat, Vollbild, zwei Fotos vergleichen, löschen
- Profil → Backup: Export als eine ZIP-Datei (Teilen-Menü / „In Dateien sichern“), Import mit Vorschau und Bestätigung
  („Alle Daten ersetzen“); ohne API-Key/Geräteschlüssel
- Room v3 (Fotos) mit Migrationstest; `changes`/`dataChanged`-Flows und `photosOf()` für KI-Chat-Sync und Coach
- Tests: Bestwerte/Zusammenfassung, ZIP (auch kaputte Dateien), Training teilen (Common); Migration 2→3, Backup-Rundlauf
  (Export → Alles löschen → Import = gleiche Daten), alte/kaputte/neuere Backups, UI-Durchlauf Vorlage → 2 Sätze → Beenden →
  Notiz → Verlauf (Robolectric)

Offen / Hinweise:
- Android-Zurück-Taste im Beenden-Screen schließt ohne Notiz/Gefühl zu speichern (Training selbst ist gespeichert)
- Backup liegt beim Erstellen komplett im Speicher; bei sehr vielen Fotos später streamen
- iOS-Kamera/Fotoauswahl/Dateiauswahl nur von der iOS-CI gebaut, nicht auf echtem iPhone getestet

## Bitte auf dem Handy testen: Beenden, Verlauf, Fotos, Backup (Phase 3)
1. Vorlage „Push“ starten, 2 Sätze abhaken (Gewicht beim zweiten erhöhen), „Beenden“ zweimal → Zusammenfassung erscheint
2. Gefühl antippen, Notiz schreiben, „Foto aufnehmen“ (Kamera erlauben) und „Aus Fotos wählen“ → Vorschaubilder, eins entfernen
3. „Speichern“ → Start. Gleiches Training nochmal mit mehr Gewicht → unter „Neue Bestwerte“ steht die Übung
4. Verlauf-Tab: Training öffnen → Sätze, Notiz, Fotos; Foto antippen → Vollbild; „Als Vorlage übernehmen“ → Editor
5. Im Verlauf-Detail „Teilen“ → Schalter „Mit Ist-Werten“, Link kopieren und über Übungen → Importieren wieder einfügen
6. Übungen → Bankdrücken → Abschnitt „Fortschritt“ (Bestwerte, letzte Trainings)
7. Fotos-Tab: Zeitachse, „Vergleichen“ → zwei Fotos wählen → nebeneinander; Foto löschen (zweimal tippen)
8. Profil → „Backup exportieren“ → in Dateien/Drive sichern. Dann Profil → „Alle Daten löschen“, Onboarding kurz
   durchklicken, Profil → „Backup importieren“ → Datei wählen → Vorschau → „Alle Daten ersetzen“ (zweimal) → alles wieder da
   (Verlauf, Fotos, Vorlagen, Name). Fotos erscheinen nicht in der Galerie

## Phase 2: Workout-Modus und Pausentimer (Session A)
Phase 2 abgeschlossen:
- „Training starten“ → freies Training oder aus Vorlage (Zielwerte der Vorlage, Lücken von „letztes Mal“)
- Workout-Screen: Name editierbar, laufende Zeit, „x/y erledigt“, Sätze abhaken (Häkchen + Kontrast), Werte live
  ändern, Satz hinzufügen/duplizieren/löschen, Übung hinzufügen/ersetzen/entfernen, Notiz je Übung,
  Umsortieren per Drag & Drop (auch im Vorlagen-Editor), „Letztes Mal: …“, unten „Nächsten Satz loggen“
- Training überlebt App-Kill und Neustart (Room v2, AutoMigration 1→2 mit Migrationstest); die App springt beim
  Start direkt ins laufende Training
- Pausentimer: startet beim Abhaken (Vorlage → Übung → Standard-Pause aus dem Profil), minimiert/erweitert,
  ±15 s, Überspringen, Tippen auf „Nächste“ springt zur Übung
- Hintergrund: Android laufende Countdown-Benachrichtigung mit +15 s/Überspringen und exakter Alarm zum Pausenende
  (Berechtigungsabfrage ab Android 13, kein Wakelock); iOS lokale Benachrichtigung
- Haptik-Interface (Tick, Pausenende, Reveal vorbereitet), Schalter „Vibration“ und „Standard-Pause“ im Profil
- „Beenden“ (zweimal tippen) legt das Training als abgeschlossen ab; „Training abbrechen“ mit Bestätigung
- Tests: Workout-Modell, Controller mit virtueller Zeit (Timer, Prozess-Tod), Formatierung (Common, 136 gesamt);
  Repository, Migration 1→2 und UI-Durchlauf Vorlage → Training → Satz → Pause → Beenden (Robolectric)

Offen / Hinweise:
- Beenden-Flow mit Zusammenfassung, Foto, Verlauf: Phase 3 (Verlauf-Tab zeigt Trainings noch nicht)
- „Bildschirm anlassen“, Ton zum Pausenende, RPE, iOS Live Activity: später
- iOS: Benachrichtigung/Haptik nur von der iOS-CI gebaut, noch nicht auf echtem iPhone getestet

## Bitte auf dem Handy testen: Workout und Pausentimer (Phase 2)
1. Start → „Training starten“ → Vorlage wählen. Beim ersten Mal Benachrichtigungen erlauben
2. Satz abhaken (Kreis rechts) → kurze Vibration, Pausenleiste unten; antippen → groß, nochmal → klein;
   −15 s / +15 s / Überspringen
3. Werte ändern (auch „92,5“, „±“), Satz hinzufügen/duplizieren/löschen, Übung hinzufügen/ersetzen/entfernen,
   Übung am Griff (zwei Striche) lange drücken und ziehen; dasselbe Ziehen im Vorlagen-Editor
4. Pause starten, Bildschirm sperren → Countdown in der Benachrichtigung, am Ende „Pause vorbei“ mit Vibration;
   Aktionen +15 s / Überspringen in der Benachrichtigung
5. Falls im Profil „Pausenende sekundengenau → Erlauben“ erscheint: antippen, erlauben, zurück → Zeile verschwindet
6. Während des Trainings App aus den letzten Apps wischen und neu öffnen → Training (und laufende Pause) ist da
7. Profil: Standard-Pause z. B. 60, Vibration Aus → Abhaken vibriert nicht mehr
8. „Nächsten Satz loggen“ bis „Alle Sätze erledigt“, dann „Beenden“ zweimal → zurück auf Start; neues Training mit
   derselben Übung zeigt „Letztes Mal: …“
9. Training starten und „Training abbrechen“ (zweimal) → verworfen

## Nacht 3.11. – Guthaben & Einladungen in der App (Session D)
Fertig:
- Geräte-Identität (Keystore / Secure Enclave), signierte Anfragen nach `docs/backend-api.md`, `FuelApi` (Ktor) mit Re-Registrierung und Zeitkorrektur, Fehlercodes
- Profil → **Fuel**: Saldo („Fuel · 4,20 €“), verfallendes Guthaben, Pakete 5/10 € („Bald im Store“), Buchungen, Wiederherstellungscode (erzeugen, kopieren, auf neuem Handy eingeben)
- **Einladungen**: „Freunde einladen“ (Name, QR mit rotierendem Token, Haarlinien-Fortschritt), „Einladung einlösen“ (Android: QR scannen oder Link einfügen; iOS: Kamera-App öffnet den Link), Reveal mit Haptik
- **Demo** (ohne Server): Profil → Fuel → „Demo ansehen“; Backend-URL per CI-Variable `OVERFIT_BACKEND_URL`, versteckter Override (7× Version in „Über“)
- Fuel als Option im KI-Zugang (`FuelProvider`); Tests: SHA/Signatur/Testvektor, Beträge, Reveal, Fuel-Client (MockEngine), QR, UI-Durchlauf Demo

### Von Fabian zu tun (Guthaben/Einladungen)
1. Backend deployen (siehe oben), dann GitHub → Settings → Secrets and variables → **Variables** → `OVERFIT_BACKEND_URL` = Worker-URL anlegen. Ohne sie zeigt die App „startet bald“ + Demo.
2. Play-/Apple-Konten für echte Käufe (Play Billing / StoreKit) und Geräteprüfung (Play Integrity Cloud-Projekt, App Attest); Server-Prüfer ergänzen (Tokenformat siehe DECISIONS).
3. Optional: Web-Seite für Einladungs-Links ohne installierte App (QR enthält `overfit://invite#…`, alte `overfit://spot#…` werden weiter gelesen).

### Bitte auf dem Handy testen (Guthaben/Einladungen)
1. Profil → „Fuel · startet bald“ öffnen → Text „Fuel startet bald“ → **Demo ansehen**
2. Demo: „Fuel · 4,20 €“, Pakete mit „Bald im Store“, Buchungen (Verbrauch, Aufladung, Einladungsbonus), Wiederherstellungscode erzeugen + kopieren
3. **Freunde einladen:** QR erscheint, Haarlinie läuft ab, alle ~30 s neuer Code; Namen eintragen
4. **Einladung einlösen:** beliebigen Link/Code einfügen → Einlösen → Bildschirm schwarz, zwei Herzschlag-Pulse, Zahl zählt 0,00 → 1,00 €, „Einladung von Fabian“, kräftiger Puls; Tippen schließt
5. „Demo beenden“ → Fuel zeigt wieder „startet bald“. Version in „Über“ 7× tippen → Abschnitt „Entwickler“ mit Backend-URL
6. Profil → KI verbinden: erste Zeile „Fuel“ wählbar

## Phase 8: Vorlagen teilen und importieren (Session B)
- Format `docs/share-format.md`: JSON → Raw Deflate → Base64url im Fragment (`https://bigfire3.github.io/overfit/t#…`,
  `overfit://t#…`), Übungen per Katalog-ID, Name (DE/EN, unscharf aufgelöst) oder eigene Übung; Version und `kind`
  (später `workout`) werden geprüft
- Teilen: Vorlage öffnen → „Teilen“ → QR-Code, „Link teilen“ (System-Share-Sheet), „Link kopieren“, „Als Datei teilen“
  (`.overfit`); QR nur bis 1.000 Zeichen
- Import mit Vorschau (Name, Übungen, Sätze, was neu angelegt wird) aus Link, `.overfit`-Datei, QR-Scan (Android) und
  Zwischenablage (Link, Base64, JSON, auch im ```json-Block mit Text drumherum)
- „KI-Vorlage kopieren“: Prompt in App-Sprache für ChatGPT/Claude/Gemini; Antwort danach aus der Zwischenablage importieren
- Beispielvorlagen Push/Pull/Legs (je 6 Übungen, Katalog-IDs): im leeren Vorlagen-Zustand auf Start, in Übungen →
  Vorlagen und im Profil „Beispielvorlagen hinzufügen“ (idempotent)
- (entfernt 3.11., Fabian; später evtl.) „Duo · bald“ auf dem Startbildschirm → Teaser-Screen
- Tests: Deflate, Format, Parser, Mapper, AI-Prompt (Common); QR gegen ZXing; Samples laden, Teilen→Import
  ergibt dieselbe Vorlage, Import-Flows in der UI (Robolectric)
- iOS: kein In-App-QR-Scanner (Kamera-App öffnet den `overfit://`-Link), Begründung in DECISIONS.md

**Von Fabian zu tun (Phase 8):**
1. Web-Fallback `https://bigfire3.github.io/overfit/t` muss live sein (GitHub Pages, aus PR #20), sonst öffnen
   https-Links ohne App im Leeren. Die App öffnet unabhängig davon `overfit://`-Links und `.overfit`-Dateien.
2. iOS Universal Links (https-Link öffnet direkt die App) brauchen eine Domain, die dir gehört, plus Associated Domains
   und `apple-app-site-association`. Später mit eigener Domain; bis dahin: `overfit://`-Link und Web-Seite mit Knopf.

## Nacht 3.11. – KI-Chat-Verbindungen, Teil 1: Konzept + Backend (Session E)
Fertig (kein App-Code angefasst):
- SPEC „KI-Chat-Verbindungen (Phase 10)“, fünf KI-Wege; DECISIONS inkl. „Zur Prüfung durch Fabian“ (Opt-in-Ausnahme vom
  „kein Cloud-Sync“)
- `backend/src/chat/`: Remote-MCP-Server `/mcp` (13 Tools, 3 Prompts), OAuth 2.1 (DCR, Client ID Metadata Documents, PKCE,
  Refresh-Rotation, Widerruf) mit Anmeldeseite per Kopplungscode, Sync-API (Snapshot/Inkremente, Fotos in R2, Eingang,
  Abschalten löscht alles), AES-GCM at rest, Limits. Migration `0002_chat.sql` (nur neue Tabellen). 16 neue Tests (58 gesamt)
- API-Vertrag für Teil 2: `docs/backend-api.md` → „KI-Chat-Verbindung“; Eintragen in Claude/ChatGPT/Gemini: `backend/README.md`

Offen (Teil 2, App): Einstellungsseite „KI-Coach“ mit den fünf Wegen, „KI-Chat verbinden“ (Einwilligung, Foto-Häkchen,
Kopplungscode, Verbindungen trennen), Sync, Eingang mit Import-Vorschau, „Kontext kopieren“ + Gem-Anleitung.

### Von Fabian zu tun (zusätzlich zu Punkt 2 unten)
1. Beim Cloudflare-Deployment zusätzlich: Secret `SYNC_ENCRYPTION_KEY` (32 Zufallsbytes Base64url, nie wieder ändern), R2
   aktivieren und `npx wrangler r2 bucket create overfit-chat-photos`, `npm run db:migrate:remote` (spielt `0002_chat.sql` ein).
2. Danach Connector eintragen: Claude → Einstellungen → Connectors → Custom Connector, URL `https://<worker>/mcp`; ChatGPT →
   Entwicklermodus → Connector erstellen (OAuth); Gemini → Eigene Apps (falls im Konto verfügbar). Genaue Schritte in
   `backend/README.md`. Anmelden geht erst mit Teil 2 (Kopplungscode aus der App); bis dahin testbar mit dem MCP Inspector.
3. Datenschutzerklärung vor dem Store-Start um die KI-Chat-Verbindung ergänzen (siehe DECISIONS).

### Bitte testen (nach dem Deployment, noch ohne App-Teil 2)
1. `https://<worker>/.well-known/oauth-authorization-server` im Handy-Browser öffnen → JSON mit `authorization_endpoint`
2. In claude.ai den Custom Connector mit `https://<worker>/mcp` hinzufügen → „Verbinden“ öffnet die schwarz-weiße
   Overfit-Seite „Overfit verbinden“ mit Feld „Kopplungscode“ (Deutsch bei deutscher Browsersprache); falscher Code →
   Hinweis „Code falsch oder abgelaufen“. Der echte Code kommt mit Teil 2 aus der App.

## Nacht 3.11. – Backend Guthaben/Einladungen + Web-Fallback (Session C)
Fertig (kein App-Code angefasst):
- `backend/`: Cloudflare Worker + D1, 42 Tests (Vitest/Miniflare, lokal ohne Cloudflare-Konto): anonyme Geräte-Identität
  (ECDSA P-256, signierte Anfragen mit Nonce/Zeitstempel), Konto mit Saldo/Buchungen in Mikro-Euro, Wiederherstellungscode,
  `POST /v1/messages` als Anthropic-Proxy (atomare Reservierung, Abrechnung nach `usage` + Aufschlag, Freigabe bei Fehlern),
  Kauf-Verifikation (idempotent, Paket-Gutschrift = Preis × (1 − Store) / (1 + Aufschlag)), Einladungen (Token, Challenge, Einlösen,
  Limits, Budget, Verfall, Belohnung bei der ersten Coach-Anfrage), Rate-Limits, einheitliches Fehlerformat
- API-Vertrag mit exaktem Signatur-Schema: `docs/backend-api.md` (Grundlage für die App-Session)
- `web/`: Fallback-Seite `t/index.html` (liest `#payload` laut `docs/share-format.md`, DE/EN, „In Overfit öffnen“, APK-Download)
  und Startseite; Playwright-Rauchtest; Workflow `pages.yml` zum Veröffentlichen; `backend.yml` testet bei Änderungen
- DECISIONS: „Produktentscheidungen Nacht 3.11.“ + Backend-Entscheidungen

Offen: Echte Google-Play-/Apple-Prüfer für Käufe und Play Integrity/App Attest (Schnittstellen + Mocks fertig, Produktion
antwortet bis dahin 503 `purchases_unavailable` / `attestation_unavailable`); App-Anbindung (`LlmProvider` „Fuel“, Einladungs-UI) nach `docs/backend-api.md`.

### Von Fabian zu tun
1. **GitHub Pages aktivieren:** Repo → Settings → Pages → Source „GitHub Actions“. Danach läuft `pages.yml` bei jedem Merge
   mit Änderungen in `web/` (oder manuell: Actions → „Web veröffentlichen“ → Run workflow). Ergebnis: https://bigfire3.github.io/overfit/
2. **Cloudflare-Backend deployen** (Schritte und alle Secrets in `backend/README.md`): Konto, `wrangler login`, `wrangler d1 create overfit`
   (ID in `wrangler.jsonc`), Migrationen, `ANTHROPIC_API_KEY` und `SPOT_SIGNING_KEY` als Secrets, `npm run deploy`. Worker-URL
   an die Session für die App-Anbindung geben.
3. Später für Käufe/Einladungen in Produktion: Play-Console- und Apple-Developer-Konten (Phase 6), Dienstkonto/Schlüssel als Secrets
   (Liste in `backend/README.md`), danach die echten Prüfer in `backend/src/verifiers.ts` ergänzen.
4. Für Android App Links / iOS Universal Links wird eine eigene Domain mit `/.well-known/` gebraucht (unter
   `github.io/overfit/` nicht möglich) – bis dahin öffnet der Button „In Overfit öffnen“.

### Bitte auf dem Handy testen (Web-Fallback, nach Aktivieren von Pages)
1. https://bigfire3.github.io/overfit/ öffnen: schlichte Startseite, Button „Für Android herunterladen“ führt zum Release „latest“
2. Einen Teilen-Link aus der App (Session B) im Handy-Browser öffnen, z. B. `https://bigfire3.github.io/overfit/t#<payload>`:
   Vorlage erscheint schwarz-weiß lesbar (Name, Übungen, Sätze, Pausen); Telefonsprache Deutsch → deutsche Texte
3. „In Overfit öffnen“ antippen → die App öffnet sich (sobald der `overfit://`-Import aus Session B drin ist)
4. Link ohne `#…` oder mit abgeschnittenem Payload öffnen → freundliche Fehlermeldung, kein Absturz

## Davor: Phase 1
Phase 1 abgeschlossen (erst nativ gebaut, dann auf KMP portiert):
- Import-Skript `tools/catalog/import_catalog.py`: Workout Guide und free-exercise-db, an Commits gepinnt →
  1098 Übungen + 302 Workout-Guide-SVGs in den Compose Resources (offline, Android und iOS)
- Room KMP (Schema v1 unter `shared/schemas`): Übungen, Vorlagen, Vorlagen-Übungen, geplante Sätze. Katalog wird beim
  Start eingespielt, eigene Einstellungen an Übungen bleiben bei Katalog-Updates erhalten
- Tab „Übungen“ mit Umschalter Katalog/Vorlagen:
  - Katalog mit unscharfer Suche (auch deutsche Begriffe wie „Klimmzug“) und Filter-Chips (Muskelgruppe,
    Equipment, Eigene)
  - Detail mit Bild und Muskeln; Erfassung und Pause auch bei Katalogübungen änderbar
- Eigene Übungen anlegen, bearbeiten, löschen (Initialen-Kachel ohne Bild). Steckt eine Übung noch in einer Vorlage,
  wird sie nur ausgeblendet
- Vorlagen-Editor: Name, Übungen auswählen (Mehrfachauswahl), Sätze mit Zielwerten passend zur Erfassung
  (kg/lb nach Profil, „±“ für Unterstützung), Pause und Notiz je Übung, ersetzen, verschieben, entfernen, löschen.
  Ungespeichert verlassen nur mit zweitem Zurück
- Vorlagen auch auf dem Startbildschirm; Profil → „Alle Bilder offline laden“; „Alle Daten löschen“ leert auch die
  Datenbank
- Tests: Suche, Zahlen/Einheiten und Vorlagen-Entwurf als Common-Tests (Android-Host und iOS-Simulator); Datenbank,
  Seeding, Migration und UI-Durchlauf (Suche → eigene Übung → Vorlage) als Robolectric-Tests

## Davor: Signatur über Secrets und Planung Teilen/Guthaben (PR #14)
- Neuer Signaturschlüssel nur noch in GitHub-Secrets, `signing/` entfernt; CI baut zusätzlich das App Bundle für Google Play
- Apache-2.0-Lizenz, Skript zum Bereinigen der Git-Historie
- SPEC: Teilen per Link/QR, Coach-Guthaben, Einladen mit Startguthaben (Phasen 7–9)
- Lokal grün: `:shared:testAndroidHostTest`, `:app:testDebugUnitTest`, signiertes APK + AAB (neues Zertifikat geprüft)

Von dir zu tun:
1. Nach dem Merge `scripts/purge-old-signing-history.sh` ausführen (braucht `pip install git-filter-repo`,
   ggf. Force-Push auf main kurz erlauben), wenn keine anderen offenen PRs mehr laufen
2. Bundle-ID / Paketnamen `app.overfit` im Apple-Developer-Konto und in der Play Console reservieren

## Davor: Phase 0b
Phase 0b abgeschlossen:
- Umbau auf Kotlin Multiplatform: `:shared` (UI + Logik für Android und iOS), `:app` (Android-Hülle, unverändert signiert),
  `iosApp/` (XcodeGen-Spec + SwiftUI-Host), iOS-CI auf macOS
- Designsystem schwarz-weiß (Inter, keine Akzentfarbe, Haarlinien, Pill-Button, eigene Linien-Icons), Dunkel/Hell/System
- Deutsch und Englisch, in der App umschaltbar
- Onboarding: Name, Alter, Gewicht (kg/lb), KI-Zugang mit Key-Prüfung (OpenRouter, Anthropic, OpenAI) oder „Später einrichten“
- Profil-Tab mit Sprache, Darstellung, Profil, KI-Zugang, Coach-Gedächtnis, Über, Alle Daten löschen
- API-Key verschlüsselt im Android Keystore bzw. iOS Keychain
- Profil-Block für den Coach (`buildProfileBlock`), unit-getestet
- Tests: 19 Common-Tests + 2 Robolectric-UI-Tests, lokal grün; iOS-Code kompiliert (Linux), Linken/Starten prüft die iOS-CI

Offen / Hinweise:
- Drag & Drop zum Umsortieren kommt mit Phase 2 (jetzt: „Nach oben/unten“)
- „Training starten“ bleibt deaktiviert (Phase 2); Tippen auf eine Vorlage öffnet den Editor
- Globale Standard-Pause in den Einstellungen folgt in Phase 2 (leere Pause = Standard)
- iOS: Linken und Starten prüft weiterhin nur die iOS-CI

## Bitte auf dem Handy testen
1. Falls noch nicht geschehen: einmalig die alte Overfit-App deinstallieren (neuer Signaturschlüssel aus #14), dann
   `overfit.apk` aus Release „latest“ installieren und das Onboarding durchlaufen
2. Tab „Übungen“: Katalog erscheint nach kurzem Einrichten, Liste mit Strichgrafiken (Workout Guide, schwarz-weiß)
   und Fotos (free-exercise-db, nur mit Internet)
3. Suchen: „Klimmzug“, „Bankdrücken“, „Kniebeuge“, „pushup“, „bensh press“ (Tippfehler), „Brust“; Chips für
   Muskelgruppe, Equipment und „Eigene“ ausprobieren
4. Übung antippen → Detail; bei Pull-up Erfassung „Gewicht × Wdh.“ wählen, Pause 120 → Speichern; erneut öffnen →
   Werte sind da
5. „Eigene Übung“: z. B. „Handstand“, Erfassung „Zeit“ → erscheint mit Kachel „HA“; bearbeiten; löschen (zweimal tippen)
6. Umschalter „Vorlagen“ → „Neue Vorlage“: Name, „Übung hinzufügen“ (mehrere auswählen), Sätze füllen (Komma,
   „±“), Satz hinzufügen/entfernen, Übung ersetzen/verschieben/entfernen, Speichern → Vorlage in der Liste und auf Start
7. Im Editor etwas ändern und Zurück → Hinweis „Nicht gespeichert …“, zweites Zurück verwirft
8. Profil → Einheit auf lb → Vorlage öffnen: Gewichte in lb; zurück auf kg
9. Profil → „Alle Bilder offline laden“ (im WLAN) → Fortschritt; danach im Flugmodus Fotos weiter sichtbar
10. App schließen und neu öffnen → Vorlagen und eigene Übungen sind noch da; Sprache auf English → Übungen-Tab englisch

## Bitte auf dem Handy testen: Teilen und Import (Phase 8)
1. Start: Bei leerer Vorlagenliste „Beispielvorlagen hinzufügen“ → Push, Pull, Legs erscheinen; nochmal tippen (Profil
   → Vorlagen) → „schon da“, nichts doppelt
2. Vorlage Push öffnen → „Teilen“: QR-Code, „Link teilen“ → WhatsApp/Notizen/Mail; der Text enthält einen Link
3. QR-Code mit einem zweiten Handy (Kamera-App) scannen → Browser/Web-Seite zeigt die Vorlage; dort „In Overfit
   öffnen“ bzw. Link `overfit://t#…` antippen → Overfit öffnet die Vorschau
4. Übungen → Vorlagen → „Vorlage importieren“ → „QR-Code scannen“ (Android, Kamera-Erlaubnis) mit dem QR eines anderen
   Handys oder Bildschirms → Vorschau → „Übernehmen“ → Editor mit der neuen Vorlage
5. Link in die Zwischenablage kopieren (z. B. aus WhatsApp) → „Aus Zwischenablage importieren“
6. „KI-Vorlage kopieren“ → in ChatGPT/Claude/Gemini einfügen, Ziel/Equipment beantworten, Antwort („```json“-Block)
   kopieren → „Aus Zwischenablage importieren“ → Vorschau zeigt Übungen (neu = eigene Übung) → „Übernehmen“
7. Vorlage → „Teilen“ → „Als Datei teilen“ → `.overfit` an sich selbst mailen/in Dateien speichern, antippen → Overfit
   öffnet die Vorschau
8. (entfernt 3.11., Fabian; später evtl.) Startbildschirm ganz unten: „Duo · bald“ → Erklärtext
9. Zwischenablage leer oder Text ohne Training → verständliche Fehlermeldung

## Bitte auf dem Handy testen: App-Icon „of“ (Session W)
1. Neue APK installieren: Icon „of“ (weiß auf Schwarz) auf dem Homescreen, in der App-Übersicht und in den Einstellungen; Launcher mit runden und eckigen Masken ansehen, die Schrift muss nie angeschnitten sein
2. Hell/Dunkel: Hintergrundbild hell und dunkel, Icon bleibt gut lesbar; iOS 18: Homescreen auf „Dunkel“ und „Getönt“ stellen
3. Android 13+: Einstellungen → Hintergrund und Stil → „Themed icons“ an → „of“ erscheint einfarbig in der Systemfarbe
4. App starten: Splash schwarz mit kleinem weißen „of“ (Android 12+ und iOS), danach direkt die App
5. Browser: Favicon im Tab der Webseite, „Zum Home-Bildschirm“ auf dem iPhone zeigt das „of“
6. Variante gefällt nicht? `python3 tools/icon/build_icon.py --variant B2` (B1–B6, siehe docs/DECISIONS.md), committen

## Session AC – „Spot“ heißt jetzt „Einladung“ (3.11.)
- Erledigt: Alle sichtbaren Texte (App DE+EN, Webseite, Store-Texte, README, Docs, Deploy-Summary, Buchungsbezeichnungen) sagen „Freunde einladen“, „Einladung einlösen“, „Einladungsbonus“ (EN: Invite friends, Redeem invite, Referral bonus). Neue Links `overfit://invite#…`, alte `overfit://spot#…` werden weiter gelesen. Technik (API, DB, SPOT_*) bleibt, siehe docs/DECISIONS.md.
- Offen: nichts.

### Bitte auf dem Handy testen (Einladungen)
1. Profil → Guthaben → Bereich „Einladungen“ → „Freunde einladen“: QR und rotierender Code, Namen eintragen
2. Zweites Handy: QR mit der Kamera-App scannen (Link `overfit://invite#…`) → Overfit öffnet „Einladung einlösen“; alternativ Link einfügen
3. „Einladung einlösen“ → Einlösen: schwarz, zwei Pulse, Zahl zählt 0,00 → 1,00 €, darunter „Einladung von Fabian“, kräftiger Puls
4. Guthaben → Buchungen: Eintrag „Einladungsbonus“; beim Einladenden „Einladungsbonus (Freund eingeladen)“ nach der ersten Auswertung des Freundes
5. Ein alter Link `overfit://spot#…` (aus der Zwischenablage eingefügt) funktioniert weiter; nirgends steht mehr „Spot“

## Session Coach-Story (3.11.)
- Erledigt: Tipp auf den Titel „Coach“ (mit Info-Icon) öffnet eine Vollbild-Story mit 4 Seiten (Fortschrittsbalken als Haarlinien, Tipp rechts/links = weiter/zurück, Wischen nach unten oder X = schließen, Auto-Weiter nach 6 s). Seite 1: was die KI auswertet; Seiten 2–4: eigener Schlüssel, Guthaben (mit Einladungsbonus), eigenes Abo (Claude/ChatGPT/Gemini) mit Knopf zur passenden Stelle. Beim ersten Öffnen des Coach-Tabs geht sie einmal von selbst auf (Flag `overfit.coachStorySeen`).
- Coach-Seite entschlackt: Erklär- und Hinweistexte (KI-Zugang ohne Zugang, Prompts-Intro, Verbindungs-Hinweis, Chat-Intro, Hinweiszeilen unter „Letzte Einheit auswerten“/„Fortschritt bewerten“) sind raus; der Inhalt steht in der Story bzw. im KI-Zugang (Profil/Einstellungen).
- Tests: neue `CoachStoryTest`; bestehende Coach-Tests setzen das Erstes-Mal-Flag vorab.

### Bitte auf dem Handy testen (Coach-Story)
1. Neue APK, Tab „Coach“ zum ersten Mal öffnen → Story geht von selbst auf; X oder nach unten wischen schließt
2. Tab wechseln und zurück → keine Story mehr; Tipp auf „Coach ⓘ“ öffnet sie wieder
3. Rechts tippen = weiter, links = zurück, nach ca. 6 s geht sie von selbst weiter; auf Seite 4 rechts tippen schließt
4. Knöpfe auf Seite 2–4: „Schlüssel einrichten“, „Zum Guthaben“, „Zu den Verbindungen“ führen zur passenden Stelle
5. Coach-Seite wirkt ruhiger; Auswerten, Chat, Prompts und Verbindungen funktionieren wie vorher
## Session AK – Sauna-Häkchen nach dem Training (3.11.)
- Erledigt: Häkchen „Sauna“ im Abschluss-Screen (Standard aus) und nachträglich in der Trainingsdetail-Ansicht; DB v8 mit Migration und Test; Kalender zeigt Sauna-Tage mit hohlem Ring (Legende, sobald es einen gibt); Coach-Auswertung, KI-Chat-Kontext und KI-Chat-Sync (Backend `list_workouts`) bekommen die Sauna; Backup führt das Feld mit (alte Backups = keine Sauna). Details in docs/DECISIONS.md.
- Offen: nichts.

### Bitte auf dem Handy testen (Sauna)
1. Training beenden → im Abschluss-Screen unter „Gefühl“ steht „Sauna“ mit leerem Kästchen; antippen → schwarz gefülltes Kästchen mit Haken → „Speichern“
2. Tab „Fotos“: Am Tag des Trainings unter dem Punkt ein hohler Ring, oben „● Training ○ Sauna“ als Legende; an einem Tag mit Foto sitzt der Ring neben dem kleinen Punkt oben links
3. Verlauf → das Training öffnen → „Sauna“ ist angehakt; abhaken → zurück zum Kalender: der Ring ist weg (und die Legende, falls es der einzige Sauna-Tag war)
4. Coach-Auswertung für ein Training mit Sauna starten (oder KI-Chat fragen „Wann war ich zuletzt in der Sauna?“) → der Coach kennt die Sauna
5. „Kontext kopieren“ (Coach-Tab bzw. KI-Chat-Einstellungen) → in einen Editor einfügen: Zeile des Trainings endet mit „· danach Sauna“
6. Backup exportieren und wieder einspielen → Sauna-Häkchen bleiben erhalten

## Session AL – Verlauf-Tab weg, Kalender-Tag, Trainings nachträglich bearbeiten (3.11.)
- Erledigt: Tab „Verlauf“ entfernt; Tabs heißen jetzt Workouts, Coach, Kalender, Profil (neue Icons: Klemmbrett, Sprechblase, Kalenderblatt, Person). Training löschen behält dessen Fotos am Tag. Tipp auf einen Tag öffnet die Tagesansicht mit Trainings und Fotos (Fotos hinzufügen per Kamera/Galerie, ansehen, mit Bestätigung entfernen, auch an Tagen ohne Training). Training → Detail → Stift „Training bearbeiten“: Gefühl, Sauna, Notiz, Sätze (Gewicht/Wdh. ändern, Satz hinzufügen/entfernen, abhaken), Speichern/Verwerfen. DB v9 (Fotos ohne Training) mit Migration und Test. Bildlader-Fehler aus Session AI behoben (Ursache: Coil-Singleton wurde in der Komposition gesetzt). Details in docs/DECISIONS.md.
- Weiterhin erreichbar (früher nur über Verlauf): Trainingsdetail, Löschen, Teilen, „Als Workout speichern“, Coach-Auswertung/Demo, KI-Chat-Notizen – alles über Kalender → Tag → Training (oder Vollbildfoto → „Training ansehen“).
- Offen: nichts.

### Bitte auf dem Handy testen (Kalender und Bearbeiten)
1. Unten nur noch vier Tabs: Workouts (Klemmbrett), Coach (Sprechblase), Kalender (Kalenderblatt), Profil (Person); Namen nicht abgeschnitten, auch auf Englisch
2. Kalender → einen Trainingstag antippen → Tagesansicht mit Datum, „Trainings“ (Name, Uhrzeit, Dauer, Sätze, kg) und „Fotos“
3. Dort „Aus Fotos wählen“ bzw. „Foto aufnehmen“ → Foto erscheint am Tag und im Kalender; Foto antippen → Vollbild; „×“ → „Entfernen?“ → nochmal tippen → weg
4. Einen leeren Tag in der Vergangenheit antippen → „An diesem Tag kein Training.“ → Foto hinzufügen → Kalender zeigt es an diesem Tag; im Vollbild steht kein „Training ansehen“
5. Training antippen → Detail → Stift oben rechts → Sauna anhaken, Gewicht eines Satzes ändern, „Satz hinzufügen“, einen Satz nach links wischen → „Speichern“ → Detail zeigt die neuen Werte; Kalender zeigt den Sauna-Ring
6. Nochmal bearbeiten, etwas ändern und Zurück → Hinweis „Nicht gespeichert …“, zweites Zurück verwirft
7. Übung (z. B. Bankdrücken) → Fortschritt/Bestwerte zeigen die geänderten Werte; eine vorher erstellte Coach-Auswertung ist unverändert
8. Detail: Teilen, Als Workout speichern, Coach-Auswertung, Löschen funktionieren wie früher im Verlauf; nach dem Löschen eines Trainings mit Foto bleibt das Foto am Kalendertag
9. Backup exportieren und wieder einspielen → Fotos ohne Training bleiben erhalten

## Session AM – UI-Audit, Teil 1: Zahlenfelder im Training (4.11.)
- Plugin „impeccable“ war in der Session nicht geladen (kein Skill, keine Hooks); Audit nach denselben Kriterien selbst.
- Erledigt: Satz-Felder neu (20 sp, tabellarische Ziffern, 48 dp hoch, Einheit als Spaltenkopf), Ursache und Belege in docs/DECISIONS.md und `docs/screenshots/audit/vorher|nachher/satz-*.png`. Tastatur „Weiter“/„Fertig“ mit Fokuskette, Cursor weg nach „Fertig“ und beim Abhaken; gilt auch im Workout-Editor und beim Bearbeiten eines Trainings.

### Bitte auf dem Handy testen (Zahlenfelder)
1. Training starten → Übung aufklappen: über den Sätzen „SATZ · KG · WDH.“, in den Feldern große, gut lesbare Zahlen; 1, 11, 102,5 und 1000 eintippen → alles vollständig sichtbar (auch mit größerer Systemschrift)
2. Gewicht eintippen → Tastatur-Taste „Weiter“ → Cursor springt zu Wdh. → „Weiter“ → nächster Satz; im letzten Satz zeigt die Taste „Fertig“ → Tastatur zu, kein Cursor mehr
3. In ein Feld tippen und dann den Haken des Satzes antippen → Tastatur zu, kein Cursor
4. Dasselbe im Workout-Editor (Workout → Bearbeiten) und unter Kalender → Training → Stift
5. Notizfeld / Suche: „Fertig“ bzw. Suchen-Taste schließt Tastatur und Cursor

## Session AM – UI-Audit, Teil 2: Panels und Audit-Funde (4.11.)
- Erledigt: Bereiche heben sich als graue, umrandete Panels vom Hintergrund ab (überall, wo Zeilen zusammengehören), kurze Erklärtexte stehen grau darunter. Funde der 360-dp-Tour behoben (Wortumbruch „Darstellung“, abgeschnittene Knopftexte, einzeilige Notizen, Titel „Profil“). Vorher/Nachher: `docs/screenshots/audit/tour-vorher` und `tour-nachher`. Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Panels)
1. Hell und dunkel: Workouts-Liste, Workout öffnen, Profil, Coach, Kalender → Tag → Training: Zeilen stehen in grauen Flächen mit feinem Rand und runden Ecken, nichts verschwimmt mit dem Hintergrund
2. Profil: „Darstellung“ steht ganz, die Auswahl System/Dunkel/Hell darunter; Standard-Pause und Wert in einer Zeile; Name/Alter antippen, ändern, „Fertig“ → gespeichert, Cursor weg
3. Training: „+ Satz hinzufügen“, „Überspringen“ in der Pause und „Beenden? Nochmal tippen“ sind ganz lesbar (ggf. etwas kleiner); lange Übungsnotiz bricht um statt abgeschnitten zu werden
4. Guthaben, Einladungen, KI-Chat und Übungsdetail: Flächen sichtbar, Knöpfe in Flächen gut erkennbar

## Session AM – UI-Audit, Teil 3: Popups (4.11.)
- Erledigt: Alle Bestätigungen („Nochmal tippen“) sind jetzt ein einheitliches Popup mit Frage, „Abbrechen“ und Aktion; Tippen daneben schließt. Belege `docs/screenshots/audit/popups/`. Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Popups)
1. Training → „Beenden“ → Popup „Training beenden?“ → daneben tippen: Popup zu, Training läuft weiter → nochmal → „Beenden“ → Abschluss
2. Training → ganz unten „Training abbrechen“ → „Training verwerfen?“ → „Verwerfen“
3. Workout öffnen → Mülleimer → „Wirklich löschen?“ mit Hinweis, dass Trainings im Kalender bleiben → „Löschen“
4. Kalender → Tag → Foto „×“ → „Foto vom Tag entfernen?“ → „Abbrechen“ behält es, „Entfernen“ entfernt es
5. Profil → ganz unten „Alle Daten löschen“ und Backup einspielen fragen ebenfalls per Popup; Zurück-Taste schließt jedes Popup

## Session AP – Kalender als Liste, Workouts als schwebende Karten (4.11.)
- Erledigt: Kalender-Tab: Icon rechts oben schaltet zwischen Kalender und Liste (neueste oben, nach Monat, mit Name, Kurzinfo, Foto-Vorschau; Tipp öffnet den Tag); Wahl wird gemerkt. Workouts-Tab: jedes Workout als eigene schwebende Karte (`FloatingCard`). Tests und Screenshots (`docs/screenshots/kalender-liste/`). Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Kalender-Liste, Karten)
1. Workouts-Tab: jedes Workout ist eine eigene Karte mit Abstand, Rand und leichtem Schatten (hell) bzw. kräftigerem Rand (dunkel); ganze Karte antippbar öffnet das Workout
2. Kalender-Tab: oben rechts Listen-Symbol → Liste (neueste oben, Monate, Trainingsname, Dauer/Sätze/kg, „Sauna“, Foto-Vorschau); Symbol wird zum Kalender-Symbol → zurück
3. Eine Zeile antippen → Tagesansicht wie im Kalender; zurück bleibt die Liste
4. App schließen und neu öffnen → die zuletzt gewählte Ansicht ist noch aktiv
5. Englisch: Beschreibungen „Show as list“ / „Show as calendar“; Liste ohne Daten zeigt freundlichen Hinweis

## Session AN – Übungsbild: Start | Ende statt Animation (4.11.)
- Erledigt: Die Detailansicht einer Übung zeigt statt der Animation ruhig zwei Posen nebeneinander, links „Start“, rechts „Ende“ (Frame 1 und 3 aus Workout Guide). Beide gleich groß, im selben Maßstab, auf derselben Bodenlinie, Linien gleich stark (dünnere Pose per Kontur angeglichen). Liegende Übungen (Liegestütz, Crunch …) stehen untereinander. Halteübungen und statische Dehnungen zeigen ein Bild. Frame 2 (Ursache der dicken, springenden Linien) entfernt, Übungsgrafiken 23 → 14 MB. Belege `docs/screenshots/uebungsbild/vorher|nachher/`. Details in docs/DECISIONS.md.
- Offen: nichts.

### Bitte auf dem Handy testen (Übungsbild)
1. Übung öffnen (Katalog oder Bild antippen), z. B. Reverse Lunge: links stehend „Start“, rechts Ausfallschritt „End(e)“, nichts bewegt sich, beide Figuren gleich dick gezeichnet und unten auf einer Linie
2. Bench Press, Pull-up, Squat, Bicep Curl, Deadlift: Reihenfolge wirkt plausibel (z. B. Pull-up: hängen | oben; Squat: stehen | tief)
3. Push-up oder Crunch: die beiden Posen stehen untereinander, gut erkennbar
4. Plank, Dead Hang, Hamstring Stretch: ein einzelnes Bild in grauer Fläche
5. Hell und dunkel: Figuren in Schwarz bzw. Weiß, graue Fläche mit feinem Rand wie die anderen Panels
6. Listen (Katalog, Workout): Icons wie vorher (manche Linien etwas kräftiger); eigene Übung ohne Bild zeigt weiter die Initialen

## Session AQ – Nur Dunkelmodus, schlichte Schalter (4.11.)
- Erledigt: App immer dunkel, auch wenn das Handy auf hell steht; „Darstellung“ im Profil entfernt. Schalter (Vibration, Ticken, Coach-/KI-Chat-Fotos) sind schlichte Schalter ohne „An/Aus“: links aus, rechts an. Details in docs/DECISIONS.md, Belege `docs/screenshots/nur-dunkel/`.

### Bitte auf dem Handy testen (Dunkelmodus, Schalter)
1. Handy-Einstellung auf hell stellen → App öffnen: alles dunkel, Statusleiste und Navigationsleiste mit hellen Symbolen, Startbildschirm schwarz
2. Profil: keine Zeile „Darstellung“ mehr
3. Profil → Vibration und „Ticken vor Pausenende“: Schalter ohne Text; aus = Knopf links, dunkle Spur mit Rand; an = Knopf rechts, weiße Spur; Tippen auf die ganze Zeile schaltet um
4. Coach → Einstellungen „Fotos senden“ und KI-Chat → Fotos: gleiche Schalter
5. TalkBack an: Schalter wird als „Schalter, an/aus“ mit Namen angesagt

## Session AQ – Doppeltipp statt Popups (4.11.)
- Erledigt: Alle Bestätigungs-Popups sind wieder ein Doppeltipp: erster Tipp färbt den Mülleimer bzw. das „×“ rot, bei Text-Knöpfen Text und Rahmen; zweiter Tipp innerhalb von 3 s führt aus. Tippen woanders, Scrollen oder Warten setzt zurück. Kein Zusatztext. Belege `docs/screenshots/doppeltipp/`, Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Doppeltipp)
1. Workout öffnen → Mülleimer oben rechts antippen → nur das Icon wird rot → nochmal tippen → Workout gelöscht
2. Mülleimer antippen und 3 s warten → wieder weiß, nichts gelöscht; antippen und dann woanders tippen oder scrollen → ebenfalls zurück
3. Training → „Beenden“ → Text und Rahmen rot → nochmal → Abschluss; „Training abbrechen“ ganz unten genauso
4. Kalender → Tag → Foto „×“ → rot → nochmal → entfernt
5. Profil → „Alle Daten löschen“ und Backup einspielen („Alle Daten ersetzen“) ebenso mit zwei Tipps; Coach: Chat löschen, Gedächtnis löschen
6. TalkBack an: nach dem ersten Tipp wird „zum Bestätigen erneut tippen“ angesagt

## Session AQ – Karten in Listen (4.11.)
- Erledigt: Workout ansehen und Training ansehen zeigen jede Übung als eigene Karte mit Übungsbild links (wie im laufenden Training); Tipp aufs Bild öffnet die Übung. Kalender-Liste: jeder Tag eine Karte; Tagesansicht: jedes Training eine Karte. Übungsblöcke im Training/Editor haben jetzt ebenfalls den feinen Rand. Belege `docs/screenshots/karten/`, Details in docs/DECISIONS.md.
- Offen: nichts.

### Bitte auf dem Handy testen (Karten)
1. Workout öffnen (z. B. Push): jede Übung eine Karte mit Bild links, Name, „4 Sätze · 60 kg × 8“; Bild antippen → Übungsdetails ohne Speichern-Knopf → Zurück
2. Kalender → Tag → Training: jede Übung eine Karte mit Bild, „x/y erledigt“ und den Sätzen darunter; Bild antippen → Übungsdetails
3. Kalender → Listen-Symbol: jeder Tag eine eigene Karte mit Abstand; Tipp öffnet den Tag
4. Tagesansicht: jedes Training eine Karte mit Pfeil
5. Laufendes Training: Übungen sehen aus wie vorher, nur mit feinem Rand; Ansehen und Trainieren wirken gleich

## Session AR – Knöpfe wie in der Banking-App (6.11.)
- Erledigt: Alle Knöpfe ohne Rand, großer Radius; Hauptknopf weiß mit schwarzem fettem Text links (64 dp), sekundäre dunkelgrau; beim Tippen kurz kleiner (0,96) und zurückfedern; Karten ebenso, ohne Rand. Doppeltipp: Text-Knopf scharf = rot gefüllt, weißer Text. Belege `docs/screenshots/buttons/`, Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Knöpfe)
1. Workouts → Workout öffnen: „Training starten“ weiß, schwarzer Text links, kein Rand
2. Knopf gedrückt halten: wird etwas kleiner, loslassen: federt zurück, kein Ripple; Workout-Karten ebenso
3. Training: Pausenleiste (−15 s, +15 s, Überspringen) dunkelgrau, „Beenden“ antippen → rot gefüllt mit weißem Text → nochmal → Abschluss
4. Mülleimer im Workout: erster Tipp rotes Icon, zweiter löscht
5. Onboarding „Weiter“ mit Pfeil rechts; Profil/Coach/Guthaben: keine Rahmen um Knöpfe

## Session AU – Cursor, Stift-Icon, Rot/Grün, Story-Balken (6.11.)
- Erledigt: Tipp außerhalb eines Textfelds, Scrollen oder Zurück bei offener Tastatur nimmt den Fokus (kein Cursor mehr, zentral im Theme). Workout bearbeiten über Stift-Icon oben rechts, großer „Bearbeiten“-Knopf entfällt. Rot nur noch für Löschen/Abbrechen; „Beenden“ scharf grün. Story-Balken füllen sich über 6 s. Belege `docs/screenshots/fokus-farben-story/vorher|nachher/`, Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Cursor, Stift, Farben, Story)
1. Training: in ein Gewichtsfeld tippen, dann auf leere Fläche/Übungsname tippen → Cursor und Tastatur weg; ebenso beim Scrollen und beim Zurück-Wischen mit offener Tastatur
2. Workout öffnen → Stift oben rechts → Bearbeiten-Modus; unten nur „Starten“
3. Training → „Beenden“ antippen → grün; „Training abbrechen“ und Mülleimer → rot
4. Coach → Story: aktueller Balken läuft von links nach rechts, nächste Seite nach ca. 6 s; Tipp startet den Balken neu

## Session AX – Ohne Rahmen (7.11.)
- Erledigt: alle Rahmen app-weit weg (nur Flächen), Hauptknopf unten schwebt ohne Leiste, Knöpfe zentriert, funktionsloser Stift im Workout-Editor entfernt, Profil → Benachrichtigungen „Erlaubt“ öffnet die Systemeinstellungen. Belege `docs/screenshots/ohne-rahmen/vorher|nachher/`.

### Bitte auf dem Handy testen (ohne Rahmen)
1. Workouts, Workout ansehen, Kalender, Profil, Coach: nirgends mehr ein Rahmen um Karten, Felder, Chips
2. Workout bearbeiten: nur ein Stift (oben), unten „Speichern“ schwebt ohne schwarze Leiste, Inhalt scrollt darunter
3. „Training starten“/„Speichern“: Text mittig im Knopf
4. Profil → Benachrichtigungen → „Erlaubt“ antippen → Android-Benachrichtigungseinstellungen der App
5. Schalter aus (grau gefüllt, kein Rand) noch klar erkennbar; Kalender „heute“ und Foto-Auswahl erkennbar

## Session AY – Import direkt im Editor (7.11.)
- Erledigt: Import (Zwischenablage, QR, Datei, Link, Coach-Vorschlag) öffnet den normalen Workout-Editor statt der Vorschau-Seite; Name ändern, Übungen ansehen (Tipp aufs Bild), umsortieren, Sätze ändern. Gespeichert erst mit „Speichern“; Zurück fragt wie gewohnt. Hinweis „Wird neu angelegt“ oben. Belege `docs/screenshots/import-editor/`.

### Bitte auf dem Handy testen (Import im Editor)
1. Workout-JSON kopieren → Neues Workout → Workout importieren → Aus Zwischenablage: Editor mit Name, Übungen, Sätzen, Pausen
2. Name ändern, Satz hinzufügen/löschen, Übung per Bild öffnen (Details), Speichern → Workout in der Liste
3. Import → Zurück → „Nicht gespeichert“ → nochmal Zurück: nichts gespeichert
4. Unbekannte Übung im JSON: Hinweis oben, nach Speichern als eigene Übung vorhanden
5. Coach-Vorschlag „Workout übernehmen“ und Posteingang-Vorschlag: öffnet ebenfalls den Editor; „Verwerfen“ oben

## Session AY – Story schließt sofort, „?“ weg (7.11.)
- Erledigt: „?“ am Übungsbild im Workout-Bearbeiten entfernt; Story schließt (Auto, X, Wischen, Zurück) ohne Animation; Story kommt nach „Alle Daten löschen“ beim ersten Coach-Besuch wieder (Flag wurde nicht zurückgesetzt).

### Bitte auf dem Handy testen (Story, „?“)
1. Workout → Stift → Bearbeiten: am Übungsbild kein „?“ mehr, Tipp aufs Bild öffnet weiter die Details
2. Coach → Story bis zur letzten Seite: schließt sofort, Tab-Leiste und Coach-Seite sofort da, kein Hochspringen; ebenso X, Wischen nach unten, Zurück
3. Profil → Alle Daten löschen → Onboarding → Coach-Tab: Story kommt automatisch; danach nur per Tipp auf „Coach ⓘ“

## Session AZ – Körpergewicht im Kalender, Zukunftsmonate, „Heute“ (7.11.)
- Erledigt: Gewicht tracken (eigene Tabelle, DB v10, ein Wert pro Tag), Schnell-Karte oben im Kalender mit Wert, Veränderung zur Vorwoche und 8-Wochen-Verlauf, Eintrag/Löschen in der Tagesansicht, Strich im Kalender + Legende, Wert in der Liste, Trend im Coach-Kontext, Backup inklusive. Kalender zeigt 12 Monate in die Zukunft (ausgegraut, nicht antippbar), „Heute“-Knopf springt zurück. Belege `docs/screenshots/gewicht-kalender/`, Details in docs/DECISIONS.md.
- Offen: nichts.

### Bitte auf dem Handy testen (Gewicht, Kalender)
1. Kalender-Tab: oben Karte „Körpergewicht“ (bzw. „Gewicht eintragen“); falls im Onboarding ein Gewicht eingegeben wurde, steht es schon da
2. Karte antippen → Tagesansicht von heute, Tastatur offen; z. B. 82,4 tippen → zurück → Karte zeigt 82,4 kg, heute im Kalender ein kleiner Strich unter der 7, Legende „Gewicht“
3. Nochmal öffnen, 81,9 eingeben → ersetzt den Wert (kein zweiter Eintrag); Mülleimer: erster Tipp rot, zweiter löscht
4. Ein paar Tage in der Vergangenheit eintragen (Tag antippen) → Verlauf in der Karte, „… zur Vorwoche“ sobald ein Wert von vor 7–14 Tagen da ist
5. Kalender nach oben scrollen → Dezember 2026 … November 2027, Tage grau und nicht antippbar; oben rechts erscheint das „Heute“-Symbol (Ring mit Punkt) → Tipp springt zum aktuellen Monat, Symbol verschwindet; ebenso nach unten in die Vergangenheit
6. Listenansicht: Tage mit Gewicht als Karte bzw. „Gewicht 82,4 kg“ in der Zeile; runterscrollen → „Heute“-Symbol → wieder oben
7. Titelzeile bei mehreren Fotos: „Vergleichen“ ist jetzt ein Symbol (zwei Rechtecke), daneben Muskel-Symbol und Liste/Kalender; nichts abgeschnitten
8. Profil auf lb stellen → Werte in lb; Coach fragen: kennt aktuelles Gewicht und Trend
9. Backup exportieren und wieder einspielen → Gewichte sind wieder da

## Session BA – Satzzeile grün, Felder hervorgehoben, Cursor rechts (7.11.)
- Erledigt: Abgehakter Satz im Training = ganze Zeile grün mit weißen Werten; Eingabefelder überall (Training, Workout bearbeiten, Training nachträglich bearbeiten) wieder leicht heller als die Zeile; Tipp in ein Satzfeld setzt den Cursor immer ans Ende. Belege `docs/screenshots/workout-gruen/vorher|nachher/`, Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (grüne Zeile, Felder, Cursor)
1. Training starten → Satz abhaken: ganze Zeile grün, Haken weiß, Felder hellgrün, Zahlen weiß; erneut tippen → wieder grau
2. Offene Sätze: Felder heller grau als die Zeile, Vorgabewerte grau
3. In ein Feld mit „25“ ganz links tippen → Cursor steht hinter der 5, Löschtaste löscht die 5
4. Weiter-Taste springt wie bisher zum nächsten Feld (Cursor am Ende), Fertig und Tipp daneben schließen die Tastatur
5. Workout → Stift → Bearbeiten und Kalender → Training → Stift: Felder ebenso leicht hervorgehoben, Cursor am Ende


## Session BC – KI-Anbieter umschaltbar (7.11.)
- Erledigt: Guthaben-Stufen (günstig/normal/stark) können jetzt von Anthropic, OpenAI, Google Gemini, Cloudflare Workers AI oder OpenRouter beantwortet werden. Umschalten ohne Code über die Auswahlliste „KI-Anbieter“ im Workflow „Backend deployen“ (Voreinstellungen `claude`, `openai`, `gemini`, `gratis`, `gemischt`, `openrouter` in `backend/llm-routing.jsonc`). Ausweichen auch über Anbietergrenzen, bezahlt wird das günstigere Modell; Protokoll und Buchungen nennen den Anbieter. Die App zeigt Modellnamen und Kosten aus dem Server und merkt sich die letzte Antwort für offline. Anleitung: docs/anbieter-umstellen.md, Details in docs/DECISIONS.md.
- Offen (Geld): Preis für `gratis` (Nutzer zahlen den Workers-AI-Listenpreis), OpenAI-/Workers-AI-Preise vor Produktion prüfen.

### Bitte auf dem Handy testen (KI-Anbieter)
1. Neue APK installieren → Coach → Einstellungen → Modell (Guthaben): drei Modelle mit Namen „Claude Haiku 5.5 (Anthropic)“, „Claude Sonnet 5.5 (Anthropic)“, „Claude Opus 5.5 (Anthropic)“ und Kosten je Auswertung, wie bisher
2. Training beenden → Coach-Auswertung über Guthaben: läuft wie bisher; Profil → Guthaben → Abrechnung: Eintrag mit Modell und Betrag
3. Optional umschalten: GitHub-App → Actions → „Backend deployen“ → Run workflow → mode `test`, KI-Anbieter `gratis` → nach dem Lauf in der App (App neu starten) zeigt die Modellwahl „Llama 4 Scout (Cloudflare)“; eine Auswertung ohne Foto kommt von Llama, mit Foto antwortet Claude Haiku („Ausweich-Modell“ im Hinweis). Danach wieder `claude` wählen
4. Flugmodus an, App neu starten → Coach-Einstellungen zeigen weiter die zuletzt geladenen Modellnamen
5. Mit eigenem API-Schlüssel: nichts ändert sich

## Session BE – Guthaben-Fehler im Coach-Chat behoben (8.11.)
- Ursache: Der Testserver erlaubte höchstens 2048 Antwort-Token, die App fragt im Chat 4000 und bei der Auswertung 8000 an → Server lehnte ab, bevor die KI gefragt wurde („Provider rejected the request“). Nichts berechnet.
- Zweiter Fehler: „Transactions“ meldete „can't be reached“, weil der Server Ledger-Zeilen roh lieferte (0/1 statt true/false). Server und App korrigiert.
- Erledigt: Backend kappt statt abzulehnen (Testserver jetzt 8192 wie Produktion), App hält sich an das Server-Limit, keine sinnlose Wiederholung, verständlichere Fehlermeldung im Chat, Anbieter-Fehlercode (ohne Text) in der Abrechnung. Test-Guthaben-Obergrenze 40 €. Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Coach-Chat über Guthaben)
1. Coach → Chat (App auf Englisch), Profil-Chip an, mit „+“ letztes Training anhängen, Frage stellen → Antwort kommt, darunter Kosten
2. Neue Frage mit Foto anhängen → Antwort kommt (ggf. Hinweis Ausweich-Modell)
3. Training beenden → Coach-Auswertung über Guthaben kommt vollständig
4. Profil → Guthaben → Abrechnung: Einträge „Chat“ und „Auswertung“ mit Modell und Betrag, Status OK; der alte fehlgeschlagene Chat-Eintrag kostete 0
5. Die alte Fehlermeldung im Chat: „Retry“ → jetzt kommt eine Antwort
6. Profil → Guthaben → Transactions: Liste mit Test-Gutschriften und Chat-/Auswertungs-Buchungen statt „Your credit can't be reached“

## Session BF – Opus-Chat „No connection“, reservierte 0,61 € (8.11.)
- Ursache: Die Android-App wartete auf eine Guthaben-Antwort nur 10 s (Standard der HTTP-Bibliothek, unser 130-s-Wert galt nur fürs Ganze). Opus braucht oft 10–35 s → App brach ab, zeigte „No connection“. Der Server wurde dabei mitten im Warten abgebrochen, die Vorab-Reservierung (~0,20 € je Anfrage) blieb 15 min stehen.
- Geld verloren: nein. Nichts abgebucht, die 0,61 € kamen nach Ablauf der Reservierungen von selbst zurück.
- Erledigt: App wartet bei Nachrichten bis 240 s (eigener Schlüssel 150 s), zeigt bei Zeitüberschreitung „Keine Antwort in der Zeit“ statt „No connection“; Server gibt Reservierungen in jedem Fehlerpfad frei, rechnet nach einem Abbruch noch bis 30 s ab, Reservierungen verfallen nach 5 statt 15 min; Kontostand zeigt laufende Reservierungen, Buchungsliste hat eine Zeile „Reserviert“. Test-Guthaben-Obergrenze 100 €. Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (Opus-Chat)
1. Profil → Guthaben: Kontostand notieren (Transactions: Summe der Buchungen = Kontostand, keine Zeile „Reserviert“).
2. Coach → Chat, Modell Claude Opus 5.5 (Guthaben), kurze Frage („Wie viele Ruhetage brauche ich?“) → Antwort nach wenigen Sekunden, darunter Kosten.
3. Lange Frage („Schreib mir einen ausführlichen 12-Wochen-Plan …“) → Antwort nach bis zu ~40 s, kein „No connection“.
4. Während 3 läuft: Guthaben-Popup öffnen → „(… € reserviert)“ neben dem Kontostand; nach der Antwort ist der Zusatz weg.
5. Profil → Guthaben → Transactions: zwei neue Opus-Buchungen; Summe aller Buchungen = Kontostand. Anfrage-Protokoll: beide „OK“.
6. Flugmodus an, Frage senden → „No connection“ (echtes Netzproblem); Flugmodus aus, „Retry“ → Antwort.

## Session BG – Nach App-Wechsel keine Tipps möglich (8.11.)
- Ursache: Beim Wechsel zwischen Screens legt die App kurz eine unsichtbare Fläche über alles, damit ein Doppeltipp nichts doppelt öffnet.
  Eine angefangene und abgebrochene Zurück-Geste (Wisch vom Rand; mit Gestensteuerung auch beim App-Wechsel) ließ diese Fläche für immer
  liegen. Zurück beendete die Geste, dann ging es wieder.
- Erledigt: Die Fläche liegt jetzt höchstens 0,4 s. Test `ResumeTapTest` (vor dem Fix rot, danach grün). Details in docs/DECISIONS.md.

### Bitte auf dem Handy testen (App-Wechsel)
1. Training läuft → Spotify öffnen → zurück zu Overfit → sofort Satz abhaken: Haken reagiert
2. Satz abhaken (Pause läuft) → Home → Pausen-Benachrichtigung antippen → Training offen, sofort tippen (z. B. „Überspringen“) reagiert
3. Coach-Chat, ins Textfeld tippen (Tastatur offen) → App wechseln → zurück → Senden-Knopf, Chip oder Liste antippen reagiert
4. Kalender, Muskel-Sheet öffnen → App wechseln → zurück → Sheet antippen bzw. daneben tippen (schließt) reagiert
5. Im Training vom linken Rand etwas reinwischen und wieder loslassen (Zurück-Geste abbrechen) → Satz abhaken reagiert sofort
