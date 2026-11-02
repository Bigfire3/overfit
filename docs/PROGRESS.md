# Fortschritt

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup, Gerüst, CI, erstes APK | erledigt |
| 0b | Kotlin Multiplatform, Schwarz-Weiß-Design, DE/EN, Onboarding, eigener API-Key, iOS-Hülle | erledigt |
| 1 | Übungskatalog, eigene Übungen, Vorlagen | offen |
| 2 | Workout-Modus, Pausentimer | offen |
| 3 | Beenden-Flow, Foto, Verlauf, Backup | offen |
| 4 | KI-Coach | offen |
| 5 | Feinschliff | offen |
| 6 | Store-Release (App Store, Play Store) | offen |
| 7 | Credits-System | offen |

## Letzte Session
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
- iOS-App wurde noch nicht auf Gerät oder Simulator gestartet (erste Prüfung durch die iOS-CI im PR)
- Noch keine Datenbank (Room KMP kommt in Phase 1)
- Pausentimer-Hintergrundmechanismus (Android + iOS) folgt in Phase 2

## Bitte auf dem Handy testen
1. Neues `overfit.apk` aus Release „latest“ installieren (Update über die alte Version)
2. Onboarding: Name, Alter, Gewicht eingeben, unten „Weiter“; Tastatur öffnet sich jeweils sofort
3. KI-Screen: einen falschen Key eingeben → Fehlermeldung; dann einen echten Key (z. B. OpenRouter oder Anthropic) → „Verbunden“
4. Start zeigt „Hallo, <Name>“ und das Gewicht groß
5. Profil-Tab: Sprache auf English, Darstellung auf Hell umstellen; App schließen und neu öffnen → Einstellungen bleiben
6. „Alle Daten löschen“ zweimal antippen → zurück im Onboarding
