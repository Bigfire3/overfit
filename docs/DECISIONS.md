# Entscheidungen

Kurzprotokoll von Annahmen und Architekturentscheidungen. Neueste unten.

## Phase 0 – Setup

- **Toolchain:** AGP 9.5.0 mit eingebautem Kotlin-Support (kein separates `kotlin-android`-Plugin), Kotlin 2.4.21
  (über das Compose-Compiler-Plugin auf den Classpath gehoben), Gradle 9.8.2, JDK 21 zum Bauen, Bytecode Java 17.
  Version Catalog unter `gradle/libs.versions.toml`.
- **SDK-Level:** minSdk 26, compileSdk und targetSdk 37 (Android 17, aktuell stabil).
- **DI:** Schlanke manuelle DI (`AppContainer` in `OverfitApp`) statt Hilt. Ein Nutzer, überschaubare Abhängigkeiten,
  schnellere Builds, kein zusätzlicher Annotation-Processor.
- **Navigation:** Navigation Compose mit typsicheren `@Serializable`-Routen. Untere Leiste mit fünf Tabs
  (Start, Verlauf, Übungen, Fotos, Einstellungen). Coach-Gedächtnis und „Über" sind Unterseiten der Einstellungen.
  Vorlagen werden im Tab „Übungen" verwaltet (Phase 1).
- **Theme:** Immer dunkel, keine Dynamic Colors (einheitlicher Gym-Look). Akzent Blau `#4C8DFF`, erledigte Sätze Grün `#3DDC84`.
- **Icons:** `material-icons-core` statt `material-icons-extended` (wird nicht mehr gepflegt und ist sehr groß).
  Fehlende Symbole kommen später als eigene Vektor-Drawables.
- **Signatur:** Keystore `signing/overfit-release.jks` (PKCS12, RSA 4096, 100 Jahre gültig, Alias `overfit`),
  Passwörter direkt in `app/build.gradle.kts`. Bewusster Kompromiss laut SPEC (privates Repo, private App).
  Zertifikat SHA-256: `90:75:31:57:8D:C2:5A:3C:85:27:BD:9D:24:EA:2E:7C:89:33:95:A2:51:5E:69:12:C9:CD:54:EE:20:B6:F2:77`.
  **Dieser Keystore darf nie ersetzt werden**, sonst sind Updates ohne Deinstallation (= Datenverlust) unmöglich.
- **Versionierung:** `versionCode = GITHUB_RUN_NUMBER` (lokal 1), `versionName = 0.1.<run>`. Die Run-Nummer zählt pro
  Workflow über alle Branches hoch, daher ist jedes neue CI-APK ein gültiges Update.
- **Release „latest":** Jeder erfolgreiche Build auf `main` oder `claude/**` verschiebt den Tag `latest` per Force-Push
  auf seinen Commit und ersetzt das Asset `overfit.apk` (fester Dateiname → feste Download-URL). Der zuletzt gebaute
  Push gewinnt, egal von welchem Branch. Release-Updates laufen serialisiert (Concurrency-Gruppe `release-latest`).
  Tags `v*` erzeugen zusätzlich ein versioniertes Release.
- **Debug-Build:** `applicationIdSuffix = ".debug"`, damit ein Debug-Build nie die echte App (und ihre Daten) überschreibt.
  CI baut nur Release.
- **R8:** Minify + Resource-Shrinking im Release an (APK ~1 MB statt ~10 MB). `mapping.txt` liegt im CI-Artefakt.
- **Auto-Backup:** `android:allowBackup="false"` – kein Cloud-Sync gewünscht, Datensicherung über eigenen
  JSON/ZIP-Export (Phase 3).
- **Tests ohne Emulator:** In der Claude-Umgebung gibt es kein KVM, daher UI-Smoke-Tests mit Robolectric
  (Compose UI Test, SDK 36 in Robolectric). JVM-Flags für JDK 21 in `testOptions` gesetzt.
- **Lokale Build-Umgebung (nicht im Repo):** Maven Central antwortet hinter dem Proxy der Claude-Umgebung mit 429.
  Ein Init-Skript in `~/.gradle/init.d/` schaltet dort lokal den Google-Mirror von Maven Central vor. CI nutzt die normalen Repos.
- **Pausentimer im Hintergrund:** Entscheidung und Begründung folgen in Phase 2.

## Phase 1 – Übungskatalog, eigene Übungen, Vorlagen

- **Bildquelle Workout Guide:** Das npm-Paket `@bryllim/workout-guide@1.0.0` enthält nur PNGs. Die SVG-Frames gibt es
  erst im GitHub-Repo, daher ist das Repo an Commit `aac5992` gepinnt (Workout-Guide-Version 1.0.0 + Vektorisierung).
  free-exercise-db ist an Commit `f00c92c` gepinnt. Beide SHAs stehen oben in `tools/catalog/import_catalog.py`.
- **Import-Skript:** Python 3 ohne Abhängigkeiten. Abgleich per normalisiertem Namen (Kleinschreibung, nur Buchstaben
  und Ziffern, einfacher Plural). Zusätzlich wird ein führendes Equipment-Wort ignoriert („Barbell Deadlift“ =
  „Deadlift“), aber nur bei gleichem Equipment, sonst würden z. B. „Barbell Curl“ und „Cable Curl“ verschmelzen.
  Dazu eine kleine manuelle Liste eindeutiger Dubletten. Ergebnis: 1098 Übungen (302 Workout Guide, 796
  free-exercise-db, 79 Dubletten zusammengeführt). Die IDs der zusammengeführten Dubletten stehen im Feld `merged`.
- **Frame-1-SVGs** liegen unverändert unter `assets/catalog/wg/` (Lizenz) und werden mit Coil-SVG gerendert, eingefärbt
  über `ColorFilter.tint` in der Primärfarbe. APK wächst dadurch auf ca. 4,7 MB.
- **Übungsnamen bleiben englisch** (so heißen sie in beiden Katalogen). Die Suche bildet gängige deutsche Begriffe
  auf die englischen Namen ab (Klimmzug → Pull-up, Bankdrücken → Bench Press …). Muskelgruppen und Equipment sind
  auf deutsche Bezeichnungen vereinheitlicht und ebenfalls durchsuchbar.
- **Suche:** komplett im Speicher (~1100 Einträge): UND über alle Wörter, Präfix, zusammengeschrieben („pushup“),
  Tippfehler (Damerau-Levenshtein ≤ 1, ab 8 Zeichen ≤ 2). Bei gleicher Trefferqualität kommen eigene Übungen zuerst,
  dann Workout Guide (bessere Bilder), dann kürzere Namen („Pull-up“ vor „Assisted Pull-up“).
- **Tracking-Typ aus dem Katalog:** Workout Guide `weight_reps`/`assisted_bodyweight` → Gewicht × Wdh.,
  `bodyweight_reps` → Wdh., `duration`/`distance_duration` → Zeit (Distanz wird nicht erfasst). free-exercise-db:
  Dehnen/Cardio/statisch ohne Gerät → Zeit, Körpergewicht → Wdh., sonst Gewicht × Wdh. **Tracking-Typ und Pausenzeit
  sind auch bei Katalogübungen änderbar** (z. B. Klimmzug mit Zusatzgewicht oder Band), und ein Katalog-Update
  überschreibt das nicht.
- **Datenmodell (Room v1):** `exercise` mit stabiler Text-ID (`wg:<slug>`, `fedb:<id>`, `custom:<uuid>`), damit Vorlagen,
  Backups und später der KI-Coach dieselben, lesbaren IDs nutzen. `workout_template` → `template_exercise`
  (Reihenfolge, Pause, Notiz) → `template_set` (Zielwerte je Satz: kg, Wdh., Sekunden). Pausenzeit `null` heißt
  jeweils „von der Übung bzw. globaler Standard“. Fremdschlüssel: Vorlage → Kinder CASCADE, Übung RESTRICT.
- **Katalog-Seeding:** beim App-Start im Hintergrund, nur wenn sich die Katalogversion (Hash über den Inhalt) ändert.
  Neue Übungen werden eingefügt, bei bestehenden nur Name, Bild, Muskeln und Equipment aktualisiert. Aus dem Katalog
  entfernte Übungen werden nie gelöscht.
- **Eigene Übungen löschen:** Ist die Übung noch in einer Vorlage, wird sie nur archiviert (ausgeblendet), sonst gelöscht.
  So geht nie etwas verloren (später auch für den Verlauf wichtig).
- **Vorlagen-Editor:** Bearbeitung im Speicher, „Speichern“ schreibt alles in einer Transaktion (Übungen und Sätze
  werden dabei neu geschrieben). Beim Verlassen mit Änderungen kommt eine Rückfrage. Umsortieren in Phase 1 über das
  Kartenmenü (nach oben/unten). Drag & Drop kommt in Phase 2 mit dem Workout-Modus und wird dann auch hier eingebaut.
- **Gewichtseingabe:** deutsches Format (Komma, Punkt geht auch), Rundung auf 0,01 kg. Ein „±“-Knopf im Feld setzt
  negative Werte, weil viele Zahlentastaturen kein Minus haben.
- **Bilder der free-exercise-db:** lazy per Coil (Disk-Cache 256 MB). „Alle Bilder offline laden“ ist ein
  WorkManager-Job, der die Fotos dauerhaft nach `filesDir/exercise-images/` lädt (Cache-Bereinigung kann sie nicht
  löschen). Vorhandene Dateien werden übersprungen, ein erneuter Start lädt nur den Rest.
- **WorkManager:** Initialisierung bei Bedarf über `Configuration.Provider` in `OverfitApp`, der App-Startup-Initializer
  ist im Manifest entfernt. Robuster und in Robolectric-Tests nutzbar.
- **Room-Schemata in Tests:** AGP 9 übernimmt Assets des `test`-Source-Sets nicht in Unit-Tests. Daher hängt
  `app/schemas` an den **debug**-Assets (nur Debug-APK, nicht Release), damit `MigrationTestHelper` sie findet.
- **CI-Branches:** Claude-Sessions laufen hier auf Branches `ccr-*`, deshalb baut der Workflow zusätzlich darauf.
