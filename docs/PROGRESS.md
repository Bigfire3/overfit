# Fortschritt

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup, Gerüst, CI, erstes APK | erledigt |
| 0b | Kotlin Multiplatform, Schwarz-Weiß-Design, DE/EN, Onboarding, eigener API-Key, iOS-Hülle | erledigt |
| 1 | Übungskatalog, eigene Übungen, Vorlagen | erledigt |
| 2 | Workout-Modus, Pausentimer | erledigt |
| 3 | Beenden-Flow, Foto, Verlauf, Backup | offen |
| 4 | KI-Coach | offen |
| 5 | Feinschliff | offen |
| 6 | Store-Release (App Store, Play Store) | offen |
| 7 | Credits-System (Coach-Guthaben) | offen |
| 8 | Trainings teilen (Link, Share-Sheet, QR, Import) | offen |
| 9 | Einladen per QR mit Startguthaben (NFC später als Spielerei) | offen |

## Letzte Session
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

## Bitte auf dem Handy testen
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

