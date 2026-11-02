# Fortschritt

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup, Gerüst, CI, erstes APK | erledigt |
| 1 | Übungskatalog, eigene Übungen, Vorlagen | offen |
| 2 | Workout-Modus, Pausentimer | offen |
| 3 | Beenden-Flow, Foto, Verlauf, Backup | offen |
| 4 | KI-Coach | offen |
| 5 | Feinschliff | offen |

## Letzte Session
Phase 0 abgeschlossen:
- Gradle-Projekt (AGP 9.5, Kotlin 2.4.21, Gradle 9.8.2, Version Catalog), minSdk 26, target/compileSdk 37
- App-Gerüst: dunkles Theme, Navigation Compose mit 5 Tabs (Start, Verlauf, Übungen, Fotos, Einstellungen),
  Unterseiten Coach-Gedächtnis und Über (mit Namensnennungen), leere Zustände, App-Icon (Hantel)
- Keystore unter `signing/`, signiertes Release-APK mit R8 (~1 MB)
- GitHub-Actions-Workflow: Tests + Release-APK bei Push auf main/claude/**, Pre-Release „latest" mit `overfit.apk`,
  versionierte Releases bei Tags `v*`
- Tests: Unit-Test Navigation + Robolectric-Smoke-Test (klickt alle Screens durch) – lokal grün
- README (Installation/Updates), docs/DECISIONS.md

Offen / Hinweise:
- Noch keine Datenbank (Room kommt in Phase 1 mit den ersten Entitäten und exportiertem Schema)
- Entscheidung zum Pausentimer-Hintergrundmechanismus folgt in Phase 2

## Bitte auf dem Handy testen
1. Release „latest" öffnen (siehe README), `overfit.apk` laden und installieren
2. App startet im dunklen Theme, Icon „Hantel" im Launcher
3. Alle 5 Tabs antippen; in Einstellungen „Coach-Gedächtnis" und „Über Overfit" öffnen und mit Zurück verlassen
4. „Über": Version zeigt `0.1.<Build-Nummer>`
5. Später ein zweites APK aus „latest" installieren → muss als Update ohne Datenverlust durchgehen
