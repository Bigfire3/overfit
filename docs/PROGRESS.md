# Fortschritt

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup, Gerüst, CI, erstes APK | erledigt |
| 0b | Kotlin Multiplatform, Schwarz-Weiß-Design, DE/EN, Onboarding, eigener API-Key, iOS-Hülle | erledigt |
| 1 | Übungskatalog, eigene Übungen, Vorlagen | erledigt |
| 2 | Workout-Modus, Pausentimer | offen |
| 3 | Beenden-Flow, Foto, Verlauf, Backup | offen |
| 4 | KI-Coach | offen |
| 5 | Feinschliff | offen |
| 6 | Store-Release (App Store, Play Store) | offen |
| 7 | Credits-System | offen |

## Letzte Session
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

Offen / Hinweise:
- Drag & Drop zum Umsortieren kommt mit Phase 2 (jetzt: „Nach oben/unten“)
- „Training starten“ bleibt deaktiviert (Phase 2); Tippen auf eine Vorlage öffnet den Editor
- Globale Standard-Pause in den Einstellungen folgt in Phase 2 (leere Pause = Standard)
- iOS: Linken und Starten prüft weiterhin nur die iOS-CI

## Bitte auf dem Handy testen
1. Neues `overfit.apk` aus Release „latest“ installieren
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
