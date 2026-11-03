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
| 7 | Credits-System (Coach-Guthaben) | Backend + API-Vertrag fertig; App-Anbindung (Guthaben, Demo) fertig; Modelle Gemini, Claude, Workers AI über AI Gateway (Session X), echte Modellnamen und volle Kostentransparenz (Session Y); Store-Kauf offen (braucht Konten) |
| 8 | Trainings teilen (Link, Share-Sheet, QR, Import) | erledigt (Vorlagen: Session B, abgeschlossene Trainings: Session F) |
| 9 | Einladen per QR mit Startguthaben (NFC später als Spielerei) | App-Seite fertig (QR, Einlösen, Reveal); Server-Deployment und Attestierung offen |
| 10 | KI-Chat-Verbindungen (Claude, ChatGPT, Gemini per MCP) | 10a Backend + Konzept fertig (Nacht 3.11.), 10b App-Seite fertig (Session H, ohne Foto-Upload und Coach-Gedächtnis – kommen nach Phase 3/4); Deployment des Backends offen |

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
3. Mit Testserver: Fuel ansehen → „Testserver“-Hinweis, 1,00 € Saldo; „Fuel nutzen“ im Coach → Antwort kostet Cent-Beträge. Spot einlösen klappt ohne Store-Konto.

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
1. Training-Tab: Workout-Zeilen und Coach/Duo haben Chevron; beim Drücken färbt sich die Zeile grau.
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
Reihenfolge wie ein echter Tag. Die Einzel-Abschnitte weiter unten haben mehr Details. Ohne Backend gehen alle Punkte außer 34–35 (und echtes Fuel/Spot); die Demo in 36–38 braucht keinen Server.
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
31. Start ganz unten: „Duo · bald“ → Erklärtext, keine Eingabe

**KI-Chat, Kontext kopieren**
32. Profil → KI-Coach → Gemini (oder Claude/ChatGPT): „Kontext kopieren“ → in Notizen einfügen: Profil, letzte Trainings, ```json-Block
33. „Gem-/GPT-Anleitung kopieren“ → einfügen und lesen
34. Mit Backend: „KI-Chat verbinden“ → Zustimmen → Kopplungscode, Connector im Chat anlegen, „Plane mir ein Push-Training“ → Eingang auf dem Start-Tab → Vorschau → Übernehmen
35. „Trennen“ und „KI-Chat trennen“ (zweimal tippen) → Chat hat keinen Zugriff mehr

**Fuel und Spot**
36. Profil → „Fuel · startet bald“ → „Demo ansehen“: „Fuel · 4,20 €“, Pakete („Bald im Store“), Buchungen, Wiederherstellungscode
37. „Freund spotten“: QR mit rotierendem Code, Name; „Ich wurde gespottet“: beliebigen Link einfügen → Einlösen → schwarz, zwei Pulse,
    Zahl zählt 0,00 → 1,00 €, „Spot von Fabian“, kräftiger Puls; Tippen schließt
38. „Demo beenden“ → Fuel zeigt wieder „startet bald“. (Echtes Fuel/Spot erst mit Backend, Punkt 1 unten)

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
   (Fuel, Spot, KI-Chat-Verbindung bleiben dann lokal/ohne Server.)
2. **GitHub Pages aktivieren** (Repo → Settings → Pages → Source „GitHub Actions“; oder Actions → „Web veröffentlichen“ → Run workflow):
   Web-Fallback `https://bigfire3.github.io/overfit/` und Teilen-Seite `/t` für Links ohne App
3. **KI-Chat-Connectoren eintragen** (nach 1.): Claude → Connectors → Custom Connector, ChatGPT → Entwicklermodus → Connector,
   Gemini → Eigene Apps, jeweils `https://<worker>/mcp`; Schritte stehen in der App unter Profil → KI-Coach
4. **Zu prüfen (Annahmen von Session G):** Standardmodelle OpenAI `gpt-5` und OpenRouter `anthropic/claude-sonnet-5.5` sind geraten – in
   Profil → KI-Coach → Coach-Einstellungen mit echtem Key ausprobieren und ggf. korrigieren; „Zur Prüfung durch Fabian“ in `docs/DECISIONS.md` lesen
5. **Datenschutzerklärung vor dem Store-Start** um KI-Chat-Verbindung (Art. 9 DSGVO, Opt-in) und Daten an Chat-Anbieter ergänzen
6. **Store-Konten (Phase 6/7):** Google-Play-Console und Apple-Developer-Konto, Play Billing / StoreKit, Play Integrity (Cloud-Projekt), App Attest,
   Server-Prüfer in `backend/src/verifiers.ts`. Store-Texte DE/EN liegen als Entwurf in `docs/store/store-listing.md`
7. **Eigene Domain** (optional, später): Android App Links / iOS Universal Links (`/.well-known/`, Associated Domains), Web-Seite für Spot-Links
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

## Nacht 3.11. – Fuel & Spot in der App (Session D)
Fertig:
- Geräte-Identität (Keystore / Secure Enclave), signierte Anfragen nach `docs/backend-api.md`, `FuelApi` (Ktor) mit Re-Registrierung und Zeitkorrektur, Fehlercodes
- Profil → **Fuel**: Saldo („Fuel · 4,20 €“), verfallendes Guthaben, Pakete 5/10 € („Bald im Store“), Buchungen, Wiederherstellungscode (erzeugen, kopieren, auf neuem Handy eingeben)
- **Spot**: „Freund spotten“ (Name, QR mit rotierendem Token, Haarlinien-Fortschritt), „Ich wurde gespottet“ (Android: QR scannen oder Link einfügen; iOS: Kamera-App öffnet den Link), Reveal mit Haptik
- **Demo** (ohne Server): Profil → Fuel → „Demo ansehen“; Backend-URL per CI-Variable `OVERFIT_BACKEND_URL`, versteckter Override (7× Version in „Über“)
- Fuel als Option im KI-Zugang (`FuelProvider`); Tests: SHA/Signatur/Testvektor, Beträge, Reveal, Fuel-Client (MockEngine), QR, UI-Durchlauf Demo

### Von Fabian zu tun (Fuel/Spot)
1. Backend deployen (siehe oben), dann GitHub → Settings → Secrets and variables → **Variables** → `OVERFIT_BACKEND_URL` = Worker-URL anlegen. Ohne sie zeigt die App „startet bald“ + Demo.
2. Play-/Apple-Konten für echte Käufe (Play Billing / StoreKit) und Geräteprüfung (Play Integrity Cloud-Projekt, App Attest); Server-Prüfer ergänzen (Tokenformat siehe DECISIONS).
3. Optional: Web-Seite für Spot-Links ohne installierte App (QR enthält `overfit://spot#…`).

### Bitte auf dem Handy testen (Fuel/Spot)
1. Profil → „Fuel · startet bald“ öffnen → Text „Fuel startet bald“ → **Demo ansehen**
2. Demo: „Fuel · 4,20 €“, Pakete mit „Bald im Store“, Buchungen (Verbrauch, Aufladung, Spot), Wiederherstellungscode erzeugen + kopieren
3. **Freund spotten:** QR erscheint, Haarlinie läuft ab, alle ~30 s neuer Code; Namen eintragen
4. **Ich wurde gespottet:** beliebigen Link/Code einfügen → Einlösen → Bildschirm schwarz, zwei Herzschlag-Pulse, Zahl zählt 0,00 → 1,00 €, „Spot von Fabian“, kräftiger Puls; Tippen schließt
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
- „Duo · bald“ auf dem Startbildschirm → Teaser-Screen
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

## Nacht 3.11. – Backend Fuel/Spot + Web-Fallback (Session C)
Fertig (kein App-Code angefasst):
- `backend/`: Cloudflare Worker + D1, 42 Tests (Vitest/Miniflare, lokal ohne Cloudflare-Konto): anonyme Geräte-Identität
  (ECDSA P-256, signierte Anfragen mit Nonce/Zeitstempel), Konto mit Saldo/Buchungen in Mikro-Euro, Wiederherstellungscode,
  `POST /v1/messages` als Anthropic-Proxy (atomare Reservierung, Abrechnung nach `usage` + Aufschlag, Freigabe bei Fehlern),
  Kauf-Verifikation (idempotent, Paket-Gutschrift = Preis × (1 − Store) / (1 + Aufschlag)), Spot (Token, Challenge, Einlösen,
  Limits, Budget, Verfall, Belohnung bei der ersten Coach-Anfrage), Rate-Limits, einheitliches Fehlerformat
- API-Vertrag mit exaktem Signatur-Schema: `docs/backend-api.md` (Grundlage für die App-Session)
- `web/`: Fallback-Seite `t/index.html` (liest `#payload` laut `docs/share-format.md`, DE/EN, „In Overfit öffnen“, APK-Download)
  und Startseite; Playwright-Rauchtest; Workflow `pages.yml` zum Veröffentlichen; `backend.yml` testet bei Änderungen
- DECISIONS: „Produktentscheidungen Nacht 3.11.“ + Backend-Entscheidungen

Offen: Echte Google-Play-/Apple-Prüfer für Käufe und Play Integrity/App Attest (Schnittstellen + Mocks fertig, Produktion
antwortet bis dahin 503 `purchases_unavailable` / `attestation_unavailable`); App-Anbindung (`LlmProvider` „Fuel“, Spot-UI) nach `docs/backend-api.md`.

### Von Fabian zu tun
1. **GitHub Pages aktivieren:** Repo → Settings → Pages → Source „GitHub Actions“. Danach läuft `pages.yml` bei jedem Merge
   mit Änderungen in `web/` (oder manuell: Actions → „Web veröffentlichen“ → Run workflow). Ergebnis: https://bigfire3.github.io/overfit/
2. **Cloudflare-Backend deployen** (Schritte und alle Secrets in `backend/README.md`): Konto, `wrangler login`, `wrangler d1 create overfit`
   (ID in `wrangler.jsonc`), Migrationen, `ANTHROPIC_API_KEY` und `SPOT_SIGNING_KEY` als Secrets, `npm run deploy`. Worker-URL
   an die Session für die App-Anbindung geben.
3. Später für Käufe/Spot in Produktion: Play-Console- und Apple-Developer-Konten (Phase 6), Dienstkonto/Schlüssel als Secrets
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
8. Startbildschirm ganz unten: „Duo · bald“ → Erklärtext
9. Zwischenablage leer oder Text ohne Training → verständliche Fehlermeldung

## Bitte auf dem Handy testen: App-Icon „of“ (Session W)
1. Neue APK installieren: Icon „of“ (weiß auf Schwarz) auf dem Homescreen, in der App-Übersicht und in den Einstellungen; Launcher mit runden und eckigen Masken ansehen, die Schrift muss nie angeschnitten sein
2. Hell/Dunkel: Hintergrundbild hell und dunkel, Icon bleibt gut lesbar; iOS 18: Homescreen auf „Dunkel“ und „Getönt“ stellen
3. Android 13+: Einstellungen → Hintergrund und Stil → „Themed icons“ an → „of“ erscheint einfarbig in der Systemfarbe
4. App starten: Splash schwarz mit kleinem weißen „of“ (Android 12+ und iOS), danach direkt die App
5. Browser: Favicon im Tab der Webseite, „Zum Home-Bildschirm“ auf dem iPhone zeigt das „of“
6. Variante gefällt nicht? `python3 tools/icon/build_icon.py --variant B2` (B1–B6, siehe docs/DECISIONS.md), committen
