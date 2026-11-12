# Product

<!-- impeccable:product-schema 1 -->

> Abgeleitet aus SPEC.md, docs/DECISIONS.md und docs/PROGRESS.md (12.11.2026, UI/UX-Feinschliff). Laut CLAUDE.md wird der
> Owner nur bei sehr wichtigen Entscheidungen gefragt; deshalb kein Interview. Was nicht ausdrücklich in SPEC/DECISIONS
> steht, ist mit *(Annahme)* markiert. Bei Widerspruch gelten SPEC.md und DECISIONS.md.

## Platform

adaptive

Eine Codebasis (Kotlin Multiplatform, Compose Multiplatform in `:shared`) für Android (ab API 26) und iOS (ab 16, nur
iPhone). Die Gestaltung ist bewusst **ein eigenes, plattformneutrales Designsystem** (schwarz-weiß, nur Compose
Foundation, kein Material, keine iOS-Systemkomponenten). Plattformkonventionen gelten für Gesten, Insets, Zurück,
Tastatur, Haptik und Bedienungshilfen, nicht für die Optik.

## Users

- **Primär:** Menschen, die regelmäßig Krafttraining oder Calisthenics machen und ihre Sätze im Studio oder zu Hause am
  Handy loggen. Sie stehen zwischen zwei Sätzen, sind außer Atem, haben verschwitzte Finger, halten das Handy oft in einer
  Hand und schauen nur kurz drauf. Ihr Job: den nächsten Satz schnell eintragen, die Pause einhalten, nach dem Training
  ehrlich sehen, ob sie besser werden.
- **Owner/Erstnutzer:** Fabian (Entwickler und tägliche Testperson, testet jedes APK am Handy).
- Deutsch und Englisch (Gerätesprache, umschaltbar). Einheit kg oder lb.

## Product Purpose

Overfit ist eine extrem minimalistische Trainings-App: Workouts (Vorlagen) anlegen, im Workout-Modus Sätze mit großen
Zahlenfeldern abhaken, Pausentimer läuft automatisch, nach dem Training fasst ein KI-Coach zusammen, merkt sich den
Verlauf und schlägt den nächsten Plan vor. Erfolg heißt: Ein Training lässt sich ohne Nachdenken mit möglichst wenigen
Tipps loggen, und der Coach gibt ehrliches, nachvollziehbares Feedback.

## Positioning

- Open Source (MIT), keine Accounts, keine Werbung, kein Tracking, keine Analytics.
- KI nur auf ausdrücklichen Tipp: eigener API-Key (Anfragen direkt vom Gerät, null Anfragen an das eigene Backend),
  optional Guthaben mit voller Kostentransparenz (Preis vor jeder Anfrage), oder Verbindung zum eigenen Chat-Abo.
- Daten bleiben auf dem Gerät (Room-Datenbank, Fotos app-privat), Backup als Datei.

## Operating Context

- Im Studio zwischen Sätzen: einhändig, kurze Blicke, oft wechselndes Licht. Die App ist immer dunkel.
- Laufendes Training überlebt App-Kill und Neustart; Pausentimer mit Benachrichtigung im Hintergrund.
- Nach dem Training: Zusammenfassung, Fotos des Tages, optional Auswertung durch den Coach.
- Kalender als Tagebuch (Trainings, Sauna, Körpergewicht, Fotos).
- Trainings teilen per Link, Share-Sheet und QR.

## Capabilities and Constraints

- Übungskatalog: 302 Übungen aus Workout Guide (CC BY-SA 4.0, Namensnennung im Über-Screen), eigene Übungen mit
  Initialen-Kachel, „Mit KI ausfüllen“.
- Workout-Modus: Satzzeilen je Erfassungsart (Gewicht×Wdh., Wdh., Zeit, Gewicht×Zeit), „Letztes Mal“, Alternativen (A/B),
  Satz wischen = löschen, Übungen umsortieren/ersetzen/auslassen.
- Pausentimer nie Vollbild: schmale Leiste oder größere Leiste, Liste bleibt bedienbar.
- Nie destruktive Migrationen; Nutzerdaten dürfen nie verloren gehen.
- Alle Texte in `Strings.kt` (DE + EN), fehlende Übersetzung = Compile-Fehler.
- Bewusst nicht: Login, Cloud-Sync, Social-Feed, Gamification, Übungsanleitungen, Videos, Popups für Bestätigungen.
- Offen: Store-Release (Phase 6), echter Store-Kauf von Guthaben.

## Brand Commitments

- Wortmarke **„Overfit“** mit großem O; Claim „personal training for everyone“ (in beiden Sprachen Englisch).
- App-Icon „of“ in Inter ExtraBold, weiß auf Schwarz (`design/icon/`).
- Stil angelehnt an Trade Republic bzw. eine Banking-App-Vorlage (Inspiration, keine Kopie): schwarz-weiß, Typografie und
  Weißraum tragen das Design, ein heller Hauptknopf pro Screen. Details in DESIGN.md.
- Ton: knapp, sachlich, duzt auf Deutsch, ehrliche Begriffe („Guthaben“, echte Modellnamen, echte Preise).

## Evidence on Hand

- Screenshots je Feature unter `docs/screenshots/` (Robolectric, 411 dp und 360 dp).
- Übungsgrafiken: Workout Guide (gebündelt, offline). Keine Nutzerstimmen, Testimonials oder Kennzahlen vorhanden; keine
  erfinden.

## Product Principles

1. **Wenig tippen.** Die häufigste Aufgabe (Satz loggen, Workout starten) braucht einen Tipp; alles andere tritt zurück.
2. **Ehrlich und transparent.** Keine KI-Anfrage ohne Tipp, Preis vorher sichtbar, keine geschönten Begriffe.
3. **Minimal statt vollständig.** Was nicht gebraucht wird, fliegt raus (Popups, Erklärtexte, Rahmen, Farben).
4. **Daten gehören dem Nutzer.** Lokal, exportierbar, nie verloren.
5. **Eine Sprache für alles.** Gleiche Aufgabe, gleiche Komponente, gleiche Geste, auf Android wie auf iOS.

## Accessibility & Inclusion

- Große Touch-Flächen (Ziel ≥ 48 dp), TalkBack/VoiceOver-Labels für Icon-Knöpfe, Rollen und Zustände (Switch, Checkbox,
  Tab, „zum Bestätigen erneut tippen“).
- Erledigt wird über Kontrast und Häkchen markiert, nicht nur über Farbe.
- Text-Kontrast: Fließ- und Beschriftungstext ≥ 4,5 : 1, Platzhalter und große Zahlen ≥ 3 : 1 *(Annahme: WCAG AA als Ziel;
  so in `ContrastTest` abgesichert)*.
- Systemschriftgröße wird respektiert (sp); Layouts auf 360 dp und mit 1,3-facher Schrift geprüft.
- Bewegung: Compose überspringt Animationen bei System-Animationsskala 0; keine Animation ist die einzige Rückmeldung.
