# Overfit – Spezifikation

Öffentliche Open-Source-Fitness-App (MIT) für **Android und iOS** (keine Accounts, kein eigenes Backend, solange es nur den eigenen API-Key gibt; siehe „Erweiterung: Teilen, Open Source, Coach-Guthaben“). Man loggt Krafttraining und Calisthenics. Im Workout-Modus läuft ein Pausentimer. Nach dem Training bewertet ein KI-Coach das Training inkl. optionalem Körperfoto, pflegt ein Langzeitgedächtnis und passt den Plan fürs nächste Mal an.
- App-Name: Overfit, Android applicationId und iOS Bundle-ID: `app.overfit`
- Extrem minimalistisch in Funktion und Design.
- UI-Sprache **Deutsch und Englisch**: aus der Gerätesprache übernommen, in den Einstellungen umschaltbar. Die App-Sprache ist auch die Antwortsprache des Coaches.
- Design **schwarz-weiß**, angelehnt an den Stil von Trade Republic (Inspiration, keine Kopie von Logo, Name, Hausschrift oder Screens): keine Akzentfarbe, Typografie und Weißraum tragen das Design, Haarlinien statt Cards, ein invertierter Pill-Button pro Screen, dünne Linien-Icons. Dunkel als Standard, Hell und System wählbar. Große Touch-Flächen.
- Erledigte Sätze werden über Kontrast und Häkchen markiert, nicht über Farbe.
- Einheit kg oder lb (aus der Region vorbelegt, umschaltbar). Intern immer kg.
- Keine Werbung, kein Tracking, keine Analytics-SDKs.

## Onboarding
Eine Frage pro Screen, groß, Tastatur sofort offen, dünne Fortschrittsanzeige, ein Button „Weiter“:
1. Wie sollen wir dich nennen? (Anzeigename)
2. Wie alt bist du? (gespeichert wird das Geburtsjahr)
3. Wie viel wiegst du? (erster Eintrag der Gewichts-Zeitreihe, mit Datum)
4. Verbinde deine KI: Anbieter wählen, API-Key einfügen, Prüfung ohne Token-Kosten, Link „Wie bekomme ich einen Key?“, Datenschutz-Hinweis. „Später einrichten“ möglich.

Nicht im Onboarding: Sprache (Gerät/Einstellungen), Ziel, Größe, Erfahrung, Einschränkungen. Die fragt der Coach im Gespräch nach und merkt sie sich (Coach-Gedächtnis / UserProfile).

## KI-Zugang
- Zuerst nur eigener API-Key (BYOK). Anbieter austauschbar hinter `LlmProvider` (aktuell OpenRouter, Anthropic, OpenAI). Aufrufe direkt vom Gerät per HTTP (Ktor).
- Später **Credits** für Nutzer ohne eigenen Key: eigenes Backend als zweite `LlmProvider`-Implementierung. Auf iOS ist dafür In-App-Purchase Pflicht (App Store Guideline 3.1.1), auf Android Google Play Billing. Mit eigenem Key bleibt die App komplett kostenlos. Details unten unter „Coach-Guthaben“.
- **Fünf Wege zur KI** (Einstellungsseite „KI-Coach“): eigener API-Key, Guthaben, und als Chat-Verbindung Claude, ChatGPT,
  Gemini – dort nutzt man das eigene Chat-Abo, ohne Key und ohne Guthaben (Details unter „KI-Chat-Verbindungen“).
- Jede Anfrage bekommt einen kleinen Profil-Block (Name, Alter aus Geburtsjahr, neuestes Gewicht mit Datum, Antwortsprache, Einheiten, heutiges Datum).

## Tech-Stack
- **Kotlin Multiplatform + Compose Multiplatform**: UI und Logik gemeinsam in `:shared` (commonMain), Plattform-Hüllen `:app` (Android) und `iosApp/` (iOS, SwiftUI-Host).
- Compose Foundation (kein Material, eigenes Designsystem), Navigation Compose (JetBrains-Multiplatform-Variante), kotlinx (Coroutines, Serialization, Datetime), Ktor Client (OkHttp / Darwin), Compose Resources (Schrift Inter).
- Später für Trainingsdaten: Room (KMP) mit exportiertem Schema, Coil 3 (KMP) für Bilder. Plattform-spezifisch über `expect`/`actual`: Pausentimer im Hintergrund, Kamera, Benachrichtigungen, Keystore/Keychain.
- Android: minSdk 26, aktuelles targetSdk, Gradle Kotlin DSL + Version Catalog. iOS: ab iOS 16, nur iPhone zum Start.
- API-Key: Android Keystore (AES-GCM) bzw. iOS Keychain (nur dieses Gerät), nie in Code, Repo oder Logs.
- Niemals destruktive Migrationen: Nutzerdaten dürfen nie verloren gehen. Jede Schemaänderung bekommt eine Migration + Migrationstest.

## Build & Verteilung
- Du richtest alles selbst ein (Gradle-Projekt, Workflows, README). Secrets lege ich an; du sagst mir genau, welche mit welchem Inhalt.
- Signatur: Der Keystore liegt nie im Repo, sondern in den GitHub-Secrets `OVERFIT_KEYSTORE_BASE64` und `OVERFIT_KEYSTORE_PASSWORD` (siehe README). Jedes CI-APK muss dieselbe Signatur haben, damit Updates ohne Neuinstallation und ohne Datenverlust gehen.
- Google Play: CI baut zusätzlich ein signiertes App Bundle (`.aab`). Beim Anlegen der App in der Play Console denselben Schlüssel als App-Signaturschlüssel hinterlegen, damit GitHub-APK und Play-Version dieselbe Signatur haben. Upload zu Play und TestFlight folgt in Phase 6.
- iOS: Xcode-Projekt wird mit XcodeGen aus `iosApp/project.yml` erzeugt. iOS-CI (macOS-Runner) läuft nur bei PRs nach main, Pushes auf main und manuell.
- versionCode automatisch aus der CI-Run-Nummer, damit jedes neue APK als Update installierbar ist.
- GitHub Actions: Build bei jedem Push auf main und auf claude/**-Branches. Signiertes Release-APK bauen, dann ein Pre-Release mit festem Tag „latest" aktualisieren, mit dem APK als Asset (Artefakte kommen nur als ZIP, das Release kann ich direkt am Handy laden). Bei Tags v* zusätzlich ein versioniertes Release.
- Versuche, das Android SDK in deiner Umgebung zu installieren und selbst zu bauen und zu testen. Klappt das nicht, arbeite besonders sorgfältig, prüfe den CI-Lauf, soweit du Zugriff hast, und sag mir sonst, was ich in der Actions-Ansicht kontrollieren soll.
- README: wie ich das APK am Handy lade und installiere (im Browser bei GitHub eingeloggt sein, Release „latest", einmalig „Unbekannte Apps installieren" erlauben) und wie Updates funktionieren.

## Datenmodell (Vorschlag, darfst du verbessern)
- Übungs-IDs müssen geräteübergreifend stabil sein (z. B. `wg:<slug>`, `fdb:<id>`, eigene Übungen: UUID), damit geteilte Trainings auf anderen Geräten auflösbar sind. Vorlagen und Sessions bekommen ebenfalls eine UUID.
- Exercise: id, name, source (WORKOUT_GUIDE | CUSTOM; FREE_DB nur noch in alten Backups), sourceId?, imageRef, primaryMuscles, secondaryMuscles, equipment, trackingType, defaultRestSec, note
- trackingType: WEIGHT_REPS (Gewicht darf negativ sein = Unterstützung, z. B. Band; positiv bei Körpergewichtsübungen = Zusatzgewicht), REPS, TIME (Sekunden, z. B. Handstand-Hold), WEIGHT_TIME
- WorkoutTemplate + TemplateExercise (Reihenfolge, geplante Sätze, Pausenzeit)
- WorkoutSession: Start, Ende, templateId?, Notiz, Gefühl 1–5 (optional), photoPath?, aiStatus (NONE, QUEUED, SENT, DONE, FAILED)
- SessionExercise: Reihenfolge, Herkunft (geplant / hinzugefügt / ersetzt für X)
- SetEntry: geplant vs. tatsächlich (weightKg, reps, durationSec), completed, completedAt, optional RPE (per Einstellung einblendbar, Standard aus)
- CoachMemory: aktueller Text + Versionshistorie
- AiReview: sessionId, Rohantwort, Bewertung, Tipps, Planvorschlag, Status (offen / übernommen / abgelehnt)
- UserProfile: Ziele, Erfahrung, Körpergewicht, Einschränkungen, Freitext

## Übungsbilder & Katalog
- Hauptquelle: Workout Guide (github.com/bryllim/workout-guide, npm @bryllim/workout-guide): 302 Übungen, je 3 schematische SVG-Frames. Frame 1 als Standbild in Listen; in der großen Detailansicht laufen alle 3 Frames ruhig durch (1→2→3→2→1, seit Session AH). SVG beim Rendern in der Theme-Farbe einfärben.
- ~~Ergänzung durch free-exercise-db~~ – am 3.11.2026 entfernt (Fotos passten nicht ins Design). Fehlende Übungen legt man als eigene Übung an; „Mit KI ausfüllen“ ergänzt aus dem Namen Erfassungsart, Muskeln, Equipment, Pause und eine Ausführungsnotiz (alles bearbeitbar), vorher „Meintest du …?“ gegen Dubletten. Auch im Workout-Modus über den Übungs-Picker.
- Import-Skript im Repo: Workout Guide an festen Commit gepinnt laden, Ergebnis als exercises.json ins App-Asset. Workout-Guide-SVGs offline mitbündeln. Keine Bilder aus dem Netz.
- Lizenz: Workout-Guide-Grafiken sind CC BY-SA 4.0 → Namensnennung (Bryl Lim, Everkinetic) im „Über"-Screen, Dateien unverändert lassen (Einfärben beim Rendern ist erlaubt).
- Keine Anleitungstexte: nur Name, Bild, Muskelgruppe. Fuzzy-Suche + Filter Muskelgruppe/Equipment.
- Eigene Übungen: Name, trackingType, Pausenzeit; ohne Bild → Initialen-Kachel (z. B. „HA").

## Workout-Modus
- Start aus Vorlage oder leer. Kopfzeile: Name (editierbar), laufende Gesamtzeit, „Beenden".
- Übungskarten mit Bild, Name, „x/y erledigt". Satzzeilen passend zum trackingType mit großen Zahlenfeldern. Werte vom letzten Mal als Vorbelegung + kleine Zeile „Letztes Mal: 90 kg × 4, 90 × 3".
- Satz abhaken → Zeile wird grün, Pausentimer startet automatisch.
- Jederzeit variierbar: Satz hinzufügen/löschen/duplizieren, Übung hinzufügen, ersetzen, entfernen, per Drag & Drop umsortieren, Notiz pro Übung.
- Unten Button „Nächsten Satz loggen".
- State laufend in Room speichern: nach App-Kill oder Neustart geht das Training nahtlos weiter.
- Option „Bildschirm anlassen".

## Pausentimer
- Dauer pro Übung (aus Übung/Vorlage), globaler Standard in den Einstellungen.
- Zwei Zustände, nie Vollbild:
  - minimiert: schmale Leiste unten mit Countdown, Fortschrittsbalken, „Nächste: Lateral Raise · Satz 3 · 30 kg × 6", ±15 s, Skip
  - erweitert: Bottom Sheet mit denselben Infos, größer
- Die Trainingsliste bleibt immer scroll- und editierbar. Tap auf „Nächste" springt zur Satzzeile, Werte dort sofort änderbar.
- Pausenende: Vibration immer, Ton optional. Muss auch bei gesperrtem Bildschirm und App im Hintergrund zuverlässig auslösen. Android: laufende Benachrichtigung mit Countdown und Aktionen (+15 s, Skip), robuster Weg für Android 14+ (Foreground-Service-Typ bzw. exakte Alarme). iOS: geplante lokale Benachrichtigung zum Pausenende, optional Live Activity. Begründung in DECISIONS.md.

## Training beenden & KI-Coach
Ablauf:
1. Zusammenfassung: Dauer, Sätze, Volumen, Abweichungen vom Plan.
2. Optional Foto aufnehmen (Kamera): vorwiegend Körper-Fortschritt, zugleich Foto-Tagebuch. Optional Notiz + Gefühl.
3. „An Coach senden" oder „Später". Ohne Netz → Warteschlange (WorkManager), Versand sobald online.

Request (über die Provider-Abstraktion; bei Anthropic die Claude Messages API per direktem HTTP-Call, Modell in den Einstellungen wählbar, Default claude-sonnet-5-5):
- System-Prompt: Coach-Rolle und Regeln (als editierbarer Text in der App): realistische Steigerungen, Stagnation erkennen und gegensteuern, Calisthenics-Progressionen berücksichtigen, Antworten in der App-Sprache. Dazu der Profil-Block. Dazu eine kompakte Liste aller verfügbaren Übungs-IDs. Prompt-Caching für diesen statischen Teil nutzen.
- Kontext: Profil, Coach-Gedächtnis, letzte 3–5 Einheiten derselben Vorlage + letzte Einheit insgesamt, das heutige Training vollständig (geplant vs. gemacht, Ersetzungen, Pausen, Dauer, Notizen).
- Foto (falls vorhanden): auf ca. 1500 px lange Kante verkleinert, JPEG. Dazu das jüngste Fortschrittsfoto, das mindestens 4 Wochen alt ist, zum Vergleich.

Antwort als strukturiertes JSON (Structured Outputs bzw. ein Tool mit `strict: true` und `tool_choice: auto` plus klarer Anweisung). Hinweis: Claude Sonnet 5.5 lehnt erzwungenes `tool_choice` (`any`/`tool`) mit HTTP 400 ab.
- rating (1–10), summary, positives[], improvements[], tips[]
- photo_feedback (nur mit Foto): sachlich und konstruktiv, Fokus auf sichtbare Veränderungen gegenüber dem Vergleichsfoto
- next_session: Übungen nur mit IDs aus der Liste (neue Übungen als markierter Vorschlag mit Begründung), Sätze mit Zielwerten, Pausenzeiten, kurze Begründung je Änderung
- memory: komplett überarbeitete Fassung des Coach-Gedächtnisses (max. ca. 1500 Wörter): Ziele, Fortschritt je Hauptübung, Plateaus, Vorlieben, Beschwerden, was funktioniert

Ergebnis-Screen: Bewertung, Tipps, Foto-Feedback, Plan-Diff („Bankdrücken: 90 → 92,5 kg", „Neu: …") mit Alle annehmen / Ablehnen / einzeln. Ein übernommener Plan aktualisiert die Vorlage.
Robustheit: Schema validieren, bei ungültiger Antwort einmal neu versuchen, Rohantwort speichern, Fehler verständlich anzeigen.
API-Key: Eingabe im Onboarding oder in den Einstellungen, Prüfung beim Verbinden, gespeichert in Keystore/Keychain.

## Weitere Screens
- Start: Vorlagen, „Training starten", letzte Coach-Tipps
- Verlauf: vergangene Trainings, Detail mit Sätzen + Coach-Bewertung (seit 3.11.26 ohne eigenen Tab: Kalender → Tag → Training, nachträglich bearbeitbar)
- Foto-Tagebuch: Zeitleiste/Grid, zwei Fotos nebeneinander vergleichen. Fotos nur im app-privaten Speicher, nicht in der Galerie
- Coach-Gedächtnis: lesen, bearbeiten, Versionen
- Profil/Einstellungen: Sprache, Darstellung, Name, Alter, Einheit, KI-Zugang, Modell, Standard-Pause, Ton, Bildschirm an, RPE an/aus, Bilder offline laden, Backup, Alle Daten löschen
- Backup: Export/Import als JSON, optional ZIP mit Fotos, über die Dateiauswahl
- Über: App-Version, Lizenzen und Namensnennungen

## Bewusst NICHT
Login-Accounts, Cloud-Sync, Social-Feed, Gamification, Werbung, Tracking, Übungsanleitungen, Videos, Animationen (Ausnahme: die ruhige Frame-Animation der Übungsgrafik in der Detailansicht).
Erlaubt sind: einzelne Trainings per Link/QR teilen, das optionale Coach-Guthaben mit anonymer Geräte-Identität und die
ausdrücklich freiwillige KI-Chat-Verbindung (verschlüsselter Upload nur für Claude/ChatGPT/Gemini, siehe unten).

## Phasen
0. Projekt-Setup: Gradle-Projekt, App-Gerüst, Keystore, CI mit Release „latest", README, docs/DECISIONS.md → erstes installierbares APK ✔
0b. Umstieg auf Kotlin Multiplatform, Designsystem schwarz-weiß, DE/EN, Onboarding, eigener API-Key, iOS-Hülle ✔
1. Übungskatalog (Import-Skript, Suche, Filter) + eigene Übungen, Vorlagen anlegen/bearbeiten
2. Workout-Modus inkl. Live-Änderungen und Persistenz, Pausentimer (minimiert/erweitert, Hintergrund, Benachrichtigung)
3. Beenden-Flow, Foto, Verlauf, Foto-Tagebuch, Backup
4. KI-Coach: Request, Antwort-Schema, Gedächtnis, Plan-Diff, Warteschlange
5. Feinschliff: leere Zustände, Fehlerfälle, Performance, README vervollständigen
6. Store-Release: App Store (iOS) und Play Store (Android)
7. Credits-System (eigenes Backend, In-App-Purchase)
8. Trainings teilen: Share-Link, Share-Sheet (WhatsApp, Quick Share, AirDrop), QR anzeigen/scannen, Import mit Vorschau, Web-Fallback-Seite (kann nach Phase 3 vorgezogen werden)
9. Einladen per QR mit Startguthaben, Attestierung, Missbrauchsschutz (setzt Phase 6 und 7 voraus)
10. KI-Chat-Verbindungen (Claude, ChatGPT, Gemini per Remote-MCP): 10a Backend + Konzept ✔ (Nacht 3.11.), 10b App-Seite
    (Einstellungsseite mit fünf KI-Wegen, Kopplungscode, Sync, Eingang mit Import-Vorschau, „Kontext kopieren“; nach Phase 3)
Später als Spielerei: NFC-Einladung.

## Erweiterung: Teilen, Open Source, Coach-Guthaben

### Grundsätze
- Die App ist Open Source (MIT). Mit eigenem API-Key ist sie komplett kostenlos und spricht nur direkt mit Anthropic.
- Das Coach-Guthaben ist freiwillig (Arbeitsname, der endgültige Name kommt noch): KI-Anfragen laufen über einen Overfit-Proxy und werden zum API-Preis plus kleinem Aufschlag abgerechnet. Ziel ist, ein paar Prozent der KI-Ausgaben als Einnahme zu behalten, nicht Gewinnmaximierung.
- Keine Login-Accounts. Das Guthaben hängt an einer anonymen Geräte-Identität (Schlüsselpaar im Android Keystore bzw. in der Secure Enclave). Ein Wiederherstellungscode erlaubt den Umzug auf ein neues Handy.

### Trainings teilen (Phase 8, ohne Backend)
- Teilbar: Vorlagen und abgeschlossene Trainings (als Vorlage übernehmbar, optional mit Ist-Werten). Fotos, Notizen, Gefühl und Coach-Gedächtnis werden nie geteilt.
- Format: JSON `{v, kind, name, exercises:[{id, sets, restSec}], custom:[{ref, name, trackingType}]}`, mit Deflate komprimiert und Base64url-kodiert, im URL-Fragment: `https://<domain>/t#<payload>`. Das Fragment erreicht nie einen Server, deshalb braucht das Teilen kein Backend.
- Kanäle: Das System-Share-Sheet (`ACTION_SEND` mit Link-Text) deckt WhatsApp, Quick Share, Signal, Mail usw. ab; auf iOS übernimmt das `UIActivityViewController` inkl. AirDrop. Zusätzlich zeigt die App einen QR-Code mit demselben Link.
- Empfang: Android App Links / iOS Universal Links öffnen die App mit einer Import-Vorschau. Katalog-Übungen werden per ID aufgelöst; eigene Übungen des Absenders werden per Namensabgleich zugeordnet oder neu angelegt. Ohne installierte App zeigt eine statische Webseite (GitHub Pages / eigene Domain) das Training lesbar an, dazu Download-Links.
- Ein QR-Code bleibt bis etwa 1.000 Zeichen gut scannbar; ein typischer Plan (8 Übungen) ergibt komprimiert ca. 200–400 Zeichen. Wird der Inhalt zu groß, wird eine `.overfit`-Datei (JSON) über das Share-Sheet verschickt statt eines QR-Codes.
- Bibliotheken: QR erzeugen mit ZXing core (Apache-2.0), scannen mit CameraX + ZXing. ML Kit ist proprietär und an die Play-Dienste gebunden, deshalb höchstens optional.

### Coach-Guthaben (Phase 7)
- Im KI-Zugang zusätzlich zu den eigenen Keys: „Overfit-Guthaben" als weitere `LlmProvider`-Implementierung.
- Backend (neu, schlank, Open Source): Messages-API-Proxy, Guthabenkonto, Einladungen, Kaufbestätigung. Vorschlag: Cloudflare Workers + D1/Durable Objects (atomare Abbuchung, kleiner Betrieb fast kostenlos). Das Backend speichert keine Trainingsinhalte und keine Fotos (nur durchgeleitet), sondern nur Konto-ID, Saldo und Buchungen (Modell, Tokens, Betrag).
- Abrechnung: Vor dem Request werden die maximal erwarteten Kosten reserviert. Danach wird nach `usage` (Input, Cache-Read, Output) × Listenpreis × (1 + Aufschlag) abgerechnet und der Rest freigegeben. Saldo und Kosten pro Auswertung werden in der App transparent angezeigt.
- Richtwert: Eine Coach-Auswertung mit Sonnet 5.5 (2 $ / 10 $ pro MTok, Cache-Read 0,20 $) inkl. zweier Fotos kostet ca. 6–8 ct. 1 € reicht damit für ca. 12–15 Auswertungen.
- Kauf: Pakete (z. B. 5 € / 10 €). Im Play Store und App Store ist für digitale Inhalte die Store-Abrechnung Pflicht (Play Billing / Apple IAP, 15 % im Small-Business-Programm; dafür übernimmt der Store die Umsatzsteuer). Der Guthabenwert eines Pakets ist deshalb Kaufpreis × (1 − Store-Gebühr) / (1 + Aufschlag). Beim APK über GitHub ginge ein Zahlungsanbieter direkt; dann ist die Umsatzsteuer selbst zu klären oder ein Merchant of Record zu nutzen.

### Einladen mit Startguthaben (Phase 9)
- Wer von einem bestehenden Nutzer persönlich eingeladen wird, bekommt ein Startguthaben (Start: 1 €, serverseitig konfigurierbar). Der Einladende bekommt eine Gutschrift, sobald der Neue seine erste Coach-Auswertung gemacht hat.
- Ablauf: Der Einladende öffnet „Freund einladen", die App zeigt einen QR-Code mit einem vom Server signierten Einmal-Token (rotiert alle 30 s, kurz gültig) plus Installationslink. Der Neue installiert die App, tippt „Ich wurde eingeladen" und scannt den QR-Code.
- NFC kommt später als Spielerei: Android sendet per Host Card Emulation (NDEF-Tag), Android und iPhone lesen ihn (iPhone über Core NFC). Vom iPhone aus geht NFC nicht (Kartenemulation auf iOS nur mit spezieller Apple-Berechtigung).
- Missbrauchsschutz (Pflicht, weil echte Kosten entstehen): Geräteattestierung (Play Integrity / App Attest), Bonus einmal pro Gerät (Play Integrity Device Recall bzw. DeviceCheck, beides übersteht Neuinstallation), Token einmalig und kurzlebig, Limit pro Einladendem (z. B. 10 pro Monat) und globales Monatsbudget. Startguthaben gilt nur für Coach-Anfragen, ist nicht auszahlbar und verfällt nach 6 Monaten. Geräteattestierung setzt Store-Installationen voraus: Seitlich installierte APKs bekommen kein Startguthaben.

### Open Source – Voraussetzungen
- Erledigt: Signatur über GitHub-Secrets mit neuem Schlüssel, `signing/` entfernt, Lizenz MIT (`LICENSE`, Entscheidung Fabian 3.11.; vorher Apache-2.0, kurz „alle Rechte vorbehalten“). Die Workout-Guide-Grafiken bleiben CC BY-SA 4.0, Inter bleibt OFL.
- Vor dem Veröffentlichen: Git-Historie mit `scripts/purge-old-signing-history.sh` bereinigen (alter Schlüssel und Passwort).
- Vor dem Start des Guthabenverkaufs (nicht Teil des Codes, bitte vorher fachlich prüfen lassen): Gewerbe, Umsatzsteuer, Impressum, Datenschutzerklärung (Körperfotos laufen durch den Proxy: ausdrückliche Einwilligung, keine Speicherung, Auftragsverarbeitung mit Anthropic), AGB/Widerruf für digitale Inhalte.

## KI-Chat-Verbindungen (Phase 10)

Ziel: Wer schon ein Claude-, ChatGPT- oder Gemini-Abo hat, kann dort im normalen Chat mit seinen Overfit-Daten arbeiten –
Trainings auswerten, über das Training „philosophieren“, den Fortschritt bewerten und neue Vorlagen anlegen – ohne
eigenen API-Key und ohne Guthaben. Die angelegte Vorlage öffnet man danach in der App und trainiert sie einfach.

### Plattformen (Stand Nov. 2026)
- **Claude:** Custom Connectors = Remote-MCP-Server (Streamable HTTP) mit OAuth 2.1 + PKCE; Client-Registrierung per DCR oder
  Client ID Metadata Document. Web, Desktop und Mobile; später Eintrag im Connector-Verzeichnis.
- **ChatGPT:** Apps/Connectors auf Basis von MCP (Apps SDK). Eigene Server über den Entwicklermodus (je nach Tarif, Schreib-
  Tools teils nur in Business/Enterprise), OAuth mit DCR. Später öffentliche App im Apps-Verzeichnis.
- **Gemini:** „Eigene Apps“ (Remote-MCP-URL, OAuth) in der Gemini-App, derzeit nur eingeschränkt verfügbar (privates Konto,
  USA, Englisch). **Ersatzweg überall dort, wo kein Connector geht** (Gemini in DE, ChatGPT ohne Entwicklermodus): ein Gem
  bzw. Custom GPT mit Anleitung aus der App, „Kontext kopieren“ in der App (kompakter Text: Profil, Gedächtnis, letzte
  Trainings, Fortschritt der Hauptübungen; Fotos einzeln anhängen) und Rückweg über den Teilen-Link
  `https://…/t#<payload>` (Antippen oder Zwischenablage → Import-Vorschau).

### Architektur
- **Ein** Remote-MCP-Server im vorhandenen Backend (`backend/`, Cloudflare Worker) unter `/mcp`, den alle drei Plattformen
  nutzen. OAuth-Autorisierungsserver im selben Worker (Metadaten, DCR, CIMD, PKCE S256, rotierende Refresh-Tokens, Widerruf).
- **Ausdrücklich freiwillige Synchronisation** („KI-Chat verbinden“): Die App lädt Profil, Coach-Gedächtnis, Vorlagen und
  Trainings (inkl. laufendem) hoch, Körperfotos nur mit separatem Häkchen. Gespeichert in D1 (Fotos in R2),
  AES-256-GCM-verschlüsselt mit kontoabhängigem Schlüssel, gebunden an die anonyme Geräte-Identität. Abschalten löscht
  serverseitig alles und trennt alle Chats. Keine Ende-zu-Ende-Verschlüsselung (der Server muss für den Chat lesen).
- **Anmeldung ohne Konto:** Die Plattform öffnet die Overfit-Anmeldeseite; dort tippt man einen 8-stelligen Kopplungscode ein,
  den die App anzeigt (10 min gültig, einmalig). Verbindungen sind in der App sichtbar und einzeln widerrufbar.
- **Rückweg in die App:** Der Chat legt Vorlagen (Teilen-Format v1) und Notizen/Auswertungen in einen **Eingang**. Die App
  holt ihn beim Öffnen ab und zeigt dieselbe Import-Vorschau wie beim Teilen-Link; nichts ändert sich ohne Bestätigung.
  Jede angelegte Vorlage kommt zusätzlich als Import-Link zurück (funktioniert auch ohne Sync-Abruf).
- **Tools:** Profil/Ziele/Gedächtnis, Vorlagen, Trainingsliste und -detail, laufendes Training, trainierte Übungen,
  Fortschritt je Übung (Top-Satz, e1RM, Volumen, Bestwerte, Veränderung), Katalogsuche (gültige IDs), Fotos (nur mit
  Freigabe, als Bild), Vorlage anlegen/ändern, Notiz hinterlegen. Vertrag: `docs/backend-api.md`.
- **Fünf Wege nebeneinander:** Der In-App-Coach (Phase 4) läuft weiter über eigenen Key oder Guthaben. Die Chat-Verbindung ist
  zusätzlich; Auswertungen aus dem Chat landen als Notiz am Training, nicht als In-App-Coach-Bewertung.

## Monetarisierung
Siehe [docs/monetization.md](docs/monetization.md) (Guthaben im Store und im Web, später Pro-Abo, Programme, Trainer-Version).
