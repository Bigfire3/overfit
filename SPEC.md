# Overfit – Spezifikation

Native Android-App für mich persönlich (ein Nutzer, keine Accounts, kein eigenes Backend). Ich logge Krafttraining und Calisthenics. Im Workout-Modus läuft ein Pausentimer. Nach dem Training bewertet ein KI-Coach (Claude API) das Training inkl. optionalem Körperfoto, pflegt ein Langzeitgedächtnis und passt den Plan fürs nächste Mal an.
- App-Name: Overfit, applicationId: app.overfit
- UI-Sprache Deutsch, dunkles Theme, große Touch-Flächen. Stil wie typische Gym-Tracker: einklappbare Übungskarten mit abhakbaren Satzzeilen. Einheit nur kg.

## Tech-Stack
- Kotlin, Jetpack Compose (Material 3), Navigation Compose, ViewModel + StateFlow, Hilt oder schlanke manuelle DI, Room mit exportiertem Schema, kotlinx.serialization, OkHttp oder Ktor, Coil (inkl. SVG-Unterstützung), WorkManager.
- minSdk 26, aktuelles targetSdk, Gradle Kotlin DSL + Version Catalog.
- Niemals destruktive Room-Migrationen: meine Daten dürfen nie verloren gehen. Jede Schemaänderung bekommt eine Migration + Migrationstest.

## Build & Verteilung
- Das Repo enthält anfangs nur die Planungsdateien. Du richtest alles selbst ein: Gradle-Projekt, Keystore, GitHub-Actions-Workflow, README. Ich lege nichts an, auch keine Secrets.
- Signatur: Keystore mit keytool erzeugen und unter signing/ committen (privates Repo, rein private App – bewusster Kompromiss), Passwörter in der Gradle-Signing-Config. Jedes CI-APK muss dieselbe Signatur haben, damit Updates ohne Neuinstallation und ohne Datenverlust gehen.
- versionCode automatisch aus der CI-Run-Nummer, damit jedes neue APK als Update installierbar ist.
- GitHub Actions: Build bei jedem Push auf main und auf claude/**-Branches. Signiertes Release-APK bauen, dann ein Pre-Release mit festem Tag „latest" aktualisieren, mit dem APK als Asset (Artefakte kommen nur als ZIP, das Release kann ich direkt am Handy laden). Bei Tags v* zusätzlich ein versioniertes Release.
- Versuche, das Android SDK in deiner Umgebung zu installieren und selbst zu bauen und zu testen. Klappt das nicht, arbeite besonders sorgfältig, prüfe den CI-Lauf, soweit du Zugriff hast, und sag mir sonst, was ich in der Actions-Ansicht kontrollieren soll.
- README: wie ich das APK am Handy lade und installiere (im Browser bei GitHub eingeloggt sein, Release „latest", einmalig „Unbekannte Apps installieren" erlauben) und wie Updates funktionieren.

## Datenmodell (Vorschlag, darfst du verbessern)
- Exercise: id, name, source (WORKOUT_GUIDE | FREE_DB | CUSTOM), sourceId?, imageRef, primaryMuscles, equipment, trackingType, defaultRestSec
- trackingType: WEIGHT_REPS (Gewicht darf negativ sein = Unterstützung, z. B. Band; positiv bei Körpergewichtsübungen = Zusatzgewicht), REPS, TIME (Sekunden, z. B. Handstand-Hold), WEIGHT_TIME
- WorkoutTemplate + TemplateExercise (Reihenfolge, geplante Sätze, Pausenzeit)
- WorkoutSession: Start, Ende, templateId?, Notiz, Gefühl 1–5 (optional), photoPath?, aiStatus (NONE, QUEUED, SENT, DONE, FAILED)
- SessionExercise: Reihenfolge, Herkunft (geplant / hinzugefügt / ersetzt für X)
- SetEntry: geplant vs. tatsächlich (weightKg, reps, durationSec), completed, completedAt, optional RPE (per Einstellung einblendbar, Standard aus)
- CoachMemory: aktueller Text + Versionshistorie
- AiReview: sessionId, Rohantwort, Bewertung, Tipps, Planvorschlag, Status (offen / übernommen / abgelehnt)
- UserProfile: Ziele, Erfahrung, Körpergewicht, Einschränkungen, Freitext

## Übungsbilder & Katalog
- Hauptquelle: Workout Guide (github.com/bryllim/workout-guide, npm @bryllim/workout-guide): 302 Übungen, je 3 schematische SVG-Frames. Nur Frame 1 als Standbild zeigen, keine Animation. SVG beim Rendern in der Theme-Farbe einfärben.
- Ergänzung für fehlende Übungen: free-exercise-db (github.com/yuhonas/free-exercise-db, Unlicense), erstes Bild.
- Import-Skript im Repo: beide Kataloge an feste Versionen bzw. Commit-SHAs gepinnt laden, per Namen abgleichen, Dubletten zusammenführen (Workout Guide hat Vorrang), Ergebnis als exercises.json ins App-Asset. Workout-Guide-SVGs offline mitbündeln. free-exercise-db-Bilder lazy laden mit Coil-Disk-Cache, Einstellung „Alle Bilder offline laden".
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
- Pausenende: Vibration immer, Ton optional. Muss auch bei gesperrtem Bildschirm und App im Hintergrund zuverlässig auslösen. Laufende Benachrichtigung mit Countdown und Aktionen (+15 s, Skip). Wähle den robusten Weg für Android 14+ (Foreground-Service-Typ bzw. exakte Alarme) und begründe ihn in DECISIONS.md.

## Training beenden & KI-Coach
Ablauf:
1. Zusammenfassung: Dauer, Sätze, Volumen, Abweichungen vom Plan.
2. Optional Foto aufnehmen (Kamera): vorwiegend Körper-Fortschritt, zugleich Foto-Tagebuch. Optional Notiz + Gefühl.
3. „An Coach senden" oder „Später". Ohne Netz → Warteschlange (WorkManager), Versand sobald online.

Request (Claude Messages API, direkter HTTP-Call, Modell in den Einstellungen wählbar, Default claude-sonnet-5-5):
- System-Prompt: Coach-Rolle und Regeln (als editierbarer Text in der App): realistische Steigerungen, Stagnation erkennen und gegensteuern, Calisthenics-Progressionen berücksichtigen, Antworten auf Deutsch. Dazu eine kompakte Liste aller verfügbaren Übungs-IDs. Prompt-Caching für diesen statischen Teil nutzen.
- Kontext: Profil, Coach-Gedächtnis, letzte 3–5 Einheiten derselben Vorlage + letzte Einheit insgesamt, das heutige Training vollständig (geplant vs. gemacht, Ersetzungen, Pausen, Dauer, Notizen).
- Foto (falls vorhanden): auf ca. 1500 px lange Kante verkleinert, JPEG. Dazu das jüngste Fortschrittsfoto, das mindestens 4 Wochen alt ist, zum Vergleich.

Antwort per Tool-Use erzwingen (ein Tool mit JSON-Schema, tool_choice auf dieses Tool):
- rating (1–10), summary, positives[], improvements[], tips[]
- photo_feedback (nur mit Foto): sachlich und konstruktiv, Fokus auf sichtbare Veränderungen gegenüber dem Vergleichsfoto
- next_session: Übungen nur mit IDs aus der Liste (neue Übungen als markierter Vorschlag mit Begründung), Sätze mit Zielwerten, Pausenzeiten, kurze Begründung je Änderung
- memory: komplett überarbeitete Fassung des Coach-Gedächtnisses (max. ca. 1500 Wörter): Ziele, Fortschritt je Hauptübung, Plateaus, Vorlieben, Beschwerden, was funktioniert

Ergebnis-Screen: Bewertung, Tipps, Foto-Feedback, Plan-Diff („Bankdrücken: 90 → 92,5 kg", „Neu: …") mit Alle annehmen / Ablehnen / einzeln. Ein übernommener Plan aktualisiert die Vorlage.
Robustheit: Schema validieren, bei ungültiger Antwort einmal neu versuchen, Rohantwort speichern, Fehler verständlich anzeigen.
API-Key: Eingabe in den Einstellungen, verschlüsselt via Android Keystore, nie in Code, Repo oder Logs. Button „Verbindung testen".

## Weitere Screens
- Start: Vorlagen, „Training starten", letzte Coach-Tipps
- Verlauf: vergangene Trainings, Detail mit Sätzen + Coach-Bewertung
- Foto-Tagebuch: Zeitleiste/Grid, zwei Fotos nebeneinander vergleichen. Fotos nur im app-privaten Speicher, nicht in der Galerie
- Coach-Gedächtnis: lesen, bearbeiten, Versionen
- Einstellungen: API-Key, Modell, Standard-Pause, Ton, Bildschirm an, RPE an/aus, Bilder offline laden, Backup
- Backup: Export/Import als JSON, optional ZIP mit Fotos, über die Dateiauswahl
- Über: App-Version, Lizenzen und Namensnennungen

## Bewusst NICHT
Accounts, Cloud-Sync, Social, Gamification, Werbung, Übungsanleitungen, Videos, Animationen.

## Phasen
0. Projekt-Setup: Gradle-Projekt, App-Gerüst (Theme, Navigation, leere Screens), Keystore, CI mit Release „latest", README, docs/DECISIONS.md → erstes installierbares APK
1. Übungskatalog (Import-Skript, Suche, Filter) + eigene Übungen, Vorlagen anlegen/bearbeiten
2. Workout-Modus inkl. Live-Änderungen und Persistenz, Pausentimer (minimiert/erweitert, Hintergrund, Benachrichtigung)
3. Beenden-Flow, Foto, Verlauf, Foto-Tagebuch, Backup
4. KI-Coach: Request, Tool-Schema, Gedächtnis, Plan-Diff, Warteschlange
5. Feinschliff: leere Zustände, Fehlerfälle, Performance, README vervollständigen
