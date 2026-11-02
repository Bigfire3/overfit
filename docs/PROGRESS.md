# Fortschritt

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup, Gerüst, CI, erstes APK | erledigt |
| 1 | Übungskatalog, eigene Übungen, Vorlagen | erledigt |
| 2 | Workout-Modus, Pausentimer | offen |
| 3 | Beenden-Flow, Foto, Verlauf, Backup | offen |
| 4 | KI-Coach | offen |
| 5 | Feinschliff | offen |

## Letzte Session
Phase 1 abgeschlossen:
- Import-Skript `tools/catalog/import_catalog.py` (Workout Guide + free-exercise-db, an Commits gepinnt) →
  `assets/catalog/exercises.json` mit 1098 Übungen + 302 Workout-Guide-SVGs (offline, eingefärbt)
- Room-Datenbank v1 mit exportiertem Schema (`app/schemas`): Übungen, Vorlagen, Vorlagen-Übungen, geplante Sätze;
  Katalog wird beim Start eingespielt, Nutzer-Einstellungen bleiben bei Updates erhalten
- Tab „Übungen“: Katalog mit unscharfer Suche (auch deutsche Begriffe), Filter Muskelgruppe/Equipment/Eigene,
  Detail-Sheet (Bild, Muskeln, Tracking-Typ + Pausenzeit änderbar), eigene Übungen anlegen/bearbeiten/löschen
  (ohne Bild → Initialen-Kachel)
- Vorlagen: Liste (Tab „Übungen“ → „Vorlagen“ und Startbildschirm), Editor mit einklappbaren Übungskarten, Sätzen mit
  Zielwerten je Tracking-Typ, Pause und Notiz je Übung, hinzufügen (Mehrfachauswahl), ersetzen, entfernen, umsortieren
- Einstellungen: „Alle Bilder offline laden“ (WorkManager, mit Fortschritt)
- Tests: 41 Unit-/Robolectric-Tests (Suche inkl. echtem Katalog, Zahlenformat, Seeding, Repositories, Schema/Migration,
  Vorlagen-Entwurf, UI-Durchlauf Katalog-Suche → Vorlage anlegen) – lokal grün, Release-APK lokal gebaut (4,7 MB)

Offen / Hinweise:
- Drag & Drop zum Umsortieren kommt mit Phase 2 (jetzt: Kartenmenü „Nach oben/unten“)
- „Training starten“ ist weiterhin deaktiviert (Phase 2); Tippen auf eine Vorlage öffnet vorerst den Editor
- Globale Standard-Pause in den Einstellungen folgt in Phase 2 (leere Pause = Standard)

## Bitte auf dem Handy testen
1. Neues `overfit.apk` aus „latest“ als Update installieren (ohne Deinstallation)
2. Tab „Übungen“ → Katalog lädt beim ersten Start (kurz), Liste mit Bildern: blaue Strichgrafiken (Workout Guide)
   und Fotos (free-exercise-db, nur mit Internet)
3. Suchen: „Klimmzug“, „Bankdrücken“, „Kniebeuge“, „pushup“, „bensh press“ (Tippfehler), „Brust“; Filter
   Muskelgruppe/Equipment/Eigene ausprobieren
4. Übung antippen → Detail; bei z. B. Pull-up Erfassung auf „Gewicht × Wdh.“ stellen, Pause 120 → Speichern
5. „Eigene Übung“ anlegen (z. B. „Handstand“, Zeit) → erscheint mit Initialen-Kachel „HA“; bearbeiten, löschen
6. „Vorlagen“ → „Neue Vorlage“: Name, „Übung hinzufügen“ (mehrere auswählen), Sätze mit kg/Wdh. füllen
   (Komma, „±“ für Band-Unterstützung), Satz hinzufügen/entfernen, Übung ersetzen/verschieben/entfernen,
   Häkchen speichern → Vorlage erscheint in der Liste und auf „Start“; erneut öffnen → Werte sind da
7. Ungespeicherte Änderungen + Zurück → Rückfrage „Änderungen verwerfen?“
8. Einstellungen → „Alle Bilder offline laden“ (im WLAN, ~50 MB) → Fortschritt, danach im Flugmodus Fotos weiter sichtbar
9. App komplett schließen und neu öffnen → Vorlagen und eigene Übungen sind noch da
