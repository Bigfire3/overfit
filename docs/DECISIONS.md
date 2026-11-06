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
- **Theme:** ~~Immer dunkel, Akzent Blau, erledigte Sätze Grün~~ – ersetzt in Phase 0b (schwarz-weiß, siehe unten).
- **Icons:** ~~`material-icons-core`~~ – ersetzt in Phase 0b durch eigene Linien-Icons.
- **Signatur (einmalig ersetzt, siehe „Signatur über Secrets“ unten):** ~~Keystore `signing/overfit-release.jks` (PKCS12, RSA 4096, 100 Jahre gültig, Alias `overfit`),
  Passwörter direkt in `app/build.gradle.kts`. Bewusster Kompromiss laut SPEC (privates Repo, private App).
  Zertifikat SHA-256: `90:75:31:57:8D:C2:5A:3C:85:27:BD:9D:24:EA:2E:7C:89:33:95:A2:51:5E:69:12:C9:CD:54:EE:20:B6:F2:77`.
  Dieser Keystore darf nie ersetzt werden~~ – die Regel gilt jetzt für den neuen Secrets-Schlüssel.
- **Versionierung:** `versionCode = GITHUB_RUN_NUMBER` (lokal 1), `versionName = 0.1.<run>`. Die Run-Nummer zählt pro
  Workflow über alle Branches hoch, daher ist jedes neue CI-APK ein gültiges Update.
- **Release „latest":** Nur ein erfolgreicher Build auf `main` (oder einem Tag `v*`) verschiebt den Tag `latest` per
  Force-Push auf seinen Commit und ersetzt das Asset `overfit.apk` (fester Dateiname → feste Download-URL).
  Arbeits-Branches (`claude/**`, `ccr-*`) bauen und testen nur und legen das APK als Actions-Artefakt ab. Grund:
  Parallele Sessions haben „latest" gegenseitig mit Versuchsständen überschrieben. Release-Updates laufen serialisiert
  (Concurrency-Gruppe `release-latest`).
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

## Phase 0b – Kotlin Multiplatform, Designsystem, Onboarding

Ausgelöst durch die Entscheidung des Owners (2026-11-02): **öffentliche App für Android und iOS**, Basis **Kotlin Multiplatform**, Design **schwarz-weiß**. Ein parallel entstandener Expo-Prototyp (PR #15, erste Fassung) wurde verworfen; Konzept und Texte daraus sind hier übernommen.

- **Modulstruktur:** `:shared` (KMP: `android` + `iosArm64` + `iosSimulatorArm64`) enthält UI und Logik. `:app` bleibt die
  Android-Application (applicationId, Signatur, versionCode, CI-Release unverändert → bestehende Installationen bekommen
  ein normales Update). AGP 9 erlaubt KMP und `com.android.application` nicht im selben Modul, daher das Android-KMP-
  Library-Plugin (`com.android.kotlin.multiplatform.library`) in `:shared`.
- **Versionen:** Compose Multiplatform 1.12.2, JetBrains Navigation 2.10.0 / Lifecycle 2.11.0, Ktor 3.6.1,
  kotlinx-datetime 0.8.0, Coroutines 1.11.0.
- **UI ohne Material:** Nur Compose Foundation plus eigene Komponenten (`ui/components`). Material würde das
  Schwarz-Weiß-Design ständig überschreiben (Ripple, Farben, Formen) und bringt auf iOS ein fremdes Look & Feel.
- **Design:** Schwarz, Weiß, wenige Grautöne, keine Akzentfarbe. Typo Inter (OFL, gebündelt über Compose Resources),
  Stufen hero/title/body/label/caption. Dunkel als Standard, Hell/System wählbar (überholt: seit 4.11. nur Dunkelmodus, siehe „Nur Dunkelmodus“). Erledigte Sätze später über Kontrast +
  Häkchen statt Grün. Launcher-Icon: weiße Hantel auf Schwarz.
- **Untere Leiste:** 5 Tabs wie in Phase 0, aber der Tab „Einstellungen“ heißt „Profil“, weil „Einstellungen“ bei
  fünf Tabs umbricht und der Screen Profil und Einstellungen enthält.
- **Sprachen:** Texte als Kotlin-Objekte (`i18n/Strings.kt`) statt `strings.xml`/Compose-String-Resources: Sprache ist in
  der App umschaltbar (auf iOS mit Plattform-Resources umständlich), fehlende Übersetzungen sind Compile-Fehler,
  Datumsformat pro Sprache. Standard aus der Gerätesprache (de → Deutsch, sonst Englisch).
- **Einheiten:** kg oder lb, Vorbelegung aus der Region (US, LR, MM → lb). Gespeichert wird immer kg.
- **Profil-Speicher:** Profil, Gewichte und Einstellungen als JSON in SharedPreferences bzw. NSUserDefaults
  (`KeyValueStore`). Das sind wenige Werte; Trainingsdaten bekommen in Phase 1 Room (KMP). Die Übernahme der Gewichte
  in Room wird ein einmaliger, nicht-destruktiver Import.
- **API-Key:** Android: AES-256-GCM-Schlüssel im Android Keystore, nur der Ciphertext liegt in SharedPreferences.
  iOS: Keychain, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (kein iCloud-Sync). Nie im Klartext-Speicher
  (Test `apiKeyNeverLandsInPlainStorage`).
- **KI-Provider:** `LlmProvider`-Interface, aktuell OpenRouter (vorausgewählt), Anthropic, OpenAI. Key-Prüfung über
  kostenlose GET-Endpunkte (`/api/v1/key`, `/v1/models`). Für KMP gibt es kein offizielles Anthropic-SDK, das auf iOS
  läuft, daher plain HTTP über Ktor (`anthropic-version: 2023-06-01`).
- **Onboarding:** Name → Alter (gespeichert als Geburtsjahr) → Gewicht (erster Eintrag der Zeitreihe) → KI-Zugang
  („Später einrichten“ möglich). Startziel der Navigation hängt von `onboardingDone` ab.
- **iOS-Projekt:** XcodeGen-Spec (`iosApp/project.yml`) statt eingechecktem `.xcodeproj` – ohne Mac nicht prüfbar und
  konfliktanfällig. Bundle-ID `app.overfit` (wie Android), iPhone only, iOS 16+, Privacy-Manifest liegt bei.
- **iOS-CI:** Eigener Workflow auf `macos-latest`, nur für PRs nach main, Pushes auf main und manuell (macOS-Minuten
  zählen 10-fach). Führt die Kotlin/Native-Tests im Simulator aus und baut die App für den Simulator.
- **Lokaler Build in der Claude-Umgebung:** Init-Skript `tools/claude-env-maven-mirror.gradle.kts` (per `-I`), setzt den
  Google-Mirror von Maven Central auch für Robolectric. Wird von CI nicht genutzt.
- **Tests:** Domain/Store/Provider/Strings als `commonTest` (läuft als Android-Host-Test und im iOS-Simulator).
  UI-Smoke-Test mit Robolectric (Compose UI Test v2) spielt Onboarding inkl. falschem und gültigem Key, alle Tabs,
  Sprache umschalten und „Alle Daten löschen“ durch – mit In-Memory-Speicher und simuliertem Anbieter.
- **Store-Release (offen, Phase 6):** ~~Für Play Store App Signing + Upload-Key als Secret; der Keystore im Repo bleibt bis
  dahin für die Test-APKs.~~ Ersetzt durch „Signatur über Secrets“ (unten). Credits müssen auf iOS über In-App-Purchase laufen (Guideline 3.1.1).

## Planung – Teilen, Open Source, Coach-Guthaben (2026-11-02)

Wunsch von Fabian: Trainings teilen (WhatsApp, Quick Share/AirDrop, QR), App Open Source, optionales Coach-Guthaben
mit Startguthaben per Einladung. Mit eigenem API-Key bleibt die App komplett kostenlos. Details in SPEC.md
„Erweiterung: Teilen, Open Source, Coach-Guthaben“, Phasen 7–9.

Entscheidungen von Fabian: Kotlin Multiplatform (umgesetzt in #15), Signatur über Secrets mit neuem Schlüssel
(einmalige Deinstallation ist okay), Historie bereinigen, Apache-2.0, Play Store und App Store, NFC später als Spielerei.

Annahmen (getroffen, änderbar):
- **Teilen ohne Backend:** Training komprimiert im URL-Fragment (`https://<domain>/t#…`), dazu QR-Code mit demselben
  Link. Ein Link funktioniert in jedem Kanal (WhatsApp, Quick Share, AirDrop, Mail), das Fragment erreicht nie einen Server.
- **Stabile IDs ab Phase 1:** Übungen `wg:<slug>` / `fdb:<id>` / UUID, Vorlagen und Sessions mit UUID. Kostet jetzt
  fast nichts und erspart später eine Migration.
- **Keine Login-Accounts:** Das Guthaben hängt an einem Geräteschlüssel plus Wiederherstellungscode.
- **Backend nur für das Guthaben:** Cloudflare Workers + D1/Durable Objects (Vorschlag), speichert keine Inhalte.
- **Einladen per QR:** iPhones können für normale Apps keinen NFC-Tag emulieren, NFC ginge nur von Android aus.
- **Einladungsbonus nur mit Geräteattestierung** (Play Integrity / App Attest): Ohne sie ist das Startguthaben trivial
  abzugreifen. Folge: Startguthaben nur für Store-Installationen.
- **Wirtschaftlichkeit:** Store-Gebühr 15 % + Aufschlag. Eine Auswertung kostet ca. 6–8 ct (Sonnet 5.5,
  2 $/10 $ pro MTok), 1 € Start ≈ 12–15 Auswertungen pro geworbenem Nutzer.
- Offen: Name des Guthabens, Höhe von Aufschlag und Einladungsbonus.

## Signatur über Secrets, Lizenz, App Bundle

- **Neuer Schlüssel:** PKCS12 (RSA 4096, Alias `overfit`, Store- und Key-Passwort identisch, wie bei PKCS12 üblich).
  Liegt nur in den GitHub-Secrets `OVERFIT_KEYSTORE_BASE64` und `OVERFIT_KEYSTORE_PASSWORD` und bei Fabian.
  Zertifikat SHA-256: `E7:DF:68:CA:A7:FD:6B:9A:5D:21:45:F9:F2:BA:30:A3:D1:00:5D:A4:38:02:7F:CB:78:EC:FE:59:D4:DF:85:D8`.
  **Dieser Schlüssel darf nie ersetzt werden**, sonst sind Updates ohne Deinstallation (= Datenverlust) unmöglich.
- **Warum der Wechsel:** Der alte Schlüssel und sein Passwort standen im Repo; bei einem öffentlichen Repo könnte jeder
  gültig signierte „Updates“ bauen. Einmaliger Wechsel jetzt, solange es nur Testdaten gibt. Installationen mit altem
  Schlüssel müssen einmal deinstalliert werden (Profil und Key gehen dabei verloren).
- **Gradle:** `app/build.gradle.kts` liest `OVERFIT_KEYSTORE_FILE` und `OVERFIT_KEYSTORE_PASSWORD` aus der Umgebung;
  ohne sie entsteht lokal ein unsigniertes Release. CI dekodiert das Base64-Secret nach `$RUNNER_TEMP` und löscht die
  Datei nach dem Build. Fehlen die Secrets, bricht der Android-Job nach den Tests mit klarer Meldung ab.
- **App Bundle:** CI baut zusätzlich `overfit.aab` (Artefakt, bei `v*`-Tags im Release). In der Play Console beim Anlegen
  „eigenen App-Signaturschlüssel verwenden“ und diesen Schlüssel hochladen (PEPK), damit GitHub-APK und Play-Version
  dieselbe Signatur haben und Nutzer ohne Datenverlust wechseln können.
- **Historie:** `scripts/purge-old-signing-history.sh` (git-filter-repo) entfernt `signing/`, `*.jks` und die
  Passwort-Zeilen aus allen Commits und überschreibt danach alle Branches und Tags per Force-Push. Ich kann nur auf
  meinen Session-Branch pushen, deshalb führt Fabian das Skript aus. GitHub hält alte Commits über PR-Referenzen
  (`refs/pull/*`) noch erreichbar; ganz weg sind sie erst über den GitHub-Support. Weil der alte Schlüssel ersetzt
  ist, ist das kein Sicherheitsproblem mehr.
- **Lizenz:** Apache-2.0 (`LICENSE`) für Code; Grafiken und Schrift behalten ihre eigenen Lizenzen.

## Phase 1 – Übungskatalog, eigene Übungen, Vorlagen (KMP)

Zuerst als native Android-Version gebaut (PR #17), dann nach der KMP-Entscheidung (#15) nach `:shared` portiert.

- **Datenhaltung: Room KMP** (2.8.5) statt SQLDelight. Das Schema aus der Android-Version bleibt unverändert, also
  dieselbe Version 1 und dieselbe Identität. Es gibt `@Relation`, Flows und `MigrationTestHelper`, und die Migrationsregel
  aus der SPEC bleibt so wie bisher. Android nutzt das System-SQLite (Room-Standard), iOS das gebündelte SQLite
  (`sqlite-bundled`, keine Abhängigkeit von der iOS-SQLite-Version). Die Datei heißt `overfit.db`, auf Android im
  Datenbankordner der App, auf iOS unter Documents. Transaktionen laufen über
  `useWriterConnection { immediateTransaction { … } }`.
  Room-Code erzeugt KSP für `android`, `iosArm64` und `iosSimulatorArm64`. Das Schema liegt unter `shared/schemas/`.
- **Datenmodell (v1):** `exercise` mit stabiler Text-ID (`wg:<slug>`, `fedb:<id>`, `custom:<uuid>`), damit Vorlagen,
  Backups und später der KI-Coach dieselben, lesbaren IDs nutzen. `workout_template` → `template_exercise`
  (Reihenfolge, Pause, Notiz) → `template_set` (Zielwerte je Satz: kg, Wdh., Sekunden). Pausenzeit `null` heißt
  „von der Übung bzw. globaler Standard“. Fremdschlüssel: Vorlage → Kinder CASCADE, Übung RESTRICT.
- **Bildquelle Workout Guide:** Das npm-Paket `@bryllim/workout-guide@1.0.0` enthält nur PNGs. Die SVG-Frames gibt es
  im GitHub-Repo, deshalb ist das Repo an Commit `aac5992` gepinnt, free-exercise-db an Commit `f00c92c`. Die SHAs
  stehen oben in `tools/catalog/import_catalog.py`.
- **Import-Skript:** Python 3 ohne Abhängigkeiten. Es gleicht per normalisiertem Namen ab (Kleinschreibung, nur
  Buchstaben/Ziffern, einfacher Plural). Ein führendes Equipment-Wort wird ignoriert, aber nur bei gleichem Equipment
  („Barbell Curl“ ≠ „Cable Curl“). Dazu kommt eine kleine manuelle Dublettenliste. Ergebnis: 1098 Übungen (302 Workout
  Guide, 796 free-exercise-db, 79 Dubletten zusammengeführt). Ausgabe in die **Compose Resources**
  (`shared/src/commonMain/composeResources/files/catalog/`), damit Android und iOS denselben Katalog und dieselben
  SVGs bekommen.
- **Katalog-Seeding:** Beim App-Start, sobald sich die Katalogversion (Hash über den Inhalt) ändert. Neue Übungen
  werden eingefügt, bei bestehenden werden nur Name, Bild, Muskeln und Equipment aktualisiert. Tracking-Typ, Pause und
  Archiv-Status, die man selbst ändern kann, bleiben erhalten. Aus dem Katalog entfernte Übungen werden nie gelöscht.
  Die Version steht im `KeyValueStore`.
- **Übungsnamen bleiben englisch** (Originalnamen beider Kataloge). Die Suche bildet gängige deutsche Begriffe auf die
  englischen Namen ab (Klimmzug → Pull-up, Bankdrücken → Bench Press …). Muskelgruppen und Equipment sind
  vereinheitlicht, haben deutsche und englische Bezeichnungen und sind in beiden Sprachen durchsuchbar,
  unabhängig von der App-Sprache.
- **Suche:** Läuft komplett im Speicher (~1100 Einträge). Alle Wörter müssen passen (UND). Ein Wort passt als Präfix,
  zusammengeschrieben („pushup“) oder mit Tippfehler (Damerau-Levenshtein ≤ 1, ab 8 Zeichen ≤ 2). Bei gleicher
  Trefferqualität kommen eigene Übungen zuerst, dann Workout Guide (bessere Bilder), dann kürzere Namen
  („Pull-up“ vor „Assisted Pull-up“).
- **Tracking-Typ aus dem Katalog:** Workout Guide `weight_reps`/`assisted_bodyweight` → Gewicht × Wdh.,
  `bodyweight_reps` → Wdh., `duration`/`distance_duration` → Zeit (Distanz wird nicht erfasst). free-exercise-db:
  Dehnen/Cardio/statisch ohne Gerät → Zeit, Körpergewicht → Wdh., sonst Gewicht × Wdh. Tracking-Typ und Pausenzeit
  sind auch bei Katalogübungen änderbar (z. B. Klimmzug mit Zusatzgewicht). Ein Katalog-Update überschreibt das nicht.
- **Eigene Übungen löschen:** Steckt die Übung noch in einer Vorlage, wird sie nur archiviert (ausgeblendet),
  sonst gelöscht.
- **Bilder:** Coil 3 (KMP). Die Workout-Guide-SVGs kommen aus den Compose Resources (`Res.getUri`) und werden über
  `ColorFilter.tint` in der Vordergrundfarbe gezeichnet (schwarz-weiß, die Dateien selbst bleiben unverändert).
  free-exercise-db-Fotos lädt Coil über Ktor lazy in seinen Disk-Cache. Ohne Bild gibt es eine Initialen-Kachel.
- **„Alle Bilder offline laden“:** Lädt die Fotos in Coils Disk-Cache vor, gemeinsam für Android und iOS und solange
  die App offen ist. Bereits gespeicherte Bilder werden übersprungen. Die native Fassung nutzte WorkManager und
  eigene Dateien. Das gibt es auf iOS nicht, und rund 50 MB passen bequem in den Standard-Cache (bis 250 MB).
  Kompromiss: Das System kann den Cache bei Speichermangel leeren, dann einfach erneut laden.
- **Gewichte in Vorlagen:** Gespeichert wird immer kg. Eingabe und Anzeige folgen der Einheit aus dem Profil (kg/lb)
  und der Sprache (Komma im Deutschen, Punkt im Englischen, beides wird akzeptiert). Ein „±“ neben dem Feld setzt
  negative Werte, weil viele Zahlentastaturen kein Minus haben (negatives Gewicht = Unterstützung, z. B. Band).
- **UI im Designsystem:** Keine Dialoge und Sheets. Detail, eigene Übung, Vorlagen-Editor und Übungsauswahl sind
  eigene Screens mit `BackHeader` und genau einem `PrimaryButton`. Filter sind horizontal scrollende Chips
  (Muskelgruppe, Equipment, Eigene). Löschen braucht wie „Alle Daten löschen“ einen zweiten Tipp. Wer den Editor mit
  ungespeicherten Änderungen verlässt, muss zweimal Zurück drücken (Hinweis über dem Button, `BackHandler` aus
  `ui-backhandler`). Umsortieren geht vorerst über „Nach oben/unten“, Drag & Drop folgt mit dem Workout-Modus
  (Phase 2).
- **ViewModels:** Für die neuen Screens die KMP-Variante von `lifecycle-viewmodel-compose`, gebunden an den
  Navigationseintrag. Die Übungsauswahl gibt ihr Ergebnis über ein kleines app-weites Objekt (`PickedExercises`)
  zurück. Das ist einfacher als Ergebniswerte im `SavedStateHandle` und auf beiden Plattformen gleich.
- **„Alle Daten löschen“** leert auch die Datenbank (Vorlagen, eigene Übungen) und spielt den Katalog neu ein.
- **Signatur:** Seit #14 kommt der Schlüssel aus den GitHub-Secrets. Lokale Release-Builds sind ohne Secrets unsigniert,
  geprüft wird lokal mit Debug-Build und Tests.
- **INTERNET-Berechtigung** im Android-Manifest ergänzt. Sie fehlte, gebraucht wird sie für Coil und die KI-Anbieter.
- **Tests:** Reine Logik (Suche, Zahlen/Einheiten, Vorlagen-Entwurf) liegt in `commonTest` (Android-Host und
  iOS-Simulator). Datenbank, Seeding, Migration und UI-Durchlauf laufen als Robolectric-Tests in `:app`
  (In-Memory-Room mit Android-Treiber, Katalog direkt aus dem Quellbaum). `MigrationTestHelper` liest die Schemas
  aus den Assets, daher hängt `shared/schemas` an den **debug**-Assets von `:app` (nur Debug-APK, nicht Release).

## Phase 2 – Workout-Modus, Pausentimer, Haptik

- **Datenmodell v2** (`AutoMigration(1 → 2)`, fügt nur Tabellen hinzu, nichts wird gelöscht oder umgeschrieben;
  Migrationstest mit Daten in v1): `workout_session` (UUID, Name, `templateId` ohne Fremdschlüssel, damit
  gelöschte Vorlagen den Verlauf nicht berühren, Status ACTIVE/FINISHED, Start/Ende, Notiz, Gefühl, Foto, aiStatus,
  laufende Pause als `restStartedAt`/`restEndsAt`) → `session_exercise` (UUID, Reihenfolge, Pause, Notiz, Herkunft
  PLANNED/ADDED/REPLACED + ersetzte Übung) → `set_entry` (UUID, Ziel- und Istwerte, erledigt, Zeitpunkt, RPE).
  Übungen in Trainings blockieren das Löschen (RESTRICT): eigene Übungen werden dann archiviert wie bei Vorlagen.
- **Workout-State:** reines Modell `domain/Workout.kt` (alle Änderungen als Funktionen, gut testbar) plus
  `WorkoutController` (eine Instanz pro Prozess in `AppDependencies`, Main-Thread). Nach jeder Änderung wird das
  ganze Training in einer Transaktion geschrieben (wenige Dutzend Zeilen). Schreibvorgänge werden zusammengefasst
  (nur der neueste Stand) und sind mit Beenden/Abbrechen strikt geordnet; ein beendetes Training wird von einem
  späten Schreibvorgang nie wieder aktiv. Beim App-Start lädt der Controller das aktive Training, die App springt
  direkt hinein.
- **Vorbelegung:** Istwerte starten mit den Zielwerten der Vorlage, Lücken kommen von „letztes Mal“ (erledigte Sätze
  des letzten abgeschlossenen Trainings mit der Übung, je Satzposition). Abhaken einer leeren Zeile übernimmt die
  Zielwerte. Unterwegs hinzugefügte Übungen bekommen die Sätze vom letzten Mal, sonst 3 leere.
- **Pausentimer als Wanduhrzeiten** (Start/Ende in ms, gespeichert): läuft ohne App weiter und überlebt den
  Prozess-Tod. Dauer: Vorlage → Übung → globale Standard-Pause (neu im Profil, Standard 90 s). Nach dem letzten
  offenen Satz startet keine Pause. ±15 s, Überspringen; „−15 s“ über die Restzeit hinaus beendet die Pause. Wird
  der Satz wieder geöffnet, der die Pause gestartet hat, endet sie. Der Controller bekommt Uhr und Scope injiziert,
  dadurch Tests mit virtueller Zeit.
- **Android – Hintergrund:** laufende, stumme Benachrichtigung mit systemseitigem Countdown (Chronometer, keine
  Wakeups) und Aktionen +15 s / Überspringen (Receiver erreicht den Controller auch nach Prozess-Tod). Zum
  Pausenende ein Alarm per `setExactAndAllowWhileIdle`, der die Benachrichtigung „Pause vorbei“ (hohe Priorität,
  Vibration) zeigt. **Kein Foreground-Service, kein Wakelock.** Gründe: `shortService` ist auf 3 min begrenzt,
  `specialUse` braucht eine Play-Begründung, `USE_EXACT_ALARM` ist Wecker-/Kalender-Apps vorbehalten.
  `SCHEDULE_EXACT_ALARM` muss der Nutzer ab Android 14 erlauben: Im Profil erscheint dann „Pausenende sekundengenau
  → Erlauben“ (Systemeinstellung). Ohne Erlaubnis `setAndAllowWhileIdle` (kann bei gesperrtem Bildschirm etwas später
  kommen). Ist die App sichtbar, unterdrückt der Receiver die Benachrichtigung, die App vibriert selbst.
  Benachrichtigungs-Berechtigung (Android 13+) wird beim ersten Trainingsstart abgefragt. Zwei Kanäle für das
  Pausenende (mit/ohne Vibration), weil Kanaleinstellungen später nicht änderbar sind. Ton vorerst aus.
- **iOS:** lokale Benachrichtigung zum Pausenende (`UNTimeIntervalNotificationTrigger`, feste ID, ±15 s ersetzt
  sie). Im Vordergrund zeigt iOS sie ohne Delegate nicht an, die App vibriert selbst. Mit Vibration an hat die
  Benachrichtigung den Standardton, weil iOS sonst nicht vibriert (im Lautlos-Modus nur Vibration). Live Activity
  später.
- **Haptik** als Interface `Haptics` nach den Produktentscheidungen der Koordination: **Tick** = leichter Klick beim
  Abhaken (Android `EFFECT_CLICK`, iOS leichter Impact), **RestEnd** = drei kurze Pulse (Android Waveform
  3 × 120 ms, iOS 3 × heavy Impact), **Reveal** = Herzschlag aus zwei Pulsen und **RevealFinish** = ein kräftiger
  Puls, angelegt für den späteren Spot-Reveal (noch ungenutzt). Schalter „Vibration“ im Profil (Standard an) gilt
  für App-Haptik und Benachrichtigungen.
- **Training beenden** legt das Training vorerst nur als abgeschlossen ab (Beenden-Flow folgt in Phase 3). Beenden
  und Abbrechen brauchen wie Löschen einen zweiten Tipp. Abbrechen löscht das Training mit allen Sätzen.
- **Drag & Drop** ohne Fremdbibliothek (`ui/components/Reorder.kt`): langes Drücken auf den Griff, Tausch, sobald die
  Kante die Mitte des Nachbarn überschreitet (kein Hin- und Herspringen bei unterschiedlich hohen Einträgen),
  Auto-Scroll am Rand. Im Training und im Vorlagen-Editor; „Nach oben/unten“ bleibt dort für Barrierefreiheit.
- **Pausenleiste** nie Vollbild: minimiert eine Leiste über dem Button „Nächsten Satz loggen“, erweitert ein höherer
  Bereich mit großem Countdown. Die Liste bleibt darüber bedienbar; Tippen auf „Nächste“ springt zur Übung.
- **Offen:** „Bildschirm anlassen“, Ton zum Pausenende, RPE-Feld und Live Activity folgen später (Phase 5).

## Phase 8: Trainings teilen (Session B)
- **Deflate selbst geschrieben:** Die Kotlin-Standardbibliothek hat kein Deflate, und eine KMP-Bibliothek nur dafür wäre
  zusätzliche Abhängigkeit auf beiden Plattformen. `Deflate.kt` (Raw Deflate, RFC 1951) schreibt einen Fixed-Huffman-Block
  mit LZ77, liest aber alle Blocktypen (Test mit dynamischem Block von Python zlib). Die Web-Seite liest es mit
  `DecompressionStream('deflate-raw')`. Ausgabegrenze 200 KB gegen Zip-Bomben.
- **Format:** Zusätzlich zur SPEC `name` statt `id` pro Übung (für KI-Vorlagen), `note`, `restSec`, Sätze als
  `{kg, reps, sec}`. `kind` ist ein String (`template` jetzt, `workout` später); unbekannte `kind` und höhere `v` werden
  mit eigener Fehlermeldung abgelehnt. Der Leser ist tolerant bei Werten ("8-12", "82,5 kg") und streng bei der Struktur.
  Details: `docs/share-format.md`.
- **Auflösung:** `ref` → eigene Übung gleichen Namens, sonst neu; `id` → Katalog; `name` → exakter Treffer, sonst bester
  Treffer der unscharfen Suche (DE/EN); sonst neue eigene Übung. Nicht zur Erfassungsart passende Satzwerte werden
  verworfen (z. B. Gewicht bei Klimmzug mit „Wdh.“).
- **QR-Code selbst geschrieben:** Reiner Kotlin-Generator (Byte-Modus, Fehlerkorrektur M, Version 1–30) in commonMain,
  damit Android und iOS denselben Code zeichnen, ohne ZXing auf iOS. Gegen ZXing-Decoder getestet (Versionen 1–30,
  inkl. Umlaute). QR nur bis 1.000 Zeichen Link, sonst Hinweis und Datei/Link.
- **Scannen:** Android in der App mit CameraX + ZXing core (Apache-2.0, keine Play-Dienste). ~~**iOS: kein In-App-Scanner,
  die Kamera-App reicht**, weil der QR ein Link ist (`overfit://`-Link öffnet die App direkt, der https-Link den
  Browser/die Web-Seite mit „In Overfit öffnen“). Grund: AVFoundation-Scanner lässt sich unter Linux nicht testen, und der
  Nutzen gegenüber der Kamera-App ist klein. Die Import-Seite zeigt auf iOS einen Hinweis statt des Scan-Knopfs.~~
  Ersetzt (Session U): seit dem kompakten `OF1:`-QR ist der Code kein Link mehr, iOS scannt in der App (AVFoundation).
- **Android-Links:** Intent-Filter für `https://bigfire3.github.io/overfit/t` **ohne** `autoVerify` (die Domain gehört
  nicht der App; ohne Verifizierung fragt Android bzw. der Browser) sowie `overfit://`, `.overfit`-Dateien
  (`application/x-overfit`, `content`/`file`). `launchMode=singleTop`, damit ein zweiter Link in der laufenden App
  ankommt. iOS: URL-Schema `overfit` und Dokumenttyp `.overfit`; Universal Links bräuchten eine Domain, die Overfit
  gehört (siehe PROGRESS).
- **Eingang von außen:** `ImportInbox` (ein Text wartet auf den Import-Bildschirm). Die Plattform-Hüllen legen dort ab,
  die UI öffnet den Import (nicht während des Onboardings).
- **`PlatformShare`-Interface** statt expect/actual-Funktionen: Share-Sheet, Zwischenablage und Datei brauchen auf
  Android einen `Context`, und Tests ersetzen es durch `NoopPlatformShare`.
- **KI-Prompt:** Liegt in `ShareStrings` (DE/EN, App-Sprache), enthält ein Beispiel (`AiPrompt.EXAMPLE`), das in Tests
  importiert wird. Übungen verlangt der Prompt per Name (englischer Alltagsname), weil Chat-Assistenten die
  Katalog-IDs nicht kennen.
- **Strings:** Die Teilen-Texte stehen in `i18n/ShareStrings.kt` (eigene Datei, DE+EN, fehlende Übersetzung = Compile-Fehler)
  und hängen als `Strings.share` ein, um Merge-Konflikte in der großen `Strings.kt` klein zu halten.
- **Beispielvorlagen:** `files/samples/{push,pull,legs}.json` in der gleichen Datei-Form wie der Export. „Hinzufügen“
  ist idempotent über den Namen (Push/Pull/Legs), wer eine löscht, kann sie neu holen.
- **Duo:** (entfernt 3.11., Fabian; später evtl.) Nur ein Teaser-Eintrag „Duo · bald“ auf dem Startbildschirm und ein Text-Screen, keine Datenerhebung.
## Produktentscheidungen Nacht 3.11.

Zentrale Entscheidungen für alle Nacht-Sessions (Backend, Teilen, Workout-Modus, KI-Coach, App-Anbindung):

- **Fuel:** Das Coach-Guthaben heißt „Fuel“ (DE und EN gleich) und wird in Euro angezeigt, z. B. „Fuel · 4,20 €“.
- **Spot:** Das Einladungsprogramm heißt „Spot“ (wie der Trainingspartner, der beim Bankdrücken sichert). EN „Spot a friend“,
  DE „Freund spotten“. Eingeladene bekommen 1 € Fuel, Einladende 1 € Fuel, sobald der Neue seine erste Coach-Auswertung gemacht hat.
- **Reveal beim Einlösen eines Spots:** Bildschirm wird schwarz, zwei kurze Haptik-Pulse (Herzschlag), dann zählt eine große
  Zahl in Inter mit tabellarischen Ziffern von 0,00 auf 1,00 € hoch, darunter „Spot von <Name>“; am Ende ein kräftiger Puls.
  Bewusste Ausnahme von „keine Animationen“.
- **Haptik:** leichter Tick beim Abhaken eines Satzes, drei kurze Pulse am Ende der Pause, das Reveal-Muster. Schalter
  „Vibration“ im Profil (Standard an).
- **Duo-Modus** (entfernt 3.11., Fabian; später evtl.) (zwei Handys trainieren gemeinsam: synchrone Sätze, gemeinsamer Pausentimer) wird nur angeteasert: Eintrag
  „Duo · bald“ mit kurzer Erklärung, keine Datenerhebung.
- **Teilen/Import:** Format laut SPEC und `docs/share-format.md` (JSON, Raw Deflate, Base64url im URL-Fragment); Beispielvorlagen
  Push, Pull, Legs in `samples/`.

### Backend (Fuel & Spot) und Web-Fallback

- **Cloudflare Worker + D1**, hand-gerollter Router ohne Framework. Tests mit Vitest in Miniflare. Wir nutzen
  `@cloudflare/vitest-plugin` (der Nachfolger des umbenannten, als veraltet markierten `@cloudflare/vitest-pool-workers`;
  gleiche Funktionalität). Atomarität ohne Durable Objects: Reservierung als einzelnes `INSERT … SELECT … WHERE saldo ≥ betrag`,
  Abrechnung als D1-Batch (Transaktion).
- **Beträge:** Integer in **Mikro-Euro** (1 € = 1 000 000), nicht Zehntel-Cent: Eine Auswertung kostet wenige Cent, aber einzelne
  Anfragen nur Bruchteile davon; Mikro-Euro vermeidet Rundungsfehler bei Tokenpreisen. Rechnen mit BigInt, immer aufrunden zugunsten
  des Betreibers (Kosten) bzw. abrunden (Paket-Gutschrift). Anzeige rundet die App auf Cent.
- **Preise in Euro, nicht USD:** `PRICE_TABLE` enthält µ€ je 1 Mio. Token (Listenpreis × Wechselkurs, vom Betreiber gepflegt, Standard
  0,92 €/$ nur für Sonnet 5.5). Kein Live-Wechselkurs: ein Fehler dort würde direkt Geld kosten; der Aufschlag deckt Schwankungen.
- **Signatur:** ECDSA P-256, kanonischer String `OVERFIT-V1\nMETHODE\nPFAD?QUERY\nTS\nNONCE\nSHA256(Body)`, ±5 min, Nonce-Tabelle
  gegen Replay. Server akzeptiert Roh- und DER-Signaturen sowie Roh- und SPKI-Schlüssel, damit Android Keystore und CryptoKit
  ohne Umwandlung funktionieren. Geräte-ID = SHA-256 des Rohschlüssels. Details: `docs/backend-api.md`.
- **Guthaben-Posten (`lots`):** Käufe verfallen nie, Startguthaben/Belohnung nach 180 Tagen. Abgebucht wird zuerst, was zuerst
  verfällt. „Nur für Coach-Anfragen“ ergibt sich daraus, dass `/v1/messages` die einzige Ausgabe ist.
- **Wiederherstellung:** Code mit 120 Bit (Hash in der DB), Umzug meldet alle anderen Geräte des Kontos ab (gestohlenes altes
  Handy verliert Zugriff) und entwertet den Code.
- **Spot-Token:** HMAC-signiert (kein Server-Zustand bis zum Einlösen), 45 s gültig, einmalig über Tabelle `spot_tokens_used`.
  Attestierung läuft über eine serverseitige, an das Gerät gebundene Challenge. Gerätekennung der Attestierung ist UNIQUE
  (einmal pro Gerät, übersteht Neuinstallation). Das globale Monatsbudget rechnet jeden Spot mit Startguthaben + Belohnung
  (die Belohnung wird dem Einladenden auch bei erschöpftem Budget immer gutgeschrieben, nur neue Einlösungen werden gestoppt).
- **Mock-Modi nur mit `DEV_MODE=true`:** Ein versehentlich gesetztes `ATTESTATION_MODE=mock` in Produktion hat keine Wirkung.
- **Echte Store-/Attestierungsprüfer fehlen noch** (brauchen Konten und Zugangsdaten): Schnittstellen und Mocks sind fertig,
  Produktion antwortet 503, bis sie nachgezogen sind. Das Anthropic-Proxying funktioniert allein mit `ANTHROPIC_API_KEY`.
- **Reservierung:** Eingabe-Token werden als UTF-8-Bytes/2 geschätzt (konservativ), Bilder pauschal mit 3000 Token, dazu
  `max_tokens` × Ausgabepreis, alles mit Aufschlag; `max_tokens` ist auf 8192 begrenzt. Ist die Eingabe-Schätzung zu niedrig, wird
  höchstens der reservierte Betrag abgebucht (Verlust beim Betreiber, nie Überziehen des Kontos).
- **Web-Fallback** unter `https://bigfire3.github.io/overfit/` (Pages-Workflow, Quelle `web/`). `t/index.html` liest nur,
  alles clientseitig (Fragment verlässt nie den Browser), Namen werden ausschließlich per `textContent` eingefügt. Inter wird erst
  im Workflow aus den App-Ressourcen kopiert, damit keine zweite Kopie im Repo liegt. **Einschränkung:** Android App Links /
  iOS Universal Links brauchen `/.well-known/…` im Wurzelverzeichnis der Domain; unter `bigfire3.github.io/overfit/` ist das nicht
  möglich. Bis eine eigene Domain steht, hilft der Button „In Overfit öffnen“ (`overfit://t#…`).

## Fuel & Spot in der App (Nacht 3.11., Session D)
- **Texte in eigener Datei:** `i18n/FuelStrings.kt` (DE + EN, fehlende Übersetzung = Compile-Fehler wie bei `Strings`), damit paralleles Arbeiten an `Strings.kt` nicht kollidiert. Kann später in `Strings.kt` einfließen.
- **Geräte-Identität:** `DeviceKey` (expect-ähnlich über Interface + Plattformklassen): Android Keystore (EC P-256, nicht exportierbar, StrongBox wenn möglich), iOS Secure Enclave mit Keychain-Softwareschlüssel als Rückfall (Simulator). Signatur DER (der Server akzeptiert beides). Öffentlicher Schlüssel wird immer als 65-Byte-Rohpunkt gesendet. SHA-256 ist reines Kotlin (keine Plattformabhängigkeit), abgesichert mit Standardvektoren; die Signatur ist gegen einen mit WebCrypto erzeugten Testvektor geprüft (`SignatureVectorTest`).
- **Backend-URL:** Build-Zeit `OVERFIT_BACKEND_URL` (Repository-Variable → Android `BuildConfig`, iOS `Info.plist` über XcodeGen); Entwickler-Override durch 7× Tippen auf die Version in „Über“ (nur im Speicher entsperrt, Wert dauerhaft in `KeyValueStore`). Leer = „Fuel startet bald“ + Demo.
- **Fuel als KI-Zugang:** `ProviderId.FUEL` + `FuelProvider` (nutzt keinen Key, „Validierung“ = Konto abrufbar). Er steht in der Auswahl vor den Key-Anbietern; „Fuel verwenden“ öffnet den Fuel-Bildschirm. Ein vorhandener eigener Key bleibt gespeichert. Phase 4 ruft `FuelProvider.messages(bodyJson)` auf (liefert Kosten und neuen Saldo).
- **Demo:** `DemoFuelService` implementiert dieselbe `FuelService`-Schnittstelle lokal; alle Screens laufen darauf, Banner „Demo“, keine echten Daten.
- **Anzeige von Beträgen:** DE „4,20 €“, EN „€4.20“ (kaufmännisch gerundet). Kosten unter 1 Cent mit 4 Nachkommastellen, damit sie nicht wie 0 aussehen.
- **Spot-Link im QR:** `overfit://spot#<token>` (Token im Fragment), wie bei Trainings auf iOS: die Kamera-App öffnet den Link direkt in Overfit (landet über `ImportInbox` bei „Ich wurde gespottet“, Code vorbelegt, Einlösen per Tippen); Android scannt zusätzlich in der App (Scanner und QR-Darstellung aus Session B, mein eigener Encoder entfällt). Keine Web-Seite nötig.
- **Attestierung:** Android Play Integrity (Nonce = base64url(SHA-256(challenge))), iOS App Attest (clientDataHash = SHA-256(challenge); Token = JSON `{"keyId","attestation"(base64url)}`). Das Format gegenüber dem späteren Server-Prüfer ist damit festgelegt und muss dort so gelesen werden. Ohne Support/Konfiguration → `attestation_unavailable` + freundliche Meldung.
- **Reveal:** schwarzer Vollbildschirm (immer schwarz/weiß, unabhängig vom Theme), Haptik `Reveal` (zwei Pulse), 1,1 s Schwarz, Zahl zählt 2,4 s ease-out hoch (Inter, tabellarische Ziffern), am Ende `RevealFinish`; Tippen schließt erst nach dem Ende.
- **Haptics:** API 1:1 von Session A übernommen (`Haptics`, `HapticPattern`, `AndroidHaptics`, `IosHaptics`); beim Merge identisch.

### Zur Prüfung durch Fabian
- Spot-QR enthält `overfit://spot#…` (ohne installierte App passiert beim Scannen nichts; eine Web-Seite dafür wäre möglich).
- Name „Fuel · 4,20 €“ in EN als „€4.20“ formatiert (DE „4,20 €“).

## KI-Chat-Verbindungen (Phase 10a, Nacht 3.11., Session E)

- **Ein Remote-MCP-Server für alle drei Plattformen** im bestehenden Worker unter `/mcp` (Streamable HTTP, zustandslos, Antwort
  als `application/json`, kein SSE). Begründung: Claude (Custom Connectors), ChatGPT (Apps/Connectors, Entwicklermodus) und
  Gemini („Eigene Apps“) sprechen heute alle Remote-MCP mit OAuth; ein Server deckt alle ab. Kein MCP-SDK: der hand-gerollte
  Router bleibt ohne Abhängigkeiten, JSON-RPC-Teil ist klein (initialize, ping, tools/*, prompts/*, resources/list).
- **OAuth 2.1 im selben Worker, Anmeldung per Kopplungscode:** RFC 8414/9728-Metadaten, Dynamic Client Registration (öffentliche
  und vertrauliche Clients, weil ChatGPT je nach Version ein Secret anfordert), zusätzlich Client ID Metadata Documents (laut
  aktueller MCP-Spezifikation der empfohlene Weg), PKCE S256 Pflicht, `resource` geprüft, Code 5 min einmalig, Access-Token 1 h,
  Refresh-Token 90 Tage mit Rotation. Tokens/Codes nur als SHA-256 gespeichert. Anmeldeseite ohne JavaScript, `frame-ancestors
  'none'`, zeigt Client-Name **und** Ziel-Host (gegen Phishing mit gefälschtem `client_name`). Kopplungscode 8 Zeichen aus 31
  (~40 Bit), 10 min, einmalig, Fehlversuche 10 / 10 min je IP.
- **Verschlüsselung at rest:** AES-256-GCM, Schlüssel per HKDF aus `SYNC_ENCRYPTION_KEY` + Konto-ID, AAD = Konto/Art/ID (Zeilen
  lassen sich nicht vertauschen). Schützt gegen DB-/R2-Abzüge, nicht gegen den Betreiber – ehrlich so dokumentiert.
  Ende-zu-Ende ist unmöglich, weil der Server die Daten für den Chat lesen muss.
- **Dokument-Sync statt Datenbank-Spiegel:** Die App lädt pro Vorlage/Training ein JSON-Dokument (Last-Writer-Wins über
  `updatedAt` in ms). Kennzahlen je Training (Volumen, Top-Satz, e1RM nach Epley bis 12 Wdh., längster Halt) berechnet der
  Server beim Upload und legt sie verschlüsselt in eine eigene Spalte – Fortschrittsabfragen über Jahre bleiben so billig (CPU-
  Limit der Worker), ohne alle Trainings zu entschlüsseln.
- **Eingang statt direktem Schreiben:** Der Chat ändert nie direkt Daten der App. Vorlagen landen im Teilen-Format v1 im Eingang
  (dieselbe Import-Vorschau wie beim Link), Notizen ebenso; jede Vorlage kommt zusätzlich als Import-Link zurück, damit der Weg
  auch ohne Sync-Abruf und in Plattformen ohne Schreib-Tools funktioniert. Obergrenze 50 offene Einträge.
- **Katalog im Worker:** Schlanke Kopie des App-Katalogs (`backend/src/catalog.gen.json`, ~90 KB, Skript
  `backend/scripts/build-catalog.mjs`) für `search_exercise_catalog`, damit der Chat gültige IDs verwendet. Unbekannte IDs mit
  Namen werden über den Namen importiert, eigene Übungen des Nutzers als `custom[]`.
- **Gemini-/ChatGPT-Ersatzweg:** Wo kein Connector erlaubt ist, Gem/Custom GPT mit Anleitung + „Kontext kopieren“ + Rückweg
  über den Teilen-Link (Session B). Keine Gemini-Extensions oder GPT-Actions als zweite API, um nur einen Server zu pflegen.

### Zur Prüfung durch Fabian
- **Bewusste Opt-in-Ausnahme von „kein Cloud-Sync / Backend speichert keine Trainingsinhalte“:** Nur nach „KI-Chat verbinden“
  werden Trainingsdaten (Fotos nur mit eigenem Häkchen) verschlüsselt im Backend gespeichert; Abschalten löscht alles sofort.
  Umkehrbar: Ohne Einschalten ändert sich nichts, der Endpunkt lässt sich serverseitig komplett entfernen. Für den Store-Start
  braucht es dazu einen Absatz in der Datenschutzerklärung (Art. 9 DSGVO: Gesundheits-/Körperdaten → ausdrückliche
  Einwilligung; die App holt sie mit dem Einschalt-Dialog ein) und die Info, dass die Daten an den gewählten Chat-Anbieter
  gehen (dessen Bedingungen gelten).
- Standard-Grenzen (5000 Dokumente / 25 MB, 300 Fotos à 3 MB, Refresh-Token 90 Tage) sind Schätzwerte; R2-Kosten bleiben im
  kostenlosen Kontingent, solange es wenige Nutzer sind.

## Phase 3 – Beenden-Flow, Verlauf, Fotos, Backup (Nacht 3.11., Session F)
- **Room v3**: neue Tabelle `workout_photo` (mehrere Fotos je Training, Datei app-privat unter `filesDir/photos` bzw.
  Application Support/photos, Name `<uuid>.jpg`). Reine Ergänzung → AutoMigration 2→3 + Migrationstest. Die alte Spalte
  `workout_session.photoPath` bleibt ungenutzt stehen (nicht destruktiv).
- **Fotos** werden beim Hinzufügen auf max. 1600 px lange Kante verkleinert, EXIF-Drehung angewendet, JPEG 85 %
  (`prepareJpeg`, expect/actual). Kamera Android: System-Kamera-Intent in den App-Cache (nie Galerie), Foto-Auswahl über
  den System-Photo-Picker (keine Speicherberechtigung). Weil das Manifest CAMERA (QR-Scanner) deklariert, fragt die App vor
  dem Kamera-Intent die Berechtigung ab. iOS: UIImagePickerController (Kamera/Mediathek), Info.plist-Texte ergänzt.
- **Beenden-Flow**: „Beenden“ legt das Training wie bisher sofort als abgeschlossen ab (kein Datenverlust, falls die App
  danach stirbt); der Flow ergänzt nur Notiz, Gefühl (1–5, optional) und Fotos. Zurück = Speichern.
  Coach-Platz für Phase 4: `AppDependencies.finishCoachSection` + `workoutSavedListeners`.
- **Bestwerte**: höchstes Gewicht, geschätztes 1RM (Epley, nur 1–12 Wdh.), meiste Wdh., längste Zeit – je nach
  Erfassungsart. Das erste Mal ist kein Bestwert. Volumen = kg × Wdh. erledigter Sätze mit positivem Gewicht.
- **Backup**: eine ZIP-Datei (`backup.json` deflate + `photos/*.jpg` unkomprimiert), eigener kleiner ZIP-Writer/-Reader
  in commonMain (keine Bibliothek nötig, jede Entpack-App kann die Datei öffnen). Enthält abgeschlossene Trainings,
  Vorlagen, eigene und referenzierte Übungen inkl. geänderter Einstellungen, Fotos, Profil und Einstellungen; nie API-Key,
  Geräteschlüssel, Fuel-Daten. Import: erst vollständig lesen/prüfen (Format, Version, CRC), Vorschau, dann
  „Alle Daten ersetzen“ (wie „Alle Daten löschen“ + Einfügen). Neuere Version → „App aktualisieren“; unbekannte Felder
  werden ignoriert, fehlende Übungen als archivierte eigene Übung angelegt. Während eines laufenden Trainings gesperrt.
  Andere Features hängen sich über `BackupContributor` (`extras`) an (Phase 4: Coach-Gedächtnis).
  Laufende Trainings sind nicht im Backup. Alles im Speicher (ok bis einige hundert Fotos; Streaming später).
- **Teilen eines Trainings**: `kind: "workout"`, Standard „mit Ist-Werten“ (Schalter auf dem Teilen-Screen), Import wird
  zur Vorlage. Die Web-Fallback-Seite konnte `workout` schon anzeigen.
- **Änderungs-Benachrichtigung** für KI-Chat-Sync/Coach: `WorkoutRepository.changes` bzw. `AppData.dataChanged`
  (Room-InvalidationTracker, kein Polling).

### Zur Prüfung durch Fabian
- Backup-Format ZIP statt reinem JSON (wegen Fotos); Dateiname `overfit-backup-<Datum>.zip`.
## KI-Chat-Verbindungen, App-Seite (Phase 10b, Nacht 3.11., Session H)
- **Sync per Fingerabdruck statt Änderungsprotokoll:** `ChatSync` vergleicht je Dokument einen Hash des Inhalts (nicht von `updatedAt`) mit
  dem zuletzt hochgeladenen Stand (SharedPreferences/UserDefaults). Dadurch gibt es keine Warteschlange, die verloren gehen kann:
  Fehler (offline, 5xx) setzen nur „beim nächsten Lauf alles vergleichen“; nach einem Prozessstart wird einmal alles verglichen (Marker
  vor dem Prozesstod sind weg). Nachteil: ein Lauf liest alle Trainings aus Room – bei einigen hundert Trainings unkritisch.
- **Keine Schemaänderung:** Sync-Zustand und Notizen aus dem Chat liegen im Key-Value-Speicher; Room bleibt unberührt, damit
  Phase 3/4 (v3/v4) nicht kollidieren. Nur additive DAO-Abfragen (`WorkoutDao.getAll`, `TemplateDao.getIds`).
- **Vorlagen-ID im Sync = lokale Nummer als Text** (`template/12`); `replacesTemplateId` kommt genauso zurück. Trainings: Session-UUID.
- **Transport:** `FuelApi` implementiert `SignedTransport` (Geräte-Identität, Signatur, Re-Registrierung, Zeitkorrektur), `ChatApi`
  setzt darauf auf – eine Identität, ein Signaturpfad.
- **Einschalten/Trennen laufen im App-Scope** (`AppData.scope`), nicht im Bildschirm-Scope (Falle aus PR #25).
- **Notizen aus dem Chat:** Übernehmen speichert in `ChatNotes` (Liste im Profil, `forWorkout` für den Verlauf), Verwerfen löscht. `ack` für beide;
  schlägt es fehl, wird es gemerkt und nachgeholt, damit nichts doppelt kommt.
- **Kontext-Text ≤ 12 000 Zeichen** (passt in jeden Chat); bei Überlänge werden die ältesten Trainings gekürzt, die Antwortregeln bleiben.
  Rückweg über den vorhandenen Zwischenablage-Import (```json-Block im Teilen-Format).
- **Markdown in Notizen** nur als flache Zeilen (Überschrift, Aufzählung, `**`/`` ` `` entfernt) – passt zum Designsystem ohne Spans.

### Zur Prüfung durch Fabian
- Texte der Einwilligung („Daten gehen an den Chat-Anbieter, nicht für Werbung/KI-Training genutzt“) sind meine Formulierung; vor dem
  Store-Start juristisch prüfen (zusammen mit der Datenschutzerklärung aus Phase 10a).
- Chat-Anleitungen (Claude/ChatGPT/Gemini-Menüs) entsprechen dem Stand laut `backend/README.md`; die Oberflächen der Anbieter ändern sich oft.

## Phase 4 – KI-Coach (Nacht 3.11., Session G)
- **Antwortformat: Structured Outputs statt Tool.** Anthropic/Fuel: `output_config.format` (JSON-Schema), OpenAI/OpenRouter:
  `response_format: json_schema, strict`. Kein erzwungenes `tool_choice` (Sonnet 5.5 lehnt `any`/`tool` mit 400 ab). Der
  Parser (`coach/CoachParser.kt`) verlässt sich trotzdem nicht darauf: JSON in Fences/Prosa, camelCase, Zahlen als Text,
  Wrapper-Objekt, Trailing Commas, Werte außerhalb des Bereichs werden geklemmt statt die Auswertung zu verwerfen. Antwortet
  ein Anbieter auf das Schema mit 400 (manche OpenRouter-Modelle), wird einmal ohne Schema gefragt; ungültige Antwort → ein
  zweiter Versuch, danach FAILED mit gespeicherter Rohantwort.
- **Schema:** alle Felder required, optionale als `anyOf [typ, null]`, keine Zahlengrenzen (gemeinsame Teilmenge beider
  Anbieter im strict-Modus). `next_session` ist die **vollständige** Übungsliste der nächsten Einheit; der Plan-Diff entsteht
  in der App (`coach/PlanDiff.kt`, Zuordnung per Übungs-ID in Reihenfolge), damit „einzeln annehmen“ zuverlässig ist und
  unbekannte IDs ignoriert werden.
- **Prompt-Caching:** statischer System-Block (Coach-Anweisungen, Ausgaberegeln, Übungsliste) mit `cache_control`, danach der
  Profil-Block mit heutigem Datum ohne Cache. OpenAI cached automatisch, OpenRouter bekommt `cache_control` am System-Teil.
- **Übungsliste im Prompt:** alle `wg:`-Übungen (302), eigene Übungen und alles, was in Vorlagen/Trainings vorkommt – nicht
  alle 1.098 (die 796 `fedb:` kosten ~10k Token pro Anfrage, Cache hält nur 5 Minuten, eine Auswertung pro Training).
- **Modelle:** Standard Anthropic `claude-sonnet-5-5` (SPEC), OpenAI `gpt-5`, OpenRouter `anthropic/claude-sonnet-5.5`, in
  Profil → Coach → Coach-Einstellungen änderbar. Fuel ist fest `claude-sonnet-5-5` (Backend erlaubt nur dieses; überholt: Stufen, siehe „Fuel: Stufen statt fester Anbieter“). `effort`
  `medium`, `max_tokens` 8.000 (Fuel-Grenze 8.192).
- **Warteschlange ohne WorkManager:** Jede Anfrage ist sofort eine Zeile in `coach_review` (QUEUED). Netzfehler, Timeout
  (150 s), 429, 5xx → bleibt QUEUED und wird beim App-Start und jedem Zurückkommen in den Vordergrund automatisch gesendet;
  Key ungültig, kein Fuel, kein Zugang, Antwort zweimal unbrauchbar → FAILED mit „Erneut versuchen“. Senden läuft im
  App-Scope (`AppData.scope`), nie im Screen-Scope. WorkManager (Senden bei geschlossener App) bewusst weggelassen: iOS
  hätte kein Gegenstück, und der Nutzer öffnet die App nach dem Training ohnehin wieder.
- **Room v4:** `coach_review` (Anfrage + Ergebnis + Warteschlange, Status, Plan-Status, Fuel-Kosten, Rohantwort) und
  `coach_memory` (Versionen, neueste = aktuell). Nur neue Tabellen → AutoMigration `3 → 4` (nach Phase 3 = v3).
- **Coach-Gedächtnis:** Der Coach liefert pro Auswertung die komplette neue Fassung; jede Änderung (Coach oder Hand) wird
  eine Version. „Löschen“ entfernt alle Versionen (Datenschutz), „Alle Daten löschen“ auch Auswertungen und Coach-Einstellungen.
- **Fotos nur mit Einwilligung:** Schalter „Körperfotos an Coach senden“ (Standard aus). Dann Trainingsfoto + jüngstes Foto,
  das mindestens 4 Wochen älter ist (aus `workout_photo` von Phase 3), über `prepareJpeg` auf 1.500 px lange Kante, JPEG 85.
- **Beenden-Flow:** Abschnitt „Coach“ mit „Senden / Später“ (Standard Senden, nur mit KI-Zugang); gesendet wird beim
  „Speichern“ (`workoutSavedListeners`), damit Notiz, Gefühl und Fotos schon gespeichert sind und mitgehen.
- **Backup:** `CoachBackup` (BackupContributor „coach“): Auswertungen, alle Gedächtnis-Versionen, Coach-Einstellungen; ein
  Backup ohne Coach-Daten leert sie beim Import (Import = „Alle Daten ersetzen“).
- **Ohne KI-Zugang:** Hinweis mit Wegen zu „KI verbinden“ und Fuel, dazu eine **Demo-Auswertung** aus dem eigenen Training
  (+2,5 kg auf die erste Gewichtsübung), deutlich als Demo markiert; Plan übernehmen ändert in der Demo nichts.
- **Coach-Anweisungen editierbar** (SPEC): Standardtext je App-Sprache; wer ihn ändert, behält seine Fassung auch nach einem
  Sprachwechsel; „Standard wiederherstellen“ setzt zurück.
- **Freies Training ohne Vorlage:** Der Planvorschlag kann als neue Vorlage „<Name> (Coach)“ gespeichert werden.

### Zur Prüfung durch Fabian
- Standardmodelle für OpenAI/OpenRouter (`gpt-5`, `anthropic/claude-sonnet-5.5`) sind Annahmen; bei „Anbieter hat die Anfrage
  abgelehnt“ im Coach-Einstellungen-Screen das Modell anpassen.
- Kein Senden im Hintergrund bei geschlossener App (siehe oben).

## Farbpalette und iOS-CI (3.11., Koordination)
- **Farbpalette:** (Hell-Werte seit 4.11. entfallen, nur Dunkelmodus.) Schwarz-weiß bleibt die Basis. Neu in `Colors` (Theme.kt), je hell/dunkel:
  `surface` (graue Hervorhebung: markierte Zeile, Chip, Auswahl, Eingabefeld), `surfacePressed` (gedrückt/gezogen),
  `positive` (grün: besser als zuvor, Bestwert, erledigt), `negative` (rot: schlechter, verpasst, destruktive Bestätigung)
  und die blassen Hintergründe `positiveSubtle`/`negativeSubtle` (z. B. Delta-Badge in der Auswertung).
  Werte angelehnt an die iOS-Systemfarben (gut lesbar auf Schwarz bzw. Weiß). Grün/Rot nur für Bedeutung in Daten,
  nie als Deko oder Markenfarbe; Buttons bleiben schwarz-weiß.
- **iOS-CI pausiert:** macOS-Minuten kosten 10x und waren am 3.11. rund 90 % der Actions-Kosten. `ios.yml` läuft nur noch
  per „Run workflow“. Ersatz: Die Android-CI kompiliert `iosMain` (`:shared:compileKotlinIosSimulatorArm64`, Linux).
  Gemergt wird bei grüner Android-CI; die iOS-CI vor einem iOS-Release oder nach größeren iOS-Änderungen von Hand starten.

## Foto-Tagebuch wie BeReal (Session M, 3.11.)
- **Wochenstart nach Sprache:** DE Montag, EN Sonntag (US-Konvention); die Spec nennt „Mo–So“, die englische Fassung folgt der Sprache wie gefordert.
- **Serie:** Wochen in Folge mit mindestens einem Training. Die laufende Woche ohne Training bricht die Serie nicht, bis sie vorbei ist.
- **Tippziel:** Tag mit Foto öffnet das Vollbild beim ältesten Foto des Tages; Tag nur mit Training öffnet das Training im Verlauf.
- **Vergleichen** bleibt erhalten und nimmt im Raster das letzte Foto des Tages.
- **Bestwerte im Vollbild** werden mit `WorkoutRepository.summary` nachgeladen (max. 3 Zeilen), kein neues Schema.
- **Teilen eines Fotos** gab es im Viewer bisher nicht; neu über `PlatformShare.shareBytes` (JPEG).

## Workout minimieren (Session L, 3.11.)
- **Minimiert = reiner UI-Zustand, kein Controller-Zustand.** `WorkoutRoute` ist ein normales Navigationsziel; „minimiert“ heißt: das Training läuft (`WorkoutController.state.workout != null`), aber `WorkoutRoute` ist nicht oben. Die Leiste (`MiniWorkoutBar`) sitzt im `AppScaffold` über der Tab-Leiste und erscheint damit auf allen Hauptscreens. Der WorkoutController, Pausentimer, Benachrichtigungen und Haptik sind unverändert.
- Minimieren-Knopf (Pfeil nach unten) und System-Zurück machen dasselbe: `popBackStack` auf den Screen darunter (Standardverhalten der Navigation, kein eigener BackHandler nötig). Beenden/Verwerfen bleiben die bestehenden Doppeltipp-Aktionen im Training.
- Tipp oder Wischen nach oben auf die Leiste → `NavHostController.openWorkout()`: zurück zum Training, falls auf dem Stack, sonst oben drauf (kein doppelter Eintrag).
- **Benachrichtigung:** Die Tap-Intents (Pausentimer läuft / Pause vorbei) tragen `EXTRA_OPEN_WORKOUT`; `MainActivity` zählt `AppDependencies.openWorkoutRequests` hoch, die UI öffnet daraufhin das maximierte Training. Nach Prozess-Neustart stellt die bestehende Wiederherstellung das Training ohnehin maximiert wieder her.
- **Zweites Training:** Läuft eins, zeigt der Start-Tab statt „Training starten“ die Wahl „Zum laufenden Training“ / „Laufendes beenden“ (Bestätigungsmuster, Button wechselt Text). „Beenden“ heißt wie sonst auch: Training abschließen und speichern (Beenden-Flow), nicht verwerfen – umkehrbar und ohne Datenverlust. Danach startet man das neue Training vom Start-Tab.


- **Lizenz (Entscheidung Fabian, 3.11.):** Apache-2.0 ersetzt durch „Alle Rechte vorbehalten“. Der Code darf öffentlich
  einsehbar sein (z. B. damit die CI für ein öffentliches Repo kostenlos läuft), aber ohne Erlaubnis nicht kopiert,
  verändert oder weiterverwendet werden. Drittbestandteile behalten ihre Lizenzen (Workout-Guide-Grafiken CC BY-SA 4.0,
  Inter OFL, Bibliotheken). Backend `package.json`: `"license": "UNLICENSED"` (npm-Konvention für „nicht lizenziert“).
  Was vor dem Wechsel unter Apache-2.0 veröffentlicht wurde, bleibt unter Apache-2.0 nutzbar. Das Repo war bis dahin
  privat, deshalb spielt das praktisch keine Rolle.

## Tab „Training“: Workouts statt Vorlagen (3.11., Session O)

Fabians Feedback: „Der erste Tab soll Training heißen … das mit den Templates verstehe ich nicht … Alles soll gespeichert werden.“

- **Begriff:** In der UI heißt ein gespeicherter Plan überall „Workout“ (DE und EN), eine laufende/abgeschlossene Einheit weiter „Training“ (DE) / „workout“ (EN). Intern bleiben `workout_template`, `Template`, `TemplateRepository` – keine Schemaänderung, keine Migration.
- **Tab 1:** DE „Training“, EN „Workouts“ (im Englischen liest sich der Plural als Liste besser), Icon Blitz. Der Tab zeigt nur noch: laufendes Training oben („Läuft gerade“ + „Training fortsetzen“ als PrimaryButton), Liste der Workouts (Name, Übungen, „zuletzt vor N Tagen“), Coach-Tipps, Duo (Coach-Tipps und Duo entfernt 3.11., Fabian). Begrüßung und Gewicht sind raus (Gewicht steht im Profil). Der Übungen-Tab zeigt nur noch Übungen.
- **„+“ oben rechts** öffnet eine eigene Seite (kein Bottom-Sheet, das Designsystem hat keins) mit „Workout erstellen“ (Editor, speichert, startet nicht) und „Leer starten“. Darunter unter „Weitere Wege“ Import, KI-Prompt und Beispiel-Workouts.
- **Workout-Seite:** Tipp auf ein Workout öffnet eine Ansicht mit „Starten“ (PrimaryButton), „Bearbeiten“, „Teilen“. Import, Chat-Eingang, Coach und „Als Workout speichern“ im Verlauf führen ebenfalls dorthin statt in den Editor.
- **Automatisch übernehmen (`WorkoutPlan`, `TemplateRepository.adopt`):** Endet ein Training (Beenden *und* Verwerfen), übernimmt sein Workout Übungsliste, Reihenfolge, Satzanzahl, Pausen und Namen – ohne Dialog. Ein leer gestartetes Training mit mindestens einer Übung wird ein neues Workout; die Sitzung bekommt dessen ID (für „zuletzt“, Coach). Zielwerte werden *nicht* aus den Ist-Werten kopiert: Sätze, die bleiben, behalten ihre geplanten Werte, neue Sätze/Übungen bekommen leere Ziele, die beim nächsten Start wie bisher aus der letzten Ausführung kommen.
- **Coach:** Weil Ist-Werte nicht in die Ziele geschrieben werden, bleiben Zielwerte, die der Coach gesetzt hat, erhalten. Die Übernahme läuft beim Beenden (vor der Coach-Auswertung); ein angenommener Coach-Plan ändert danach die Werte im selben Workout und gewinnt damit für das nächste Mal. Übungsnotizen aus dem Training bleiben beim Training (sie sind oft tagesbezogen), das Workout behält seine eigenen.
- **Name:** Leer gestartet heißt das Training „Workout vom 3. Nov.“; im Beenden-Flow steht der Name als Eingabefeld oben, eine Änderung benennt Training und Workout um.
- **Schutzregeln:** Ein Training ohne Übungen leert nie ein Workout und legt keins an. Ein Workout, das während des Trainings gelöscht wurde, wird nicht wiederhergestellt. Gelöschte Workouts bleiben im Verlauf (Sitzungen speichern ihre Werte selbst, `templateId` ohne Fremdschlüssel). „Alle Daten löschen“ verwirft ohne Übernahme. Schlägt die Übernahme fehl, wird trotzdem beendet.
- **Sicherung:** Das laufende Training wird schon nach jeder Änderung gespeichert (Phase 2); Verwerfen geht nur über das Bestätigungsmuster.
- **Übungen ohne eigenen Tab** (Fabian: „Der Übungskatalog soll auch nicht mit in der Tableiste sein.“): Tab-Leiste = Training, Verlauf, Fotos, Profil. Der Katalog (eigene Übungen, Suche, Fortschritt je Übung) öffnet über das Hantel-Icon oben im Training-Tab neben „+“ – dort, wo man trainiert, statt versteckt im Profil – und weiterhin über jeden Übungs-Picker.

### Zur Prüfung durch Fabian
- Auch **Verwerfen** übernimmt die Struktur (ein leer gestartetes, verworfenes Training mit Übungen erscheint als Workout). Begründung: „nichts darf verloren gehen“. Wer das nicht will, löscht das Workout. Alternative: beim Verwerfen nichts übernehmen.
- Benennt man das Training um (im Training oder beim Beenden), heißt auch das Workout so.

## KI-Kontext, Auswertung je Übung, Gemini (3.11., Session N)
- **Ein Profil-Block für alle KI-Wege:** `aiProfileFacts` (domain/ModelContext.kt) baut „Name · Alter · Körpergewicht (Stand …)“ und wird überall verwendet: Coach (eigener Key und Fuel, im dynamischen System-Block), „Kontext kopieren“, KI-Vorlage (Teilen/Erstellen), Gem-/GPT-Anleitung (als „Über mich“-Zeile oben) und der Chat-Sync. Fehlende Werte fallen weg, ein unplausibles Alter (außerhalb 13–110) auch. Die Beschriftung folgt der App-Sprache, damit ein eingefügter deutscher Prompt deutsch bleibt. Der Coach-Prompt bleibt englisch. Diese Texte sind KI-Eingaben, keine UI, deshalb stehen sie wie die übrigen Prompt-Regeln im Code und nicht in Strings.kt.
- **MCP:** Die App lädt die Zeile als `summary` im Profil-Dokument hoch. Das Backend stellt sie als `user` an den Anfang **jeder** Tool-Antwort (bei Foto-Antworten als erste Textzeile). Bei älteren App-Versionen ohne `summary` baut das Backend die Zeile selbst aus Name, Geburtsjahr und Gewicht. Es gibt keine eigene Kontext-Ressource. Die Tool-Antwort ist der Weg, der in Claude, ChatGPT und Gemini sicher ankommt.
- **Auswertung je Übung ohne Schemaänderung:** Die Zahlen (bester Satz, Sätze, Volumen, e1RM, Wdh., Zeit, Deltas, Einordnung nach Zahlen) rechnet die App beim Anzeigen deterministisch aus der Datenbank (`ExerciseReviews.compare`, „letztes Mal“ = letztes beendetes Training davor mit derselben Übung). Gespeichert wird nur die KI-Antwort (`exercise_reviews` im bestehenden `reviewJson`). Room bleibt deshalb auf dem bisherigen Stand, v6 wird nicht gebraucht.
- **Einordnung:** Bei Gewichtsübungen entscheidet das e1RM (Epley, 1–12 Wdh.), sonst Wdh. bzw. Sekunden. „Gleich“ heißt bei Gewichtsübungen unter 1 % Unterschied, sonst weniger als 1 Wdh. bzw. 1 s. Ist das Top-Ergebnis gleich, zählen mehr als ±5 % Volumen als besser bzw. schlechter. Die KI startet mit diesem Wert und darf ihn begründet korrigieren. Angezeigt wird die KI-Einordnung, ohne KI die lokale.
- **Schema:** `exercise_reviews: [{exercise_id, verdict: better|same|worse, comment, next: Satz|null}]` ist im strikten Schema Pflicht. `summary` ist bei Trainings nur noch 1–2 Sätze, positives/improvements/tips höchstens 2 übergreifende Punkte. Alte Auswertungen ohne das Feld zeigen trotzdem die lokalen Zahlen je Übung.
- **Gemini über den OpenAI-kompatiblen Endpunkt** (`https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`, Key als Bearer) statt der nativen API: derselbe Code wie OpenAI/OpenRouter (Bilder als `image_url`-Data-URL, Structured Output über `response_format: json_schema`). Gemini kann laut Doku `anyOf`, `enum`, `additionalProperties` und `["…","null"]`, unser strenges Schema passt also. Fällt Structured Output doch mit 400 aus, wiederholt der Coach die Anfrage wie bei OpenRouter ohne Schema, und der tolerante Parser liest die Antwort. Die Key-Prüfung läuft über `GET …/openai/models`, kostet also keine Tokens. Google meldet einen ungültigen Key mit **400** `API_KEY_INVALID`: Bei der Key-Prüfung zählt 400 als ungültig, beim Coach wird 400 mit API_KEY_INVALID zu AUTH. `max_tokens` liegt bei 16 000, weil Gemini die Denk-Tokens mitzählt.
- **Standardmodell `gemini-3.6-flash`:** In Googles Modellliste (3.11.) die neueste Flash-Version mit dem Badge „Stable“. `gemini-3.8-flash` steht dort als „New“ ohne GA-Angabe. Das Modell lässt sich in den Coach-Einstellungen ändern.

### Zur Prüfung durch Fabian
- Standardmodell Gemini: `gemini-3.6-flash` (stabil) oder schon `gemini-3.8-flash`?
- Gem-/GPT-Anleitung: Die „Über mich“-Zeile steht fest in der Anleitung und veraltet, wenn sich das Gewicht ändert. Aktuell bleibt sie über „Kontext kopieren“.

- **iOS-CI wieder an (3.11., Fabian):** Seit das Repo öffentlich ist, sind auch macOS-Runner kostenlos. `ios.yml` läuft
  wieder für PRs nach main, Pushes auf main und per Hand; Merge-Bedingung ist wieder Android- und iOS-CI grün.
  Der zusätzliche iOS-Kompilierschritt in der Android-CI bleibt als schnelle Vorprüfung.
- **Lizenz MIT (3.11., Fabian):** „Alle Rechte vorbehalten“ wieder aufgehoben. Jeder soll ohne Fabians Erlaubnis mit dem
  Code arbeiten können, deshalb MIT: kurz, bekannt, erlaubt Nutzung, Änderung und Weitergabe, auch kommerziell.
  Drittbestandteile behalten ihre Lizenzen (Workout-Guide-Grafiken CC BY-SA 4.0, Inter OFL, Bibliotheken).
  Beiträge laufen über Fork und Pull Request (`CONTRIBUTING.md`); Fabian bzw. die Koordination entscheidet über den
  Merge. Direkten Schreibzugriff für Fremde gibt es nicht, weil Workflows sonst an die Signatur-Secrets kämen.
- **Fremde PRs und Secrets:** GitHub gibt Workflows aus Fork-PRs keine Secrets. Der signierte Release-Job läuft ohnehin
  nur auf main. Fork-PRs bauen und testen also ohne Schlüssel.



## Session K (3.11.) – Katalog ohne free-exercise-db, Übungen per KI, eigene Übungen im Workout

- **free-exercise-db entfernt** (Fabian: Fotos passen nicht ins Design). Der Katalog besteht nur noch aus Workout Guide
  (302 Übungen, SVGs, CC BY-SA 4.0 auf Basis von Everkinetic). Import-Skript, `exercises.json` und
  `backend/src/catalog.gen.json` neu erzeugt. Weg sind auch „Alle Bilder offline laden“, das Laden von Übungsbildern
  über HTTP (Coil-Ktor-Fetcher samt Abhängigkeit `coil-network-ktor3`) und der Attributionshinweis im „Über“-Screen.
- **Migration v5 (manuell):** Benutzte `fedb:*`-Übungen (Vorlage, Training oder als ersetzte Übung) **behalten ihre ID**
  und werden zu eigenen Übungen (`source = CUSTOM`, kein Bild → Initialen-Kachel). Kein Umschlüsseln: kein
  Fremdschlüssel ändert sich, Backups, Coach-Texte und alte Teilen-Links mit `fedb:`-ID passen weiter. Unbenutzte
  `fedb:*` werden gelöscht; der neue Katalog enthält sie nicht, also holt das Seeding sie nicht zurück. Dazu die neue
  Spalte `exercise.note`. `ExerciseSource.FREE_DB` bleibt nur, damit alte Backups lesbar sind: beim Wiederherstellen
  werden benutzte FREE_DB-Übungen zu eigenen (ohne Bild), unbenutzte übersprungen.
- **Eigene Übungen sind vollständiger:** Haupt- und Hilfsmuskeln (mehrere), Equipment, Erfassungsart, Standard-Pause,
  Ausführungsnotiz. Die Notiz steht in der Übungsdetailseite.
- **„Mit KI ausfüllen“:** nutzt den Zugang des Coaches (`CoachService.ai`: gleicher Provider, eigener Key oder Fuel,
  gleiches Modell). Neue Provider, die in die Coach-Transporte eingehängt werden (Session N: Gemini), gehen automatisch
  mit, wenn sie `CoachPrompt.outputSchema`/`outputSchemaName`/`outputMaxTokens` statt fest `CoachSchema` verwenden.
  Structured Output mit Enums aus den Muskel-/Equipment-Schlüsseln der App; der Parser verwirft trotzdem Unbekanntes,
  begrenzt Muskeln und rundet die Pause auf 15 s. Bei HTTP 400 mit Structured Output einmal ohne (wie beim Coach).
  Ohne KI-Zugang: kurzer Hinweis, manuelle Eingabe.
- **„Meintest du …?“:** Beim Anlegen zeigt das Formular bis zu drei Treffer der normalen Übungssuche für den
  eingegebenen Namen (ab 3 Zeichen). Antippen öffnet die vorhandene Übung bzw. nimmt sie im Picker.
- **Neue Übung aus dem Picker** (Vorlagen-Editor und Workout-Modus): letzte Zeile „„…“ als neue Übung anlegen“ öffnet
  dasselbe Formular. Nach dem Speichern kommt die Übung über `PickedExercises.created` in den Picker zurück, der sie
  sofort (zusammen mit schon gewählten) an den Aufrufer gibt. WorkoutScreen und WorkoutRoute bleiben unverändert
  (konfliktarm zu Session L); das Training läuft im App-weiten Controller weiter.
- **Teilen eigener Übungen:** `custom[]` bekommt `primary`, `secondary`, `restSec`, `note`; `muscle` wird für alte
  App-Versionen weiter geschrieben (abwärtskompatibel, Format-Version bleibt 1). Beim Import wird eine gleichnamige
  eigene Übung (oder eine Katalogübung mit genau dem Namen) wiederverwendet, sonst eine vollständige eigene Übung angelegt.

### Zur Prüfung durch Fabian
- Die KI-Anfrage beim Ausfüllen kostet mit Fuel einen kleinen Betrag (eine kurze Sonnet-Anfrage, max. 1000 Tokens).
- Ehemalige free-exercise-db-Übungen, die du benutzt hast, heißen weiter englisch (z. B. „Barbell Curl“) und haben
  keine Muskeln außer den importierten; du kannst sie wie jede eigene Übung umbenennen.

## Tab „Coach“ mit Chat und sichtbaren Prompts (3.11., Session P)
- **Tab-Leiste:** Training, Verlauf, Coach, Fotos, Profil. Coach-Icon: Sprechblase mit zwei Textzeilen (`Icon.Coach`). Die bisherige Route `CoachRoute` ist jetzt der Tab; die alte Coach-Startseite bleibt als „Alle Auswertungen“ (`CoachReviewsRoute`).
- **Aufbau:** KI-Zugang (Statuszeile) → Auswertungen (letzte, „Letztes Training auswerten“, „Fortschritt einschätzen“, alle, Gedächtnis mit Vorschau, Einstellungen) → Chat → Prompts & Vorlagen → KI-Chat-Verbindungen. Der Chat steht als Eingabe mit der letzten Unterhaltung im Tab; die Unterhaltung selbst läuft im eigenen Chat-Screen (`CoachChatRoute`), weil ein Chat mit Tastatur und Verlauf in einer scrollenden Seite mit anderen Abschnitten nicht sauber bedienbar ist.
- **Profil:** nur noch „Coach & KI“ (Verweis auf den Tab) und Fuel (Guthaben gehört zum Konto). Verbinden/Ändern/Trennen des Keys liegt im Screen „KI-Zugang“ (früher „KI-Coach“), Trennen jetzt mit Bestätigungsmuster.
- **Chat-Transport:** dieselben Transports wie der Coach (eigener Key: Anthropic, OpenAI, OpenRouter, Gemini; oder Fuel) mit Verlauf (`CoachPrompt.history`, die letzten 20 Nachrichten). Kein Streaming: Fuel liefert die Antwort als Ganzes (inkl. Kosten), und Structured Output lässt sich erst am Ende sicher lesen. Stattdessen „Coach schreibt …“.
- **Workout-Vorschläge:** Structured Output `{"reply": "...", "workout": ""}`; `workout` ist ein **String** mit JSON im Teilen-Format v1 (leer = kein Vorschlag). Ein String funktioniert im Strict-Modus aller Anbieter, ein verschachteltes optionales Objekt nicht. Fallback ohne Structured Output (400 bei manchen OpenRouter-Modellen): Text mit ```overfit-Block. „Workout übernehmen“ legt das JSON in die `ImportInbox`, damit öffnet sich dieselbe Import-Vorschau wie bei einem Link.
- **System-Prompt:** Coach-Anweisungen (eigene oder Standard) + Chat-Regeln + Beispiel + Übungsliste (statisch, cachebar), dahinter Profilblock (`aiProfileFacts`, Sprache, Einheiten, heute) und der Stand wie bei „Kontext kopieren“ ohne dessen Antwortanweisung. „Kontext kopieren“ listet jetzt auch die gespeicherten Workouts.
- **Room v6:** `coach_chat` und `coach_chat_message` (Fremdschlüssel mit Cascade), AutoMigration 5→6, Migrationstest. Antworten, die beim Beenden der App noch liefen, werden beim nächsten Start „fehlgeschlagen“ mit „Erneut versuchen“. Chats sind im Backup (Schlüssel `coachChat`) und werden bei „Alle Daten löschen“ gelöscht.
- **Demo ohne KI-Zugang:** feste Antwort mit Beispiel-Workout (Bankdrücken, Rudern, Kniebeuge), markiert als Demo, geht nie als Verlauf an eine KI.
- **Fotos:** nur explizit über „Foto anhängen“ (bis zu 3 der letzten 12 Fotos), unabhängig vom Schalter „Körperfotos senden“ der Auswertung, weil der Nutzer hier jedes Foto selbst wählt.
- **Prompts sichtbar:** gemeinsame Komponente `PromptView` (Titel, Hinweis, Text in grauer Box, „Mehr/Weniger“, „Kopieren“ → „Kopiert“, optional „Im Chat verwenden“). Genutzt im Coach-Tab, im Import („KI-Prompt kopieren“) und bei den KI-Chat-Verbindungen (Kontext, Gem-Anleitung). Neue Prompts: Training auswerten lassen, Fortschritt der letzten 8 Wochen, Nächste Woche planen, Plateau durchbrechen (jeweils Kontext + Frage).

### Zur Prüfung durch Fabian
- Der Chat-Verlauf bleibt unbegrenzt lokal gespeichert; im Tab stehen die letzten 5 früheren Chats. Falls gewünscht: Alles-löschen für Chats oder automatisches Aufräumen.
- Fuel-Kosten stehen je Antwort klein darunter („Kosten: 0,0042 €“). Bei eigenem Key zeigen wir keine Kosten (die Preise kennt nur der Anbieter).

## Teilen und Import verbessern, Beispiel-Workouts standardmäßig (3.11., Session Q)

**.overfit-Dateien öffnen.** Import-Screen hat „Datei auswählen“ (vorhandenes `rememberFilePicker`: Android `OpenDocument`, iOS
UIDocumentPicker). Android-Manifest: zusätzlich zu den VIEW-Filtern (Typ `application/x-overfit`, Pfadmuster `*.overfit`) jetzt
VIEW für `application/octet-stream` (WhatsApp und Downloads liefern oft diesen Typ ohne Endung im `content://`-Pfad) und
SEND für `application/x-overfit` und `application/octet-stream` („Teilen mit …“). Overfit prüft den Inhalt selbst und
meldet „kein Overfit-Training“, falls die Datei etwas anderes ist (Preis: Overfit erscheint bei jeder unbekannten Datei in der
Auswahl). `text/plain` wird bewusst nicht registriert (Overfit würde in jedem Teilen-Dialog stehen; Links öffnen über VIEW). Das
Lesen steckt in `IncomingShare.textOf` (Robolectric-Test `IncomingShareTest`). iOS hatte UTI und Document Type schon
(`iosApp/project.yml`). Backups sind `.zip`: Wählt man eines im Import, erscheint „Das ist ein Backup … unter Profil → Backup“.

**Schalter „Gewichte und Wiederholungen mitsenden“.** Vorher „Mit Ist-Werten“ (Teilen eines abgeschlossenen Trainings). Jetzt klar
benannt, ein Satz Erklärung mit Beispiel darunter (an: „Der Empfänger sieht … z. B. 80 kg × 8“, aus: „Nur Übungen und Satzanzahl“) und
eine Vorschau „Das wird geteilt: 5 Übungen · 18 Sätze“ mit Beispielwert (auch bei Workouts ohne Schalter). Die Vorschau zeigt die
Zahlen aus dem tatsächlich geteilten Inhalt.

**QR-Größe: gemessen, entschieden.** Modulzahl je Seite (ohne Ruhezone), Beispiel-Workouts:

| | Link in QR (JSON → Deflate → Base64url, Byte-Modus, ECC M) | `OF1:` (kompakt, Base45, Alphanumeric, ECC L) |
|---|---|---|
| Push | 93 × 93 (617 Zeichen) | 57 × 57 (367 Zeichen) |
| Pull | 89 × 89 (544) | 53 × 53 (333) |
| Legs | 85 × 85 (496) | 49 × 49 (262) |

Beitrag der einzelnen Maßnahmen: (1) ECC L statt M (Bildschirm ist sauber, Fehlerkorrektur 7 % genügt), (2) Alphanumeric-Modus
(5,5 statt 8 Bit je Zeichen) über Base45, (3) Binärform mit Katalog-ID als 24-Bit-Hash (statt `"wg:bench-press"` je Übung),
Satz-Gruppen („4 × 80 kg × 8“) und Standardwerten weggelassen, (4) Raw Deflate nur, wenn es kleiner ist. Regressionstest
`QrSizeTest` (≤ 57 Module). Verworfen: eigenes Spotify-artiges Strichcode-Format. Das Muster ist nur mit eigener Erkennung
(Kamera, Entzerrung, Fehlerkorrektur) lesbar, die auf iOS fehlt und auf Android von ZXing nicht kommt; ein QR in Version 1
(21 × 21) über den Kurz-Code erreicht dieselbe Kleinheit ohne eigenes Format.
Optik: runde Module (Finder-Muster bleiben solide, damit jeder Scanner sie sicher findet), 200 dp statt 320 dp breit,
weißer Grund mit Ruhezone 3 Module + abgerundeter Karte. Die Komponente `QrView(text)` ist generisch (Spot nutzt sie weiter, Duo später).
Beim Prüfen gegen ZXing fand sich, dass die Maskenwahl des Generators schwächer als ISO war (manche Symbole ließen sich nicht lesen):
Penalty-Regeln 1–4 sind jetzt wie in ZXing, der Test `QrCompareTest` vergleicht Symbol und Maskenwahl mit dem ZXing-Encoder.

**Kurz-Code über das Backend.** Vorgebaut, soweit es ohne Backend geht: `ShortCodeService` (`NoShortCodes` als Standard),
`qrTextFor(...)` (Kurz-Code, sonst voller Inhalt), `OFS:<ID>` im Import erkannt, Vertrag in docs/backend-api.md (`POST
/v1/share/short`, `GET /v1/share/short/<id>`).

**Beispiel-Workouts.** `AppData.seedSamplesOnce()` legt Push/Pull/Legs einmal an (Flag `samples_seeded_v1` im KeyValueStore, nach
erfolgreichem Anlegen gesetzt), beim Erststart und einmalig bei bestehenden Installationen (dort ohne Duplikate, per Name).
Gelöschte kommen nicht wieder, „Beispiel-Workouts hinzufügen“ (Leerzustand und Profil) bringt sie auf Wunsch zurück. „Alle Daten
löschen“ setzt das Flag zurück (wie ein frischer Start, sie erscheinen beim nächsten App-Start). Läuft im App-Scope.

### Zur Prüfung durch Fabian
- **iPhone-Kamera öffnet den neuen QR nicht.** `OF1:` ist Text, kein Link. Auf iOS gibt es keinen In-App-Scanner (nur Android), das
  Teilen per Link/Datei bleibt. Alternative wäre ein URL-QR `OVERFIT://T#<Base32>` (ca. 3–5 Module größer, iPhone-Kamera öffnet ihn
  vermutlich); umkehrbar in `qrTextFor`. Auf iOS ist ein In-App-Scanner (AVFoundation) der saubere Weg.
- Overfit erscheint wegen `application/octet-stream` in der „Öffnen mit“-Auswahl für unbekannte Dateien.
- Bestehende Installationen bekommen Push/Pull/Legs einmalig, auch wenn jemand sie früher bewusst gelöscht hat.

## UI-Feinschliff (Session R, 3.11.)
**Mängelliste aus den Screenshots** (`docs/screenshots/before/`, Test `ScreenshotTest`, hell + dunkel):
1. Textaktionen („Beenden“, „Bearbeiten“, „Teilen“, „Satz hinzufügen“, „Ersetzen“) sahen aus wie Fließtext, nicht klickbar.
2. Klickbare Zeilen (Workouts, Coach, Profil, Teilen) ohne Chevron und ohne Pressed-State.
3. Segmente und Chips nur mit Haarlinie, Chips ohne Fläche.
4. Workout-Ansicht: Minimieren-Zeile + 34-sp-Namensfeld + Beenden + Zeile mit Uhr + Fuß mit 56-dp-Button; oben zu viel Luft.
5. Satz-Haken: leerer Kreis mit Haarlinie, 40 dp; erledigt nur schwarz (keine Bedeutung).
6. Bestätigungsmuster nur Textwechsel, ohne Farbe.
7. Diary: Raster zeigte nur Monate mit Daten.

**Umsetzung:**
- Buttons: `PrimaryButton` (48 dp, inverse), `SecondaryButton` (`surface`), `TertiaryButton`/`TextButton` (unterstrichen, ≥ 44 dp), `DestructiveButton`, `ConfirmButton` (Textwechsel + Farbe: rot `negative` bei Zerstörung, grün `positive` bei Beenden/Speichern; 4 s Zurücksetzen). Kompakt = 40 dp. `IconButton` und `BackHeader` 44 dp. API rückwärtskompatibel (`PrimaryButton` hat nur optionale neue Parameter; `TextButton` ist jetzt standardmäßig `Default`-Ton statt `Muted`).
- `ListRow` mit `onClick` bekommt automatisch einen Chevron und beim Drücken eine `surface`-Fläche (`pressableRow`); `WorkoutRow` ebenso. Segmente und Chips liegen auf `surface`.
- Workout-Ansicht: `Screen(compact = true)`, ein Kopf (Minimieren, Name + Uhr, „Beenden“ als kompakter grüner Bestätigungsbutton; während der Bestätigung weicht der Name), Fuß mit engerem Abstand. Satz abhaken: 48 dp, erledigt = `positive`. „Training verwerfen“ ist ein `ConfirmButton` (rot).
- Bestätigungsmuster überall auf `ConfirmButton` umgestellt (Workout beenden/verwerfen, Löschen von Übung/Training/Workout, Reset, KI-Chat trennen, Coach-Gedächtnis, laufendes Training beenden); Foto löschen im Vollbild rot.
- Diary: `DiaryCalendar.months` liefert mindestens die letzten 3 Monate plus den aktuellen und alle leeren Monate dazwischen bis zum ersten Training (Test `monthsShowAtLeastThreeEmptyMonthsBack`). Die Liste ist lazy; weiter zurück gibt es nur, wenn Daten dort liegen.
- Wackel-Tests `TrainingTabTest` und `CustomExerciseFlowTest`: Wartezeiten 10 → 20 s (Timing, nicht abgeschaltet).
- Nicht angefasst (Session Q arbeitet dort): `ui/share`.

### Zur Prüfung durch Fabian
- Unterstrichene Textlinks vs. eigene Pill-Buttons: Seiten-Aktionen (Bearbeiten/Teilen) sind jetzt graue Pills, Inline-Aktionen in Listen unterstrichen. Leicht umkehrbar in `Components.kt`.

## iPhone testbar machen (Session U, 3.11.)
- **Sehen statt raten:** Die iOS-CI startet die echte App im Simulator (neuestes iPhone Pro, neuestes iOS des Runners)
  und fährt sie per XCUITest. Compose Multiplatform legt die Semantik als Accessibility-Elemente offen, die Tests finden
  Texte und Content-Descriptions über ihr Label – dieselben Schritte wie `ScreenshotTest` auf Android. Screenshots als
  Artefakt `ios-screenshots` und in `docs/screenshots/ios/`.
- **UI-Test-Modus über Launch-Argumente** (`-overfitUITest`, `-overfitTheme`, `-overfitSkipOnboarding`,
  `-overfitKeepData`) statt Deep-Links je Screen: kein Eingriff in die Navigation (App.kt), eigene UserDefaults-Suite,
  eigene Datenbankdatei und eigener Keychain-Eintrag, die beim Start geleert werden. Ein normaler Start ist unberührt.
- **Bundle-ID `app.overfit`** wie die Android-applicationId. Ist sie im Apple-Portal vergeben, setzt die Variable
  `IOS_BUNDLE_ID` eine andere, ohne Codeänderung.
- **TestFlight:** automatische Signierung mit App-Store-Connect-API-Key (`-allowProvisioningUpdates`), Export mit
  `destination=upload` (Apples eigener Upload, kein altool/fastlane). Schlüssel-Rolle **Admin statt App Manager**:
  Team-API-Keys mit App Manager dürfen keine Cloud-verwalteten Verteilungszertifikate anlegen
  („Cloud signing permission error“); ohne Zertifikat schlägt die automatische Signierung fehl. Ohne Secrets bricht der
  Workflow im ersten Schritt mit Liste und Anleitung ab.
- **Statusleiste:** Das App-Theme setzt `overrideUserInterfaceStyle` des Fensters. Damit passen Statusleiste,
  Tastatur, Share-Sheet und Picker zur App, auch wenn iOS selbst hell und die App dunkel ist.
- **App-Icon:** dieselbe Hantel wie das Android-Launcher-Icon (weiß auf schwarz, 1024 px). Launch-Screen: einfarbig,
  weiß bzw. im Dunkelmodus schwarz (Asset-Farbe `LaunchBackground`).

### Paritäts-Audit Android ↔ iOS
| Bereich | Android | iOS | Stand |
|---|---|---|---|
| `expect` `prepareJpeg` | Bitmap, EXIF-Drehung | UIGraphicsImageRenderer (wendet Orientierung an) | gleich |
| `expect` `rememberPhotoPicker` | Kamera-Intent, Photo Picker | UIImagePickerController (Kamera, Mediathek ohne Berechtigung) | gleich |
| `expect` `rememberFilePicker` | SAF | UIDocumentPicker (Kopie) | gleich |
| `expect` `QrScanner` | CameraX + ZXing | **neu:** AVFoundation (`AVCaptureMetadataOutput`, iOS 16) | Lücke geschlossen |
| `expect` `deviceDefaults` | Locale | `NSLocale.preferredLanguages` → Sprache folgt dem System | gleich |
| `expect` `createHttpClient` | OkHttp | Darwin | gleich |
| `expect` Room-Konstruktor/Builder | System-SQLite | gebündeltes SQLite, Documents | gleich |
| `KeyValueStore` / `SecretStore` | SharedPreferences / Keystore | UserDefaults / Keychain (nur dieses Gerät) | gleich |
| `Haptics` | Vibrator | UIFeedbackGenerator | gleich |
| `RestAlerts` | Exakter Alarm + Benachrichtigung mit Aktionen | `UNTimeIntervalNotificationTrigger`, Ton (iOS vibriert nur mit Ton) | +15 s/Überspringen in der Mitteilung fehlen auf iOS (bräuchte Notification-Actions + Hintergrund-Logik) |
| Tipp auf Pausen-Mitteilung | Intent → maximiertes Training | **neu:** `UNUserNotificationCenterDelegate` → maximiertes Training | Lücke geschlossen |
| Berechtigung Mitteilungen | beim ersten Trainingsstart | beim ersten Trainingsstart | gleich |
| Training im Hintergrund/nach Beenden | Zustand in DB, Timer aus Zeitstempel | gleich; XCUITest `testWorkoutSurvivesRelaunch` | gleich |
| `PlatformShare` | Share-Sheet, FileProvider | UIActivityViewController (inkl. AirDrop, „In Dateien sichern“) | gleich |
| `overfit://`-Links | Intent-Filter | `CFBundleURLTypes` + `onOpenURL` | gleich |
| `.overfit`-Dateien | VIEW/SEND-Filter | Dokumenttyp + exportierte UTI, `onOpenURL` (Kopie, `LSSupportsOpeningDocumentsInPlace = NO`) | gleich |
| https-Link `bigfire3.github.io/overfit/t` | Intent-Filter (ohne Verifizierung) | Web-Seite mit „In Overfit öffnen“ | Universal Links brauchen eine eigene Domain (AASA im Domain-Root) |
| App Attest / Play Integrity | Play Integrity | DCAppAttestService (TestFlight/App Store = Produktionsumgebung) | gleich |
| Zurück | System-Zurück | Wisch vom linken Rand (Compose BackHandler/NavHost); XCUITest `testEdgeSwipeBack` | gleich |
| Safe Areas, Tastatur | `safeDrawing`, `imePadding` | dieselben Insets in Compose | per Screenshot geprüft |
| Statusleiste hell/dunkel | Edge-to-Edge, Theme | **neu:** Fenster-Stil folgt dem App-Theme | Lücke geschlossen |
| App-Icon, Launch | adaptives Icon | **neu:** AppIcon + Launch-Farbe | Lücke geschlossen |
| Sprache | System, in der App umschaltbar | gleich | gleich |
## Wackel-Tests nach #39 (Session S, 3.11.)
**Befund** (lokal reproduziert in der Gesamtsuite, etwa jeder zweite Lauf; die Klasse allein bleibt grün; mit Log-Ausgaben und State-Dump beim Timeout):
1. `CustomExerciseFlowTest`: Nach „Speichern“ fand `waitFor("Turkish Get-up Neu")` den Namen sofort im Formular bzw. im Suchfeld des Pickers, obwohl das Speichern (Room-Write im `viewModelScope`, Rückkehr auf dem Main-Looper) noch lief. Die Prüfung danach kam zu früh. Der Umbau auf `waitUntil { state … }` (PR #41) lief in den Timeout, weil eine reine State-Bedingung unter Robolectric den Main-Looper nicht pumpt (erst `fetchSemanticsNodes` synchronisiert). Laut Log kam `onSaved` erst **nach** den 20 s, danach landete die Übung korrekt im Training. Die App hatte hier also keinen Fehler.
2. `WorkoutFlowTest`, `ShareFlowTest` und alle anderen „Starten“-Tests: Der Footer mit „Starten“ war sichtbar, bevor das Workout aus Room geladen war, aber noch `enabled = false`. Ein Klick in diesem Fenster verpuffte (Test hängt nach „Starten“), und der Titel fehlte noch („Oberkörper A … is not displayed“).

**Entscheidung:**
- `WorkoutPlanScreen` zeigt den Footer erst, wenn das Workout geladen ist. Es gibt keinen toten Button mehr, auch nicht für schnelle Finger. Der Inhalt erscheint ohnehin erst dann.
- Das Hinzufügen bzw. Ersetzen aus dem Picker läuft über `WorkoutController.addPicked` im Controller-Scope statt in `rememberCoroutineScope` des Workout-Screens. Das Ergebnis wird ohne Suspend-Punkt aus dem Relay genommen und übergeben, es geht also weder verloren noch wird es doppelt angewendet, wenn der Screen die Komposition verlässt (dieselbe Falle wie in #25).
- Tests warten auf UI, die nur der Zielzustand zeigt (Kopf „1/5 erledigt“), und prüfen danach den State.
- Wartet ein Test auf State oder Daten statt auf Text, nimmt er `waitUntilState` (app/src/test/.../ui/TestWaits.kt): Der Helfer holt vor jeder Prüfung den Semantik-Baum und pumpt damit den Main-Looper. Ein reines `waitUntil { deps.workout.state … }` läuft sonst in den Timeout, sobald die Arbeit auf `Dispatchers.Main` liegt (so geschehen in TrainingTabTest/DayWithOverfitTest, nachdem `addPicked` in den Controller-Scope gezogen war). Alle bestehenden State-Waits sind umgestellt.

## Marke und Claim (Session T)
- **Wortmarke „Overfit“ mit großem O** (Fabian, 3.11.: „Der erste Buchstabe des App-Namens soll groß sein.“). Ersetzt die frühere Entscheidung „immer klein“. Geändert: App-Name (`appName`, Android-Label), „Über/About Overfit“, Fließtexte in den Strings, Store-Texte, README, Web. iOS hatte schon `CFBundleDisplayName: Overfit`. Interne Namen (Paket `app.overfit`, Datei `.overfit`, `overfit://`, Prefs-Keys, Backup-Dateiname) bleiben klein.
- **Claim „personal training for everyone“** in beiden Sprachen auf Englisch als Markenclaim (30 Zeichen = App-Store-Untertitel). Auf Deutsch zusätzlich eine erklärende Zeile („Dein Trainer in der Tasche …“) statt Übersetzung: ein Claim, überall wiedererkennbar, Store-Suche profitiert von englischem Begriff nicht, aber die Marke bleibt einheitlich.
- **Store-Titel** „Overfit: Workout & KI-Coach“ / „Overfit: Workout & AI Coach“ (je 27 Zeichen), Play-Kurzbeschreibung ≤ 80 geprüft.
- **`aiSubtitle`:** „kein Abo nötig“ statt „kein Abo“, damit ein späteres optionales Pro-Abo nicht widerspricht (siehe docs/monetization.md).
- **Onboarding:** Der erste Screen ist die Namensfrage, es gibt keinen Splash und keinen Markentext; nichts geändert (keine Funktionsänderung).

## App-Icon „of“ (Session W)
- **Konzept B, Fabian hat B5 gewählt (Standard):** „of“ in Inter ExtraBold, 58 % der Kante, Laufweite −4 %, unten links (linke Tintenkante bei 19 %, Grundlinie bei 77 % statt 16 %/80 % im Entwurf, damit „o“ und „f“ auch in der Kreismaske klar lesbar bleiben), weiß auf Schwarz. Masken-Vorschauen (Kreis, Squircle, abgerundet, iOS-Radius, Rechteck; dunkel, hell, monochrom) liegen unter `design/icon/previews/` und werden vom Skript mit erzeugt. Die anderen Varianten B1–B4 und B6 bleiben im Skript wählbar.
- **Eine Quelle:** `tools/icon/build_icon.py` liest die Glyphen aus den Inter-OTFs (`tools/icon/fonts/`, OFL-Lizenz liegt dabei), wandelt „of“ in Pfade um (die Schrift steckt nicht im Icon) und erzeugt daraus `design/icon/overfit-icon.svg` (1024²) sowie alle Plattform-Dateien: Android (adaptive Icon: Vordergrund-Vector in der 66-dp-Sicherheitszone, Hintergrund `launcher_background` aus `values/launcher_colors.xml`, Monochrome-Layer für Android-13-Themed-Icons, Splash-Drawable), Play-Store-Icon `docs/store/icon-512.png`, iOS (`AppIcon` 1024 ohne Transparenz plus Dunkel- und Getönt-Variante für iOS 18, Launch-Logo), Web (`favicon.svg/.ico`, `apple-touch-icon.png`, 192/512). Abhängigkeiten nur für das Skript: `pip install fonttools cairosvg pillow`.
- **Variante wechseln:** `python3 tools/icon/build_icon.py --variant B2` (B1–B6, dazu `--theme light` für weißen Grund oder frei `--weight 800 --size 0.5 --tracking -0.03 --dot`), danach die erzeugten Dateien committen. Die Spezifikation der Varianten steht im Dict `VARIANTS` im Skript.
- **Kein Legacy-PNG:** minSdk ist 26, dort gibt es adaptive Icons immer; `mipmap-anydpi-v26` genügt.
- **Android-Splash:** `values-v31` mit schwarzem Grund und kleinem weißem „of“ (`ic_splash`, bewusst immer B1-Schnitt, unabhängig von der Icon-Variante). Vor Android 12 bleibt das schwarze `windowBackground`.
- **iOS Launch Screen:** `UILaunchScreen` mit schwarzem `LaunchBackground` und `LaunchLogo` (weißes „of“). Das ersetzt den Platzhalter aus #43 (dort weißer/schwarzer Grund je nach Modus); bei Konflikten gilt die Version aus dieser Session. Standard-iOS-Icon ist die gewählte Fassung (dunkel), die Dunkel-Variante bleibt immer schwarz/weiß-Schrift, „Getönt“ ist Graustufen auf Schwarz.
- **Über-Screen:** hat kein Logo (nur Text), daher keine Änderung.
- Der Android-Vordergrund wird per `clip-path` auf die sichtbaren 72 dp begrenzt, damit Varianten mit angeschnittener Schrift unter jeder Launcher-Maske wie im Master aussehen.

## Deploy-Knopf und Datenschutz-Garantie für den eigenen Key (Session V, 3.11.)

### Deploy-Workflow (`.github/workflows/backend-deploy.yml`)
- **Ein Knopf, alles idempotent.** `workflow_dispatch` mit `mode` (test | production) und `credit_eur`. Reihenfolge: Voraussetzungen prüfen → Typprüfung und Tests →
  D1 anlegen, falls es fehlt (ID per `wrangler d1 list --json`) → R2-Bucket anlegen (Fehler „already exists“ wird toleriert) → `wrangler.generated.jsonc` erzeugen
  (`scripts/deploy-config.mjs`; die `database_id` steht nie in `wrangler.jsonc`, die erzeugte Datei ist gitignored) → Migrationen `--remote` → Deploy → Secrets
  (`wrangler secret bulk`) → Health und `/v1/config` (Modus muss stimmen) → Zusammenfassung mit Worker-URL.
- **Test und Produktion sind getrennte Instanzen** (`overfit-backend-test`/`overfit-test`/`overfit-chat-photos-test` gegen `overfit-backend`/`overfit`/`overfit-chat-photos`).
  Begründung: „nie versehentlich in Produktion“ lässt sich am sichersten dadurch garantieren, dass Testdaten und Test-Guthaben in einer anderen Datenbank liegen. Preis:
  Beim Wechsel auf Produktion ändert sich die Adresse, `OVERFIT_BACKEND_URL` muss neu gesetzt werden.
- **Secrets:** Pflicht ist nur `ANTHROPIC_API_KEY` (aus GitHub oder schon im Worker). `SPOT_SIGNING_KEY` und `SYNC_ENCRYPTION_KEY` erzeugt der Workflow einmalig zufällig, wenn weder ein
  GitHub-Secret noch ein Worker-Secret existiert (Fabian soll nichts im Terminal erzeugen müssen); bestehende Worker-Secrets werden nie überschrieben, `SYNC_ENCRYPTION_KEY` auch dann nicht.
  Ein separates „OAuth-Geheimnis für MCP“ gibt es im Code nicht (die Anmeldung läuft über Kopplungscode, Tokens liegen gehasht in D1), also kein Secret dafür.
  Optionale Store-Prüfer (`GOOGLE_PLAY_*`, `PLAY_INTEGRITY_*`, `APPLE_*`) werden nur übertragen, wenn gesetzt; sonst Hinweis in der Zusammenfassung. Werte werden im Log maskiert.
- **`OVERFIT_BACKEND_URL`:** `GITHUB_TOKEN` darf keine Repo-Variablen schreiben (dafür gibt es keine Workflow-Berechtigung, nur ein PAT mit „Variables: write“). Ich habe bewusst keinen PAT-Weg
  und keinen Umweg über Release-Assets eingebaut (mehr bewegliche Teile in android.yml und ios.yml, die Session U gerade anfasst). Die Zusammenfassung nennt die URL, den Link
  zur Variablen-Seite und erkennt, wenn die Variable schon stimmt.
- **Automatisches Deploy** bei Änderungen unter `backend/` auf main nur, wenn die Repo-Variable `BACKEND_DEPLOY_MODE` gesetzt ist und die Cloudflare-Secrets existieren (sonst Hinweis, kein Fehler).
  Ohne diese bewusste Variable deployt nie etwas von allein, ein versehentliches Produktions-Deploy ist so ausgeschlossen.

### Testbetrieb (`DEPLOY_MODE=test`)
- Nur der exakte Wert `test` schaltet ihn ein, unabhängig von `DEV_MODE` (der bleibt `false`; die `mock:`-Token der Entwicklung gibt es dort weiter nicht).
- Geräteprüfung entfällt: `testAttestationVerifier` akzeptiert `test:<geräte-id>` (Schlüssel `test-<id>`, also weiter höchstens ein Spot je Gerät). Die App nimmt diesen Weg nur, wenn die
  Geräteprüfung nichts liefert (seitlich installiertes APK) **und** `/v1/config` `mode: "test"` meldet; Produktion lehnt `test:`-Token ab (Test).
- Fuel nur über Test-Guthaben: neues Konto bekommt `TEST_STARTER_MICRO` (1 €), dazu `POST /v1/admin/credit` (Bearer `ADMIN_SECRET`, nur Testbetrieb, ≤ 10 € je Aufruf, „all“ ≤ 100 Konten).
  Der Workflow ruft ihn mit `credit_eur` selbst auf und erzeugt `ADMIN_SECRET` bei jedem Lauf neu, Fabian muss also kein Geheimnis kennen und keine Konto-ID suchen.
  Gesamtdeckel für alles Test-Guthaben: `TEST_CREDIT_CAP_MICRO` = 20 €. Kein Testkauf, weil die App ohne Store keinen Kaufweg hat.
- Niedrige Limits im Testbetrieb: `MAX_TOKENS_CAP` 2048, 5 KI-Anfragen/Minute, 10 Registrierungen/Stunde/IP, 3 Spots je Einladendem, Spot-Budget 10 €.
- Markiert: `/v1/config` → `"mode": "test"`, die App zeigt unter Fuel „Testserver“.

### Garantie: eigener Key = null Anfragen an das Backend
Prüfung des Codes (Start, Vordergrund, Fuel-Konto, Registrierung, `/v1/config`, Coach, Chat, KI-Übung, Teilen):
- `ChatFeature.onForeground`/`onBackground` → `sync.flush()` und `inbox.refresh()` sind ohne eingeschaltete KI-Chat-Verbindung (`enabled`) wirkungslos. Die Chat-Screens laden erst bei `enabled`.
- Coach, Coach-Chat, KI-Übung und Warteschlange sprechen über `Providers`/`CoachTransport` direkt mit dem Anbieter; nur `ProviderId.FUEL` geht über das Backend.
- Teilen: `ShortCodeService` ist in der App noch `NoShortCodes`, der Kurz-Code-QR lädt also nichts hoch (und würde nur bei ausdrücklichem Einschalten laufen).
- **Abweichung 1 (behoben):** Die Profilzeile „Fuel“ rief bei konfigurierter Backend-URL `account()` auf, also schon beim bloßen Öffnen des Profil-Tabs eine Geräteregistrierung und
  einen Kontoabruf, auch mit eigenem Key. Jetzt lädt die Zeile nur, wenn Fuel der gewählte KI-Zugang ist (oder die Demo läuft).
- **Abweichung 2 (behoben):** Der Fuel-Screen lud beim Öffnen sofort Konto und Config. Mit eigenem Key zeigt er jetzt zuerst einen Hinweis („Fuel ist optional … geht nichts über overfit-Server“) und
  erst „Fuel ansehen“ verbindet sich mit dem Server. Spot, Wiederherstellung und Buchungen sind nur von dort erreichbar.
- Test: `OwnKeyPrivacyTest` (Robolectric) konfiguriert eine Backend-URL und führt alle Requests aller `HttpClient`s durch eine zählende `MockEngine`. Start, Vordergrund/Hintergrund, Coach-Queue, alle Tabs, Coach-Chat,
  Profil und der Fuel-Hinweis ergeben **0** Anfragen an den Backend-Host; als Gegenprobe erzeugt „Fuel ansehen“ danach Anfragen.
- In der App (Coach-Tab → KI-Zugang, nur bei eigenem Key): „Deine Daten gehen direkt an <Anbieter>, nicht über overfit-Server.“ (DE/EN).
- Bewusst nicht Teil der Garantie: Fuel, Spot, KI-Chat-Verbindung und Kurz-Code-QR – sie sind ausdrückliche Opt-ins und sprechen mit dem Backend.

## Fuel: Stufen statt fester Anbieter, AI Gateway (Session X, 3.11.)
- **Stufen:** Die App schickt weiter das Anthropic-Messages-Format, `model` ist aber eine Stufe: `standard` (Google `gemini-3.6-flash`,
  Voreinstellung für Coach-Auswertung, Chat und Vorschläge), `pro` (Anthropic `claude-sonnet-5-5`), `lite` (Workers AI
  `@cf/meta/llama-3.1-8b-instruct`, nur „Mit KI ausfüllen“). Das Backend bildet das Format ab und antwortet im Anthropic-Format, damit
  `anthropicReply` in der App und alle App-Verträge gleich bleiben. Alte App-Versionen schicken `claude-sonnet-5-5` und landen auf `pro`.
- **Modellwahl:** `gemini-3.6-flash` ist laut Googles Modellliste stabil (3.8 Flash ist noch „New“, gleicher Preis), auch der eigene
  Gemini-Key-Weg der App nutzt es. Für `lite` ein Modell, das bei Workers AI ausdrücklich JSON-Modus kann (Llama 3.1 8B); die günstigeren
  fp8-fast-Varianten stehen nicht auf der JSON-Liste. Schlägt der JSON-Modus fehl, weicht die Anfrage auf `standard` aus.
- **Gemini über die native API** (`generateContent`) statt OpenAI-kompatibel: `responseJsonSchema` nimmt das Schema der App direkt,
  `usageMetadata` liefert Cache- und Thinking-Token getrennt (beides wird abgerechnet), `finishReason`/`blockReason` lassen sich sauber auf
  `max_tokens`/`refusal` abbilden. Gemini zählt Thinking in die Ausgabe, darum bekommt es `2 × max_tokens` (mind. 4096) und
  `thinkingLevel: medium` (oder `effort` der App).
- **Preise im Code** (`backend/src/providers.ts`, µ€ je MTok, Listenpreis × 0,92 €/$), mit Gültig-ab-Datum: Gemini 3.6 Flash wird am
  1.1.2027 doppelt so teuer, das ist schon hinterlegt (Test). Workers AI in Neurons je MTok (0,011 $ je 1000 Neurons); das Freikontingent
  von 10 000 Neurons/Tag rechnen wir nicht heraus (Marge). `PRICE_TABLE` kann einzelne Modelle kurzfristig überschreiben.
- **Ausweichen:** Ausfall = 5xx, 429, 408, Zeitüberschreitung, Netzwerk, bei Workers AI jeder Binding-Fehler. Dann genau ein Versuch mit
  der Ausweich-Stufe (`standard` ↔ `pro`, `lite` → `standard`), wenn sie konfiguriert ist, Bilder kann (falls nötig) und von den 140 s
  Gesamtbudget noch ≥ 15 s übrig sind. 4xx-Ablehnungen weichen nie aus (die App wiederholt Structured-Output-Fehler selbst ohne Schema).
  `lite` → `standard` ist zusätzlich zum Auftrag drin, weil „Mit KI ausfüllen“ sonst bei jeder Workers-AI-Störung ausfiele; es kostet uns
  pro Fall Bruchteile eines Cents.
- **Nie mehr als die angefragte Stufe:** abgerechnet wird `min(Kosten zum Preis des genutzten Modells, Kosten zum Preis der angefragten
  Stufe)` für die tatsächlich verbrauchten Token, höchstens die Reservierung der angefragten Stufe. Begründung: Der Nutzer hat einen Preis
  gewählt und kann nichts für den Ausfall; Ausweichen ist selten, die Differenz (Sonnet statt Flash ≈ 2,7×) trägt der 8-%-Aufschlag über alle
  Anfragen. Weicht `pro` auf `standard` aus, zahlt der Nutzer den günstigeren Preis. Das Ledger bucht `model` = genutztes Modell und `tier`
  = angefragte (abgerechnete) Stufe (Migration `0003_ledger_tier.sql`), die Antwort meldet `overfit.servedTier` und `fallback`.
- **Nicht verfügbar ≠ Ausfall:** Fehlt der Key einer Stufe, ersetzt das Backend sie nicht still (sonst dauerhafte Quersubvention), sondern
  antwortet `503 tier_unavailable` mit `details.available`. `/v1/config` meldet je Stufe `available`, die App wählt dann selbst die nächste
  (lite → standard → pro, pro ↔ standard) und blendet Pro aus, wenn es fehlt.
- **AI Gateway:** alle Anbieter-Aufrufe laufen über ein Gateway je Betrieb (`overfit`, `overfit-test`): Gemini und Anthropic über
  `env.AI.gateway(id).getUrl(provider)` (keine Account-ID im Code nötig), Workers AI über die `gateway`-Option von `AI.run`. Das Gateway ist
  nicht authentifiziert, weil jeder Aufruf ohnehin den Anbieter-Key braucht. Angelegt mit Protokollen, Rate-Limit 600/min (gleitend, nur
  Notbremse; je Gerät gilt weiter 10/min) und ohne Cache (Coach-Anfragen sind praktisch nie gleich; im Dashboard zuschaltbar).
  **Datenschutz:** Anfragen mit Körperfotos schicken `cf-aig-collect-log: false`, das Gateway speichert sie nicht. Textanfragen werden
  protokolliert (Fehlersuche, Kosten); Metadaten nur `tier`, keine Konto-ID.
- **Workers-AI-Binding nur im Deploy:** `ai` steht nicht in `wrangler.jsonc`, sondern wird von `scripts/deploy-config.mjs` ergänzt.
  Sonst bräuchten die Vitest/Miniflare-Tests und `wrangler dev` ein Cloudflare-Konto (das Binding ist immer remote). Tests mocken `AI`.
- **Deploy-Rechte:** Der Workflow prüft vor dem Deploy `GET /ai/models/search` (braucht *Workers AI · Read*) und liest bzw. legt das Gateway
  per REST an (`/ai-gateway/gateways`, braucht *AI Gateway · Edit*). 401/403 → Abbruch mit genauer Anleitung, welche Zeile im Token fehlt.
- **Secrets:** `GEMINI_API_KEY` neu, `ANTHROPIC_API_KEY` optional; mindestens einer ist Pflicht (Abbruch mit Anleitung). Fehlt einer, steht
  in der Zusammenfassung, welche Stufe nicht verfügbar ist. Testbetrieb unverändert.
- **App:** Coach-Einstellungen → „Coach-Qualität: Standard / Pro“ (nur mit Fuel, nur wenn beide Stufen verfügbar; Hinweis „Pro kostet etwa
  das n-Fache“ aus den Preisen in `/v1/config`, gewichtet 80 % Eingabe / 20 % Ausgabe). Gespeichert in den Coach-Einstellungen (kein
  Room-Schema). „Mit KI ausfüllen“ fragt über `AiGateway.send(light = true)`: mit Fuel `lite`, mit eigenem Key unverändert.
  `FuelProvider` merkt sich `/v1/config` 10 Minuten. Die Config wird nur geladen, wenn Fuel der KI-Zugang ist; die Garantie „eigener Key =
  null Backend-Anfragen“ bleibt (OwnKeyPrivacyTest).

## Ehrliche Begriffe und volle Kostentransparenz (Session Y, 3.11.)
Auslöser (Fabian): „Ich möchte nicht die Begriffe Standard oder Pro oder Lite verwenden. Das klingt nach Abzocke. Verwende einfach die Namen der Modelle … und kommuniziere die Kosten.“ Außerdem: „Fuel“ heißt künftig „Guthaben“ (EN „Credit“), schlicht und in Euro.

### Grundsatz (gilt für alle künftigen Sessions)
1. **Kein Marketing-Name für Modelle.** In der UI, auf der Webseite, in Store-Texten und Doku stehen die echten Modellnamen mit Anbieter („Gemini 3.6 Flash (Google)“, „Claude Sonnet 5.5 (Anthropic)“, „Llama 3.1 8B (Cloudflare)“), nie „Standard“, „Pro“, „Lite“ o. Ä.
2. **Preise sind immer sichtbar:** vor der Wahl (geschätzt, pro Auswertung, Chat-Nachricht und „Mit KI ausfüllen“), nach der Antwort (echt, mit Modell).
3. **Die Marge steht offen** in docs/monetization.md („Marge (offen)“): 8 % Aufschlag auf den Listenpreis, Store-Gebühr 15 %, daraus ≈ 6,3 % vom Kaufpreis.
4. Wenn ein anderes Modell antwortet als das gewählte, sagt die App es: „Gemini 3.6 Flash war nicht erreichbar, Claude Sonnet 5.5 hat geantwortet, berechnet wurde der Preis von Gemini 3.6 Flash.“

### Umbenennung Fuel → Guthaben
- Sichtbar überall: UI (DE „Guthaben“, EN „Credit“), README, SPEC, Store-Texte, backend/README, docs, Workflow-Meldungen. Komposita: „Guthaben aufladen“, „Guthaben · 4,20 €“, „Mit Guthaben nutzen“, „Guthaben öffnen“. Spot bleibt Spot (1 € Guthaben je Einladung).
- **Bezeichner im Code bleiben** (`Fuel*`-Klassen, Paket `app.overfit.fuel`, `ui/fuel`, String-Felder `fuel*`, `FuelStrings`): Das Umbenennen berührt ~60 Dateien, Room-Tabellen/Backups und Tests ohne Nutzen für Nutzer und birgt Merge-Konflikte mit den parallelen Sessions U und Z. Nutzer sehen sie nie.
- **Store-Produkt-IDs bleiben** `overfit.fuel.5` / `overfit.fuel.10` (bei Apple/Google registriert, Änderung = neue Produkte, nicht sichtbar).
- Die Web-Kaufseite (Session Z) nutzt „Guthaben“ bereits.

### Modellnamen statt Stufen
- Stufen-IDs (`standard`, `pro`, `lite`) bleiben **intern** (Protokoll App ↔ Backend, gespeicherte Einstellung, Ledger-Spalte `tier`); die UI zeigt sie nie.
- `GET /v1/config` liefert je Stufe `displayName`, `vendor`, `label`, `thinkingTokens`, Preise, dazu `usageProfiles` (typische Token je Aufgabe). Die App liest die Namen von dort; die statische Tabelle `fuel/ModelNames.kt` benennt nur gespeicherte Modell-IDs in alten Buchungen und Antworten und dient als Rückfall bei altem Backend.
- Auswahl im Coach: „Modell für deinen Coach“ als Liste der verfügbaren Modelle; pro Modell „Auswertung ca. 1,5 ct · Chat-Nachricht ca. 1,1 ct“, darunter „Mit deinem Guthaben (4,20 €) reicht das für etwa 278 Auswertungen.“ und „„Mit KI ausfüllen“ nutzt immer Llama 3.1 8B (Cloudflare), ca. 0,04 ct je Übung.“
- Unter jeder Coach-Antwort und Chat-Antwort: „Modell · echte Kosten · Guthaben“. Buchungen zeigen das Modell statt der Stufe; bei Ausweichung steht „Ersatzmodell, berechnet wurde der Preis von …“.
- Antwort-Meta (`overfit`) trägt zusätzlich `modelName`, `vendor`, `requestedModel*`; die App speichert `servedModel`/`fallbackFrom` an Review und Chat-Nachricht (Room v7, AutoMigration 6→7, Migrationstest).

### Kostenschätzung (Methode, berechnet statt geraten)
Schätzung = ⌈(Eingabe-Token × Eingabepreis + (Ausgabe-Token + Thinking) × Ausgabepreis) / 1 Mio. × (1 + Aufschlag)⌉ in µ€, Integer-Rechnung wie das Backend (`CostEstimate.kt`, unit-getestet). Preise aus der Preistabelle (`/v1/config`), Aufschlag `markupBp`. Typische Token-Mengen aus den **echten Prompts**, gemessen an den Bausteinen der App (Zeichen, ÷ 3,3 Zeichen je Token, bewusst hoch gerundet, ohne Cache-Rabatt):
- **Coach-Auswertung:** System (Anweisungen ≈ 1,1k + Ausgaberegeln 1,8k + Beispiel 0,6k Zeichen) + Übungsliste des Katalogs (302 Einträge, 11,5k Zeichen) + Schema ≈ 1,5k + Profil ≈ 0,6k + Nutzernachricht (Gedächtnis ≈ 2,5k, heutiges Training ≈ 2,5k, Vorlage ≈ 0,8k, frühere Einheiten ≈ 2,5k, Verläufe ≈ 1,5k) ⇒ ≈ 27 000 Zeichen ⇒ **≈ 8 200 Token ein**; Antwort (JSON mit Bewertung, Übungen, nächster Einheit, Gedächtnis) ≈ **1 400 Token aus**.
- **Chat-Nachricht:** System ≈ 15k Zeichen (Regeln, Share-Beispiel, Übungsliste) + Schema ≈ 1,5k + Kontext ≈ 3k + Verlauf (≈ 6 Nachrichten) ≈ 2,4k + Frage ⇒ **≈ 6 700 ein / 500 aus**.
- **Mit KI ausfüllen:** System ≈ 1,8k + Schema ≈ 0,9k + Eingabe ≈ 0,2k ⇒ **≈ 900 ein / 120 aus** (immer Llama 3.1 8B).
- **Gemini-Thinking:** zählt als Ausgabe; Aufschlag von ≈ 1 000 Token bei Coach und Chat (`thinkingTokens`, Stufe `medium`).
- Die echten Mengen schwanken (lange Gedächtnisse, viele Übungen). Die echten Kosten stehen unter jeder Antwort; die Schätzung ist als „ca.“ gekennzeichnet. Ändern sich Prompts deutlich, werden `USAGE_PROFILES` (backend/src/providers.ts) und `DEFAULT_USAGE_PROFILES` (App, Rückfall für alte Backends) neu gemessen.

### Protokollierung von Anfragen mit Körperfotos
- **Recherche (Cloudflare-Doku, AI Gateway → Logging, 3.11.):** Der Header `cf-aig-collect-log-payload: false` überspringt das Speichern von Anfrage und Antwort, die Metadaten (Tokens, Kosten, Modell, Anbieter, Dauer, Status) werden weiter protokolliert. `cf-aig-collect-log: false` unterdrückt dagegen den ganzen Eintrag, und das war bisher unser Verhalten bei Foto-Anfragen (Kosten und Tokens fehlten im Gateway).
- **Entscheidung:** Foto-Anfragen senden jetzt `cf-aig-collect-log-payload: false` statt `cf-aig-collect-log: false`. Es werden weiterhin keine Fotos, Prompts oder Antworten gespeichert; Kosten und Verbrauch erscheinen im Gateway. Zusätzlich `cf-aig-metadata` `{ tier, photo, fallback }` bei jeder Anfrage (nur Markierungen, keine Inhalte). Für Workers AI läuft dieselbe Metadaten-Angabe über die `gateway`-Option; diese Stufe nimmt keine Bilder.
- **Eigener Weg unabhängig vom Gateway:** Das Ledger in D1 hält je Verbrauchsbuchung Modell, Tokens, berechneten Betrag und (neu, Migration 0004) `cost_micro` (Anbieterkosten zum Listenpreis ohne Aufschlag), `fallback`, `photo`; Stufe (`tier`) war schon da. `GET /v1/admin/usage?from=&to=` (ADMIN_SECRET ≥ 16 Zeichen, auch in Produktion; sonst 404) gruppiert nach Modell/Stufe/Ausweichung/Foto und liefert Anzahl, Tokens, berechnet, Anbieterkosten und Marge (= berechnet − Anbieterkosten). Dazu `backend/scripts/usage-report.mjs` als Tabelle.
- **Datenschutz:** Die Auswertung enthält nur Zahlen und Ja/Nein-Markierungen, nie Prompt, Antwort, Foto, Konto- oder Geräte-IDs. Ein Foto-Flag verrät nur „diese Anfrage enthielt ein Foto“, nicht welches; es gibt keine Verbindung zu einer Person im Bericht (aggregiert). Alte Buchungen behalten `cost_micro = NULL` (Marge unbekannt) statt geschätzter Werte.

## Guthaben im Web, Testmodus (Session Z, 3.11.)
- **Anbieter: Paddle (Billing) als Merchant of Record, zuerst nur Sandbox.** Paddle übernimmt EU-USt, Belege, Erstattungen und
  Chargebacks; die Sandbox ist für ein Einzelkonto sofort und kostenlos nutzbar (keine Domain- oder Kontoprüfung, Default Payment
  Link darf jede Domain sein). Lemon Squeezy wäre gleichwertig, gehört aber inzwischen zu Stripe und wird umgebaut; Stripe allein
  hieße USt selbst abführen (OSS). Gebühr Paddle: 5 % + 0,50 € je Kauf. Die Integration liegt hinter `PaymentProvider`
  (`backend/src/payments/provider.ts`: `createCheckout`, `parseWebhook`); ein Wechsel betrifft nur `paddle.ts`.
- **Checkout per API-Transaktion + Paddle.js auf der eigenen Seite** (`/v1/web/checkout` legt die Transaktion mit Preis ohne
  Katalog, `tax_mode: internal` an). So braucht Fabian im Paddle-Dashboard keine Produkte/Preise anzulegen; Pakete und Preise
  stehen im Backend (`WEB_PACKAGES`). Paddle hat keine eigene gehostete Seite; der Default Payment Link zeigt auf `/guthaben/`.
- **Pakete 5/10/20 € statt 2/5/10 €:** Die Fixgebühr von 0,50 € frisst bei 2 € ein Viertel des Preises. (Session AA: weiter auf 10/20/50 €, siehe unten.)
- **1:1-Guthaben, Endpreise inkl. USt.** Fabians Vorgabe; die Margenfrage ist in Session AA entschieden (nächster Abschnitt).
- **Kauflink = Zufallstoken mit Hash in D1 statt HMAC-signiertem Token.** Einmaligkeit und Ablauf muss der Server ohnehin
  speichern; ein signierter Token brächte nichts dazu, würde aber die Konto-ID in die URL tragen. Token im Fragment (`#t=`), damit er
  weder in Logs noch im Referer landet. 15 Minuten, bis zu 5 Checkouts (Paket wechseln, Abbruch), nach der ersten Zahlung verbraucht.
- **Backend-Adresse der Kaufseite nur aus `config.js` (Pages-Workflow, Repo-Variable `OVERFIT_BACKEND_URL`), nie aus der URL:**
  sonst könnte ein präparierter Link unsere Seite auf einen fremden Server mit fremdem Paddle-Konto umleiten.
- **Sandbox nie in Produktion:** Ein Produktions-Worker bietet den Web-Kauf nur mit `PADDLE_ENV=live` an; der Testbetrieb ist immer
  Sandbox. Sonst könnte jemand mit Testkarten echtes Guthaben (= echte KI-Kosten) erzeugen.
- **Idempotenz:** Wächterzeile `web_events (provider, event_key)` in derselben D1-Batch-Transaktion wie die Gutschrift; eine zweite
  Zustellung scheitert am Primärschlüssel und rollt komplett zurück. Erstattungen erst bei `status = approved`; abgezogen wird von
  allen Posten des Kontos (unbefristete zuerst), nie unter 0, Fehlbetrag als `incident = …:shortfall` an der Bestellung.
  Chargeback-Rücknahmen (`chargeback_reversal`) buchen nichts automatisch zurück (selten, lieber von Hand).
- **Guthaben-Codes** 12 Zeichen aus 31 (ohne 0/O/1/I/L, ≈ 59 Bit), Klartext in D1 (wie ein Gutschein; wer die DB hat, hat ohnehin
  die Salden). Der Käufer sieht den Code nur auf der Erfolgsseite (Bestell-ID als Geheimnis). Erstattung vor dem Einlösen senkt den
  Wert bzw. sperrt den Code, danach Abzug beim Einlöser. Codes verfallen nicht.
- **Store-Regeln:** App-Knopf „Im Web aufladen“ nur, wenn `/v1/config` → `webPurchase.channels` den Kanal enthält. Kanal Android:
  Installer (Play/andere Stores = `store`, sonst `sideload`), iOS immer `store`. Standard `sideload`, Repo-Variable
  `WEB_PURCHASE_CHANNELS`. „Code einlösen“ bleibt überall sichtbar (kein Kaufweg in der App).
- **Datenschutz-Garantie:** Der Web-Kauf ist ein Opt-in hinter dem bestehenden Guthaben-Opt-in; `OwnKeyPrivacyTest` bleibt grün.
- **Benennung:** Neue Texte sagen „Guthaben“/„Credit“ (Session Y benennt den Rest um); sie liegen in `i18n/WebCreditStrings.kt`,
  damit es mit der parallelen Umbenennung keine Konflikte gibt. Neue Ledger-Typen: `code_redeem`, `refund`, `chargeback`; neues Feld
  `source` (`web`, `code`).
- Impressum, Datenschutz, AGB auf der Kaufseite sind sichtbar als „folgt vor dem Start“ markiert, keine erfundenen Rechtstexte.

## Marge bei 1:1-Guthaben (Session AA, 3.11.)
Fabians Entscheidung (Geld): „1:1 mit höherer Marge“. Guthaben = Kaufpreis in jedem Kanal; der Aufschlag auf die Modellpreise wird so hoch gesetzt, dass jeder Kauf auch bei ganz verbrauchtem Guthaben kostendeckend ist. Rechnung mit Tabelle: docs/monetization.md „Rechnung (Session AA)“.
- **Ein einziger Aufschlag `MARKUP_BP = 6900` (69 %)**, weil das Guthaben kanalübergreifend dasselbe ist. Dimensioniert für den ungünstigsten Kanal (Store: 15 % Gebühr auf den Preis ohne USt, nötig 68,7 %) mit 10 % Spot-/Startguthaben-Rücklage und 7 % Zielgewinn vom Netto (Spanne 5–10 %; bescheiden, aber genug für Betrieb, Rückbuchungen und Kursschwankungen). Zentral in `backend/src/config.ts` (`DEFAULT_MARKUP_BP`) und `wrangler.jsonc`; `/v1/config` liefert Preise und `usageProfiles`, die App rechnet die Schätzung (Coach-Auswertung Gemini 3.6 Flash ≈ 2,4 ct) daraus, ohne eigene Konstante.
- **Web-Pakete 10/20/50 € statt 5/10/20 €:** Die feste Paddle-Gebühr von 0,50 € macht 5 € zum teuersten Paket (nötig wären 74,5 % statt 68,7 %, also 5,8 Punkte mehr für alle). Ab 10 € ist der Store der Engpass. Das 5-€-Store-Paket (`overfit.fuel.5`) bleibt: proportionale Gebühr, kein Einfluss auf den Aufschlag. IDs stabil, nichts läuft aus.
- **Store-Gutschrift korrigiert:** vorher Preis × (1 − Gebühr) ÷ (1 + Aufschlag) (zog den Aufschlag doppelt ab und ließ die USt weg). Jetzt Gutschrift = Preis, die Store-Gebühr (15 % auf Netto nach USt) steckt im Aufschlag. `STORE_FEE_BP` entfällt, `storeFeeBp` ist nicht mehr in `/v1/config`.
- **Transparenz:** Aufschlag und Wofür in monetization.md; App unter „Guthaben“: ein Satz plus Link „Wie setzen sich die Preise zusammen?“ auf `/preise/` (DE+EN, Beispielrechnung).
- **Verworfen:** Abzug beim Kauf (widerspricht 1:1); eigener Aufschlag je Kanal (das Guthaben ist kanalübergreifend dasselbe und wird beim Verbrauch nicht nach Herkunft getrennt; ein Wert ist einfacher und ehrlicher).
- **Offen für später:** Bei Paddle für kleine Pakete bessere Konditionen anfragen; bei Wechselkurs- oder Preisänderungen der Modelle `backend/test/pricing.test.ts` anpassen und Aufschlag neu rechnen.

## „Spot“ heißt jetzt „Einladung“ (Session AC, 3.11.)
Fabian: „Benenne Spot um in das, was es ist. Referral-Programm mit Einladung und so weiter.“ Der Name „Spot“ war nicht selbsterklärend.
- **Sichtbare Begriffe:** DE „Freunde einladen“ (Menüpunkt und Titel), „Einladung einlösen“, „Einladung von <Name>“, „Einladungscode“, Bonus „Einladungsbonus: 1 € Guthaben für dich und deinen Freund“; in erklärenden Texten zusätzlich „Empfehlungsprogramm“. EN „Invite friends“, „Redeem invite“, „Invitation from <name>“, „Referral bonus“, „referral program“. Ledger-Bezeichnungen: „Einladungsbonus“ bzw. „Referral bonus“ (für beide Seiten; der Einladende sieht den Zusatz „Freund eingeladen“). „Spot“ steht in keinem sichtbaren Text mehr (App, Web, Store-Texte, README, Docs, Deploy-Summary).
- **Technik bleibt bewusst „spot“:** Code-Bezeichner, API-Pfade (`/v1/spot/*`), Fehlercodes (`spot_*`), DB-Spalten/Tabellen, Ledger-Typen (`spot_bonus`, `spot_reward`) und Config-Namen (`SPOT_*`). Ein Umbenennen würde die Server-API, Migrationen und deployte Secrets/Variablen brechen, ohne dass Nutzer etwas davon sehen.
- **Link-Alias:** Neue QR-Codes/Links erzeugen `overfit://invite#<token>`; die App liest zusätzlich `overfit://spot#…`, damit alte QR-Codes und Links weiter gehen. Beide laufen über denselben URL-Scheme-Handler (`overfit`), das Manifest bzw. die Info.plist bleiben unverändert. Test: `FuelBasicsTest.spotTokenParsing`.
- Reveal-Animation und Haptik unverändert, nur der Text.

## Web-Kauf über Stripe, Store-Aufpreis offen, Aufschlag 28 % (Session AB, 3.11.)
Fabians Entscheidung (Geld): „Ja, mit Stripe umbauen und Store-Aufpreis ausweisen. Ich bin schon Kleinunternehmer.“ Rechnung mit Tabellen:
docs/monetization.md „Rechnung (Session AB)“; Schwellen: „Kleinunternehmer und Schwellen“.
- **Stripe statt Paddle, Paddle entfernt.** Paddle ist Merchant of Record und schlägt USt auf bzw. zieht sie ab; das passt nicht zum
  Direktverkauf als Kleinunternehmer (ohne USt), und die Gebühr (5 % + 0,50 €) war der Grund für den hohen Aufschlag. Paddle als zweite
  Implementierung zu behalten hätte Secrets, Doku, Tests und eine Auswahl-Logik am Leben gehalten, ohne dass sie je genutzt wird; die
  Schnittstelle `PaymentProvider` bleibt, ein Wechsel ist weiterhin eine neue Datei in `backend/src/payments/`. Migrationen bleiben
  abwärtskompatibel: `0006_stripe.sql` fügt nur Spalten hinzu (`provider_payment_id`, `country`, `paid_micro`); alte Sandbox-Bestellungen
  (`provider = paddle`) bleiben lesbar.
- **Gehosteter Stripe Checkout, kein Stripe.js, kein Node-SDK.** Formular-POST an `/v1/checkout/sessions` per fetch, Weiterleitung auf
  `session.url`. Kein Publishable Key nötig, keine Kartendaten auf unserer Seite. Zahlarten nicht im Code fest: „dynamische
  Zahlungsmethoden“ aus dem Dashboard (Karte, Wallets, Link, SEPA, Klarna …). Verzögerte Zahlarten buchen erst bei
  `checkout.session.async_payment_succeeded`. Idempotency-Key je Bestellung.
- **Webhook-Signatur selbst geprüft** (WebCrypto, HMAC-SHA256 über `t.body`, 300 s Toleranz wie Stripes SDK, mehrere `v1`). Zusätzlich
  `livemode`-Abgleich: Test-Ereignisse buchen nie auf einem Live-Worker. Erstattungen kommen als `charge.refunded` mit kumulierter Summe;
  gebucht wird nur die Differenz zum schon Erstatteten, idempotent über `refund:<charge>:<Summe>`. Chargeback: `charge.dispute.created`.
  Erstattung vor Zahlungsbestätigung findet die Bestellung über `metadata.overfit_order` am PaymentIntent.
- **Modus aus dem Schlüssel statt Repo-Variable** (`PADDLE_ENV` entfällt): Testbetrieb nimmt nur `sk_test_…`, Produktion nur `sk_live_…`.
  Für Produktion eigene GitHub-Secrets `STRIPE_LIVE_SECRET_KEY`/`STRIPE_LIVE_WEBHOOK_SECRET`, damit Test- und Live-Werte nebeneinander liegen.
- **Abbruch-Rücksprung ohne Token:** `cancel_url` enthält den Kauflink-Token nicht (er soll nicht bei Stripe landen); die Kaufseite merkt
  ihn sich vor der Weiterleitung in `sessionStorage`.
- **Kleinunternehmer:** Endpreise ohne USt, kein Stripe Tax. „Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.“ steht auf Kaufseite,
  Stripe-Checkout (`custom_text`) und Bestätigung. Stripe-Belege haben kein freies Fußzeilenfeld; für eine Rechnung mit Hinweis gibt es
  `STRIPE_INVOICES=on` (Stripe-Rechnung mit Fußzeile, kostet extra), standardmäßig aus, weil bei B2C keine Rechnungspflicht besteht und die
  Gebühr den Gewinn kleiner Pakete auffrisst.
- **Schwellen-Werkzeug statt Beratung:** `webRevenue` im Admin-Usage-Report (Jahr, Land, Summe, Warnung ab 80 % von 10.000 € EU-Lieferschwelle,
  25.000 €/100.000 € Kleinunternehmergrenze). Gespeichert wird nur das Rechnungsland. Unbekanntes Land zählt bei der Lieferschwelle
  vorsichtshalber mit. Store-Erlöse fehlen darin (stehen in den Store-Konsolen); der Hinweis sagt das.
- **Preise je Kanal:** Web 1:1. Store-Pakete bekommen festes Guthaben und einen offen ausgewiesenen Store-Preis (`PACKAGES` mit
  `priceMicro` = Store-Preis, `creditMicro` = Guthaben): 5 € für 6,99 €, 10 € für 13,99 €. Die Stores sind Verkäufer, führen USt ab und
  nehmen 15 % vom Netto; Fabians Kleinunternehmerstatus ändert das nicht. Preisstufen: die niedrigste x,99-Stufe, die bei 27 % USt noch
  ≥ 5 % Gewinn lässt (6,49 bzw. 13,49 € reichen nicht). Produkt-IDs bleiben; Store-Kauf ist noch nicht live, also bricht nichts.
- **Aufschlag 28 % (`MARKUP_BP = 2800`).** Stripe-Gebühr konservativ gemischt mit 2,5 % + 0,25 € (Preisseite: EWR 1,5 %, Premium 2,8 %,
  UK 2,5 %, Nicht-EWR 3,15 %, Umrechnung + 2 %, jeweils + 0,25 €); jede einzelne Zahlart muss ohne Verlust bleiben. Sonst dieselben
  Annahmen wie AA (10 % Rücklage für Startguthaben und Einladungsbonus, Ziel 7 %, Untergrenze 5 % vom Netto). 28 % ist der kleinste ganze Wert, mit dem jedes Web-Paket
  ≥ 5 % behält; ab 10 € > 7 %. Coach-Auswertung Gemini 3.6 Flash damit ≈ 1,8 ct statt 2,4 ct.
- **5 € im Web wieder drin:** kostet alle nur einen Punkt (27 % → 28 %) statt 5,8 Punkte wie bei Paddle; ein niedriger Einstieg ist für
  eine App ohne Abo den Punkt wert. Vorgewählt bleibt 10 €.
- **Verworfen:** eigener Aufschlag je Kanal beim Verbrauch (Guthaben wird nicht nach Herkunft getrennt, siehe AA); 6,49/12,99 € im Store
  (bräuchten ~35 % Aufschlag für alle); Stripe Tax/OSS jetzt schon (erst nötig über der Lieferschwelle).
- **Offen:** echter Lauf gegen Stripe im Testmodus (alles gemockt geprüft); Live-Betrieb braucht Kontoaktivierung, Rechtstexte,
  juristische Prüfung. Bei Preisänderungen `backend/test/pricing.test.ts` anpassen und neu rechnen.

## Session AD – Einladung intern, Guthaben vs. echte Euro, Bonus als Auswertungen (3.11.)

- **„Spot“ intern entfernt:** API `/v1/invite/*`, Env `INVITE_*`, Tabellen `invite_redemptions`/`invite_tokens_used`, Ledger-Typen
  `invite_bonus`/`invite_reward`, Kotlin `Invite*`. Alte Pfade und alte `spot`-Links werden nicht mehr gelesen (App unveröffentlicht).
  Der Token-Präfix wechselt mit; laufende 45-s-Codes werden beim Deploy einmal ungültig (harmlos). Bleibende Erwähnungen: Migration 0001
  (angewandte Migrationen ändern wir nie) und 0007 (benennt um), der Fallback im Deploy-Workflow, `package-lock.json` (`@cspotcode`, fremd).
- **Signierschlüssel:** Fehlt das GitHub-Secret `INVITE_SIGNING_KEY`, übernimmt der Deploy das alte Secret; fehlen beide, wird ein neuer
  erzeugt (der Schlüssel signiert nur kurzlebige Tokens und Challenges, ein Wechsel verliert nichts). Der alte Secret-Name im Worker wird
  gelöscht.
- **Geschenkte Auswertungen als eigener Zähler (Tabelle `review_gifts`)** statt Euro-Posten: Fabian hat „5 + 5 Auswertungen“ entschieden.
  Ein Zähler ist ehrlicher als ein Euro-Betrag (die Kosten schwanken je Modell und Preis) und lässt sich auf ein Modell begrenzen.
- **Was ist eine Coach-Auswertung?** Die App markiert sie im Body (`overfit.purpose = "review"`, nur über Guthaben, wird vor dem Anbieter
  entfernt). Der Server kann den Inhalt nicht prüfen; dass ein manipulierter Client einen Chat als Auswertung ausgibt, begrenzen Stufe
  standard, die Obergrenze je Anfrage (0,25 € Anbieterkosten) und die harte Monatsgrenze der echten Kosten (50 €). Das reicht für einen
  Bonus von 5 Anfragen.
- **Belohnung bei jeder ersten erfolgreichen Anfrage** (auch wenn sie geschenkt war), nicht erst bei einer bezahlten: Der Neue soll seine
  geschenkten Auswertungen nutzen können, ohne dass der Einladende leer ausgeht.
- **Monatsbudget doppelt:** Zählt Auswertungen bei der Einlösung (1000, Vorhersage) und echte Kosten beim Verbrauch (50 €, Garantie).
  Ausweichen auf Claude bei Ausfall bleibt erlaubt; die echten Kosten zählen in die 50 € mit.
- **Reservierung geschenkter Auswertungen** zählt den Zähler sofort herunter und gibt ihn bei Fehlschlag zurück. Stürzt der Worker
  zwischen beidem ab, ist eine Auswertung verloren (sicherer Fehler, selten).
- **Kontoumzug:** Geschenkte Einladungs-Auswertungen und alte Euro-Einladungsboni des aufgegebenen Kontos verfallen (Buchung
  `invite_expired`), Startguthaben/Test-Gutschriften und Gekauftes wandern mit. Belohnungen gehen nie an das eigene Konto.
- **Gekauft vs. geschenkt:** Geschenkt = `test_credit`, `invite_bonus`, `invite_reward`; alles andere (Store, Web, Code) ist gekauft.
  Verbrauch: Geschenktes zuerst (explizit sortiert, nicht nur über den Verfall). Erstattungen ziehen weiter zuerst vom Gekauften ab.
- **Kategorie je Buchung kommt vom Server** (`category`), die App rechnet sie für alte Backends selbst aus. „Verfallen“ ist eine fünfte
  Kategorie neben bezahlt/geschenkt/verbraucht/erstattet.
- **Finanzbericht:** Gebühren sind geschätzt (Stripe-Mischung 2,5 % + 0,25 €, Store 15 % auf netto bei 19 % USt), weil Stripe und die
  Stores die echten Gebühren erst in Auszahlungsberichten nennen. Store-Erstattungen fließen noch nicht ein (keine Store-Webhooks).
- **Kein Room-Update:** Ob eine gespeicherte Antwort geschenkt war, erkennt die App an Kosten 0 (Anzeige „geschenkte Auswertung“).

## Duo-Teaser, Coach-Tipps auf dem Training-Tab, Lizenzhinweis (3.11., Fabian)
- Duo-Teaser komplett entfernt (Screen, Route, Texte); Duo ggf. später. Ebenso „Letzte Coach-Tipps“ und die Coach-Zeile auf dem Training-Tab (Coach bleibt eigener Tab).
- „Über“: oben „overfit ist Open Source unter der MIT-Lizenz“ mit Link zum Repo (LICENSE ist MIT); die Bibliotheken stehen getrennt mit „(Apache License 2.0)“, damit die Lizenzen nicht verwechselt werden.

## Feedback-Runde 3: Übergänge, Kopfzeilen-Icons, Buttons, Bestätigen, Flächen statt Haarlinien (Session AE, 3.11.)
- **Ursache der „verbuggten Übergänge“:** Der `NavHost` hatte keine eigenen Übergänge, also galt der Standard: 700 ms Überblendung
  (alter und neuer Screen halbtransparent übereinander), auch zwischen Tabs. Dazu wechselten Tab-Leiste und Mini-Workout-Leiste sofort,
  während die Überblendung noch lief (der ausblendende Screen sprang in der Höhe), und Taps während der 700 ms wirkten weiter: Doppel-Tipp
  auf „Zurück“ poppte zwei Screens, Doppel-Tipp auf eine Zeile öffnete sie zweimal.
- **Lösung, ein Übergang für alles (`ui/ScreenMotion.kt`):** Tab-Wechsel ohne Animation; alle anderen Wechsel ein kurzes Fade-through
  (alter Screen 90 ms aus, dann neuer 150 ms ein), nie zwei halbtransparente Screens übereinander, nichts gleitet. `sizeTransform = null`.
  Während ein solcher Wechsel läuft (`visibleEntries` > 1, nicht nur Tabs), schluckt eine Ebene über dem `NavHost` alle Taps.
  Tests: `UiFeedbackTest` (Wechsel < 300 ms und schluckt den zweiten Tap, Tab-Wechsel < 50 ms).
- **Icons oben rechts:** `BackHeader(actions = …)` mit `HeaderIconButton` (Teilen, Bearbeiten) und `HeaderDeleteButton` (Mülleimer → rote
  Pille „Wirklich löschen?“, zweiter Tipp löscht). Gilt für Workout, Training im Verlauf, eigene Übung, Foto. Bearbeiten-Modi (Workout-Editor,
  Übungsformular) zeigen weder Teilen noch Löschen; deshalb liegt „Workout löschen“ jetzt im Workout-Screen und „Übung löschen“ in den
  Übungsdetails. Ausnahme Workout-Screen: „Bearbeiten“ als großer SecondaryButton unter „Starten“ (Fabians Vorgabe), kein Stift oben.
  Hinweis „Trainings bleiben im Verlauf“ erscheint, solange Löschen scharf ist, statt „zuletzt …“.
- **Keine unterstrichenen Buttons mehr:** `TertiaryButton`/`TextButton` entfernt, Ersatz `SmallButton` (kompakte graue Pille, so breit wie
  der Text). Alle Verwendungen umgestellt; Listenzeilen mit Chevron bleiben Zeilen. `ConfirmButton(textOnly)` entfällt.
- **Bestätigungs-Buttons:** ohne Rahmen. Zentral `ConfirmArming` (CompositionLocal) + `ConfirmArea` um die ganze App: höchstens einer scharf
  (der nächste entschärft den vorigen), ein Tipp irgendwo anders entschärft (Pointer-Input im Initial-Pass am Root, nichts wird verbraucht;
  ein Tipp innerhalb der Grenzen des scharfen Buttons zählt als Bestätigung). 4-s-Rücksetzung bleibt. Test in `UiFeedbackTest`.
- **Flächen statt Haarlinien für Übungs-/Satzlisten (Editor und Workout-Modus):** `Block` (surface, 16 dp Radius) je Übung, `SetRowSurface`
  (surfacePressed, 12 dp) je Satz, `SetField` (eigene Fläche, große Zahl, Einheit „KG“/„WDH.“), `BlockButton` („+ Satz hinzufügen“,
  „+ Übung hinzufügen“), `MoreMenu` (⋮: Ersetzen, Nach oben/unten, Entfernen). Farben bleiben unsere Palette. Übrige Listen (Verlauf,
  Einstellungen) behalten Haarlinien.
- **Satz nach links wischen = löschen:** rote Fläche hinter der Zeile, ab 40 % Breite wird gelöscht, sonst federt die Zeile zurück.
  Danach 5 s „Satz gelöscht · Rückgängig“ über dem Fuß; Rückgängig stellt nur her, solange seitdem nichts anderes geändert wurde (sonst
  würde es neue Eingaben überschreiben). Screenreader bekommen „Satz n entfernen“ als Aktion. Der Drag liegt auf dem festen äußeren
  Rahmen, nicht auf der gleitenden Zeile (sonst schrumpfen die Deltas). Das ×-Icon je Satz entfällt. Tests: Komponente + Editor.
- **Editor-Kopf:** Name groß als Eingabe mit Stift daneben (fokussiert das Feld), darunter „Bearbeitungsmodus“; Übungsbild mit „?“ öffnet
  die Übungsdetails. Langes Drücken auf den Übungskopf verschiebt weiter per Ziehen.

## Ticken vor Pausenende (Session AG, 3.11.)
- **Was:** Bei 3, 2 und 1 s Restzeit je ein kurzer Klick, danach wie bisher das Pausenende (Benachrichtigung/Vibration). Schalter
  „Ticken vor Pausenende“ im Profil unter „Vibration“, Standard an (fehlt das Feld im gespeicherten Zustand, gilt an). Eine Pause, die
  beim Planen (z. B. nach −15 s) weniger als 3 s übrig hat, tickt nicht (kein halber Countdown).
- **Ein Ton für alle drei Ticks:** `RestTicks` (commonMain) erzeugt eine 2,06-s-Tonfolge synthetisch (Sinus-Burst 1,8 kHz, 45 ms, schnell
  abklingend, 0/1/2 s), keine fremden Sounds. So muss jede Plattform nur *einen* Ton zum Zeitpunkt Ende − 3 s starten, und die Ticks
  bleiben untereinander exakt im Takt. Planung (`RestTicks.startAt`, Toleranz `onTime` 700 ms) ist gemeinsamer, getesteter Code.
- **Android:** Zweiter exakter Alarm (`setExactAndAllowWhileIdle`, ohne Erlaubnis die inexakte Variante) bei Ende − 3 s auf
  `RestTickReceiver`. Neu planen ersetzt ihn (gleicher PendingIntent), Überspringen/Verwerfen/Beenden bricht ihn ab, Einstellung aus
  oder < 3 s übrig = kein Tick-Alarm (Robolectric-Test). Kommt der Alarm mehr als 0,7 s zu spät (keine Exakt-Erlaubnis), entfällt das
  Ticken, statt falsch zu zählen. Der Receiver spielt per `AudioTrack` mit `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` (Medienstrom, also
  Medienlautstärke wie die Musik) und Audiofokus `GAIN_TRANSIENT_MAY_DUCK`: Spotify wird kurz leiser, nicht pausiert. `goAsync()` hält
  den Broadcast offen, solange der Ton läuft; damit deckt der Wakelock des Alarms die 2 s ab – kein eigener Wakelock, kein
  Foreground-Service. Der Alarm spielt auch, wenn die App offen ist (die App selbst tickt auf Android nie), so gibt es nie doppelte Ticks.
  ±15 s während des Tickens stoppt den Ton. **Lautlos-Modus:** Ticks hörbar (Medienstrom ist vom Klingelmodus unabhängig); nur bei
  Medienlautstärke 0 still. „Bitte nicht stören“ lässt Medien standardmäßig durch.
- **iOS:** Im Hintergrund läuft kein App-Code, daher eine zweite lokale Benachrichtigung bei Ende − 3 s („Pause · Noch 3 Sekunden“)
  mit eigenem Ton. Der WAV wird einmal nach `Library/Sounds` geschrieben (dort darf iOS Benachrichtigungstöne laden, kein
  Bundle-/XcodeGen-Eingriff nötig; Name versioniert, weil iOS Töne cacht). Im Vordergrund zeigt iOS die Benachrichtigung ohne Delegate
  nicht an, dann spielt die App denselben Ton selbst (`WorkoutController` fragt `RestAlerts.appPlaysTicks()` = App aktiv) über
  `AVAudioPlayer` mit Kategorie `playback` + `mixWithOthers` + `duckOthers`, danach Sitzung freigeben mit `notifyOthersOnDeactivation`
  (Musik wird wieder laut). **Lautlos-Modus:** im Vordergrund hörbar (Kategorie playback ignoriert den Schalter); im Hintergrund nicht,
  weil iOS Benachrichtigungstöne im Lautlos-Modus stummschaltet – umgehen ließe sich das nur mit „Critical Alerts“ (Apple-Sonderfreigabe
  für Gesundheit/Sicherheit) oder einer dauerhaften Hintergrund-Audiositzung (Store-Ablehnungsrisiko, Akku). Dann bleibt die Vibration
  der Benachrichtigung. Lautstärke im Hintergrund = Klingel-/Hinweislautstärke, nicht Medien (iOS-Vorgabe).
- **Pausen-Benachrichtigung nicht wegwischbar (Zusatz, Fabian: „Manchmal geht die Notification einfach weg“):** Seit Android 14
  lassen sich auch `ongoing`-Benachrichtigungen wegwischen (Ausnahme nur Foreground-Services in bestimmten Fällen, und auch die seit
  14 nicht mehr). Lösung: Die Countdown-Benachrichtigung hat einen Delete-Intent (`RestDismissedReceiver`), der sie sofort neu postet,
  solange die Pause noch ≥ 1 s läuft. Alle Daten dafür stecken im Intent, es klappt also auch, wenn der Prozess beendet war.
  Programmatisches Abbrechen (Überspringen, Training beenden/verwerfen) und das Zeitlimit am Pausenende lösen keinen Repost aus.
  **Kein Foreground-Service:** Er brächte keinen Schutz vor dem Wegwischen (Android 14+), bräuchte einen FGS-Typ (`specialUse` mit
  Play-Begründung oder `health` mit Sensor-Berechtigung) und eine Dauerbenachrichtigung über das ganze Training; zu arbeiten gibt es
  im Hintergrund nichts (Alarme erledigen Ende und Ticks). Andere Ursachen fürs Verschwinden geprüft: Sie endet absichtlich am
  Pausenende (Zeitlimit + Ersatz durch „Pause vorbei“) und gibt es zwischen den Pausen nicht (keine Trainings-Dauerbenachrichtigung).
  Kanal „Pausentimer“ hat niedrige Wichtigkeit (lautlos), wird dadurch aber nicht ausgeblendet; Doze betrifft nur Alarme, die sind
  `AllowWhileIdle`. Eine Benachrichtigung über das ganze Training wäre ein eigenes Feature (offen, falls Fabian sie möchte).
- **Abschnitt „Benachrichtigungen“ in den Einstellungen (Fabian: „in den settings soll es ein Notifications Absatz geben“):** eigene
  Überschrift unter „Training“. Inhalt: Berechtigungsstatus („Erlaubt“ bzw. „Erlauben“ + Hinweis), exakte Alarme (nur wenn nicht
  erlaubt, Android 12+, nötig für pünktliches Ticken und Pausenende), Vibration (aus „Training“ verschoben, gespeicherter Wert
  unverändert), Ticken, Hinweis zur Countdown-Benachrichtigung. „Erlauben“: Android 13+ zeigt den Systemdialog; kommt „abgelehnt“
  in < 400 ms zurück, hat Android keinen Dialog mehr gezeigt (zweimal abgelehnt) → App-Benachrichtigungseinstellungen öffnen.
  Unter Android 13 gibt es keine Laufzeit-Berechtigung, dann direkt die Einstellungen. iOS: nie gefragt → Systemdialog, sonst
  die App-Seite in der Einstellungen-App. Status wird bei jedem Zurückkehren in die App neu gelesen.
- **Verworfen:** Ticks als drei einzelne Alarme (drei Wakeups, Takt schwankt), Foreground-Service während der Pause (Android 14+
  braucht dafür einen Typ, `shortService` ist begrenzt, dauernd sichtbare Benachrichtigung gibt es schon), Ticks im
  Benachrichtigungskanal (Kanalton läuft über den Klingelstrom und ist im Lautlos-Modus stumm).

## Feedback-Runde 4 (Session AF, 3.11.)
- **Beispiele nach „Daten löschen“:** `resetAll()` löschte den Flag `samples_seeded_v1`, säte aber erst beim nächsten Start neu. Jetzt sät `resetAll()` Push/Pull/Legs sofort (einmalig, wie beim ersten Start). Backup-Wiederherstellung ruft `resetAll(reseedSamples = false)`: wiederhergestellte Daten bekommen keine zusätzlichen Beispiele. Im Normalbetrieb gelöschte Beispiele bleiben weg (Flag bleibt gesetzt).
- **Schwarzer Screen nach Workout löschen:** `templates.templates` ist ein `combine` zweier Room-Abfragen und emittiert nach dem Löschen mehrfach. `WorkoutPlanScreen` rief bei jeder Emission mit „Workout weg“ `onBack()` auf; ein zweiter Pop entfernte auch den Training-Tab, der Back-Stack war leer. Jetzt schließt der Screen genau einmal.
- **Kalender:** immer sichtbar, auch ohne Foto/Training (Hinweistext darüber). Tag nur mit Training: gefüllter Punkt in der Kachel. Foto und Training: Foto, plus kleiner Punkt oben links.
- **Gewichte nur Plus:** ± -Schalter entfernt (Editor und Training), das Eingabefeld lässt kein „-“ mehr zu. Vorhandene negative Werte (z. B. importierte Hilfsgewichte) werden nicht gelöscht oder migriert und weiter angezeigt; beim Bearbeiten des Feldes fällt das Minus weg, der Wert wird positiv. Keine Room-Migration; Parser/Import bleiben tolerant.
- **Gewichte nur Plus, KI-Seite (Wunsch Manager):** Coach-Prompt verlangt `weight_kg` ≥ 0; `CoachParser` und Share-/Chat-Import (`ShareFormat`) klemmen negative Werte auf 0 (eine Stelle je Eingang, deckt Plan-Diff, Coach-Chat-Workouts und Import ab). Gespeicherte alte Werte bleiben, die Verlaufsanzeige zeigt sie weiter.
- **Satzzeile (Punkt 5, Screenshot 0.1.227):** Ursache war der ±-Knopf, der die Felder auf ca. 40 dp quetschte (Platzhalter brach um). Mit dem Entfernen des Knopfes + `SetField`: Mindestbreite 52 dp, Platzhalter und Einheit einzeilig (`maxLines = 1`, kein Umbruch), Feld füllt die Breite, rechtsbündig. Platzhalter ist „–“ statt der Einheit (keine doppelte Einheit neben dem „KG“-Label). Test `SetRowLayoutTest` (360 dp, DE/EN, Gewicht×Wdh., Gewicht×Zeit, leer).

## Übungsbild antippen → Details, Frame-Animation (Session AH, 3.11.)
- **Auftrag (Fabian):** „Wenn ich, egal wo, auf das Icon drücke, soll die Übung in Vollbild wie aus dem Katalog angezeigt werden mit der geplanten Animation.“ Das hebt „Nur Frame 1, keine Animation“ (SPEC) für die Detailansicht auf; SPEC angepasst.
- **Wo Übungsbilder vorkommen:** nur Katalog-/Picker-Zeile, Workout-Editor, laufendes Training (`ExerciseImage`-Aufrufe; Verlauf, Coach-Auswertung, Plan-Vorschau und „Workout ansehen“ zeigen keine Bilder, dort gibt es nichts anzutippen). Jetzt öffnet das Bild überall `ExerciseDetailScreen`: Katalog (wie die Zeile), Picker (nur das Bild; die Zeile wählt weiter aus), Editor (war schon so, „?“-Badge), Training (neu).
- **Aus dem Training schreibgeschützt (`ExerciseDetailRoute.readOnly`):** Die Detailseite liegt als eigene Route über dem Workout (Zurück → Training, Timer/Pausenleiste/Controller laufen unberührt weiter). Tracking, Pausenzeit, „Speichern“ sowie Bearbeiten/Löschen eigener Übungen sind dort ausgeblendet: Änderungen an der Übung mitten im Training würden die laufenden Sätze unvorhersehbar betreffen. Name, Muskeln, Equipment, Fortschritt bleiben. Editor und Picker behalten die bisherigen Optionen.
- **Tippfläche und Beschreibung:** `ExerciseImage(onClick = …)` hat mindestens 48 dp und die Beschreibung „Details zu <Name>“ / „Details for <Name>“ (`exerciseDetails`, DE+EN).
- **Große Detailansicht (`ExerciseHero`):** bildschirmbreit und quadratisch statt 160 dp. Frames 1→2→3→2→1, 700 ms je Frame, harter Wechsel (kein Crossfade: ruhiger und billiger; alle drei Frames bleiben komponiert, nur einer sichtbar, damit beim Wechsel nichts nachlädt), Theme-Farbe wie bisher. Listen-Icons bleiben Frame 1. Kein weiterer Frame bzw. kein SVG: Standbild.
- **Frame-Auflösung:** Konvention `catalog/wg/<slug>.svg`, `<slug>-2.svg`, `<slug>-3.svg` (`ExerciseImages.frameRefs`); der Import schreibt immer alle drei (bricht ab, wenn einer fehlt). `exercises.json` und die DB bleiben unverändert (kein Room-Eingriff). Test: `ExerciseFramesTest` prüft, dass zu jedem Katalogbild alle Frames im Bundle liegen.
- **„Animationen reduzieren“:** Nicht umgesetzt, weil es in Compose Multiplatform keinen einfachen gemeinsamen Zugriff auf die Systemeinstellung gibt (bräuchte `expect/actual`). Die Animation ist ruhig (harter Wechsel, 1,4 Hz) und nur in der Detailansicht; als Folgeaufgabe vermerkt.
- **Netz:** Frames 2/3 werden einmalig vom Import-Skript (gepinnter Commit `aac5992`) geholt und eingecheckt; zur Laufzeit kein Netz, `OwnKeyPrivacyTest` unberührt.
- **Dateigröße:** Alle SVGs (auch Frame 1) laufen im Import durch `shrink()`: Dezimalzahlen im Attribut `d` auf 1 Nachkommastelle gerundet (Grafik 512×512, Abweichung < 0,05 px, nicht sichtbar). Das ist eine Änderung im Sinne von CC BY-SA 4.0; der Hinweis in „Über Overfit“ (`aboutWorkoutGuide`) nennt sie. Vorher/nachher siehe PR.

## Hotfix kaputte Übungsbilder (Hotfix-Session Bilder, 3.11.)
- **Fabian:** „Die Animationen werden nicht richtig angezeigt. Generell werden die Bilder nur noch total verbuggt angezeigt.“
- **Ursache:** `shrink()` aus Session AH rundete Zahlen per Regex `-?\d*\.\d+`. SVG-Pfade schreiben Zahlen kompakt ohne Trenner (`-8.002.392` = `-8.002` und `.392`). Aus `8.002` wurde `8`, direkt gefolgt von `.4` → `8.4`: zwei Zahlen verschmolzen, alle folgenden Parameter verrutschten. Betroffen waren 905 von 906 Dateien (Chromium-Vergleich mit der Quelle: >1 % abweichende Pixel, bis 29 %). Frame-Zuordnung, Theme-Farbe und Takt (`ExerciseHero`, 1→2→3→2→1, 700 ms) waren korrekt.
- **Fix:** Echter Pfad-Parser im Import (Befehle, Parameterzahl, implizite Wiederholung, Arc-Flags, Exponenten), Ausgabe mit Trenner überall dort, wo zwei Zahlen sonst verschmelzen würden, **2 Nachkommastellen** (Abweichung ≤ 0,005 px). Jede Datei wird nach dem Schreiben zurückgeparst und gegen das Original geprüft (gleiche Befehle, Zahlen ±0,005), sonst bricht der Import ab. Größe aller 906 Frames 21,8 MB (Quelle 26,4 MB; kaputter Stand 17,4 MB). Korrektheit vor Größe.
- **Regressionstest `ExerciseSvgIntegrityTest` (JVM):** alle 906 Dateien parsen streng, Pfadgrenzen innerhalb der viewBox (±2 px); 15 Frames (5 Übungen inkl. der am stärksten betroffenen und einer mit q/t-Befehlen) werden mit Java2D gerastert und mit den unveränderten Quell-SVGs (`shared/src/androidHostTest/svg-reference/`, CC BY-SA 4.0, gepinnter Commit) verglichen (<0,5 % Pixel); Selbsttest, dass der Vergleich zwei verschiedene Frames unterscheidet. Mit dem kaputten Stand von main schlägt der Test fehl.
- Lizenzhinweis „Über Overfit“ nennt jetzt zwei Nachkommastellen.
- **Sonnet-Gegencheck:** alle 906 Dateien in Chromium gerendert (neu max. 0,044 % Abweichung, main 905 über 1 %), `shrink(original)` byte-gleich zur eingecheckten Datei, 20 000 Fuzz-Pfade ohne Fehler, Animation 1→2→3→2→1 bestätigt. Umgesetzt: Lizenz-/Quellenhinweis für `svg-reference/`, Referenz mit `q`/`t`-Befehlen ergänzt.


## Coach-Story statt Erklärtexte (3.11.)
- Die Story zeigt vier Wege/Inhalte: Auswertung allgemein, eigener Schlüssel, Guthaben (inkl. Einladungsbonus), eigenes Abo (Prompts/Chat-Verbindungen). Das sind die real vorhandenen Wege im Code. Seite 4 führt zur Claude-Verbindung (Prompts bleiben auf der Seite).
- Die Story ist eine eigene Route ohne Tab-Leiste (Vollbild). Das Erstes-Mal-Flag liegt im `AppStore` (KeyValueStore), nicht im Profil-State, damit Backup/Restore es nicht beeinflusst.
- Auto-Weiter nur bis zur letzten Seite; die letzte Seite schließt nie von selbst. Tests schalten das Auto-Weiter per `coachStoryAutoAdvance = false` ab (virtuelle Testuhr würde sonst bei jedem Warten Seiten überspringen).
- Die Zeile „Deine Daten gehen direkt an …“ bleibt auf der Coach-Seite (Status, vom OwnKeyPrivacyTest geprüft) und steht zusätzlich in Story Seite 2.
- Gegenprüfung durch einen unabhängigen Sonnet-Agent (Regel von Fabian): Seite 4 führt jetzt zur Übersicht „Verbindungen“ (KI-Coach-Screen) statt zu Claude, damit die Story keinen Anbieter vorwählt und ohne Backend nichts verspricht; Text sagt nur „Prompts kopieren, normales Abo nutzen“. Seite 1 nennt die Demo ohne Zugang; Seite 3 verspricht kein Aufladen (je Vertriebskanal verschieden). Tipp-Erkennung als Tap-Geste am Container (Buttons nehmen ihre Taps zuerst), Mittelspalte scrollbar (große Schrift), Überschrift als Heading, Seitenzähler als Live-Region. Bewusst nicht geändert: Aktionsknopf bleibt `SmallButton` (ruhiger, Story ist kein Formular-Screen); Auto-Weiter ohne Pause-Geste (bleibt auf der letzten Seite stehen, Tippen setzt die Zeit zurück).
## Sauna-Häkchen nach dem Training (Session AK, 3.11.)
- **Auftrag (Fabian):** „Es soll eine Checkpunkt nach dem Training für Sauna geben (einfach (x) Sauna). Das soll mit als Punkt mit im Kalender angezeigt werden und auch den KI gegeben werden.“
- **Datenmodell:** `workout_session.sauna` (Boolean, NOT NULL, Default 0). Room v7 → v8 als `AutoMigration` (reine Spaltenergänzung mit Default, wie v6 → v7); Migrationstest `migration7to8KeepsWorkoutsAndAddsSaunaDefaultFalse`. Kein eigener Tabelleneintrag, weil Sauna immer an genau ein Training gebunden ist; Sauna ohne Training gibt es bewusst nicht.
- **UI:** schlichte Zeile „Sauna“ mit quadratischem Häkchen (`CheckRow`, neu in `ui/components`: umrandet = aus, invertiert gefüllt mit Haken = an) im Abschluss-Screen direkt unter „Gefühl“, Standard aus. Gespeichert wird mit „Speichern“/Zurück, und zwar vor `onSaved`, damit die dort startende Coach-Auswertung den Wert schon sieht. In der Trainingsdetail-Ansicht (Verlauf) sofort umschaltbar (ändert `updatedAt`, KI-Chat-Sync zieht nach).
- **Kalender:** gefüllter Punkt = Training (wie bisher), hohler Ring = Sauna. Tag ohne Foto: Ring unter dem Punkt. Tag mit Foto: Ring neben dem kleinen Punkt oben links, innen mit der Seitenfarbe gefüllt, damit er auf dem Foto lesbar bleibt. Legende („● Training ○ Sauna“) nur, sobald es mindestens einen Sauna-Tag gibt, damit der Kalender für Nicht-Nutzer unverändert bleibt. Screenreader: „…, Sauna“.
- **KI:** Coach-Auswertung: Kopfzeile jedes Trainings endet mit „sauna: yes“; der statische System-Teil erklärt das immer (`SAUNA_NOTE`, bleibt byte-gleich fürs Caching). KI-Chat-/„Kontext kopieren“-Text: „· danach Sauna“ / „· sauna afterwards“. KI-Chat-Sync (Backend-Dokument `workout`): `"sauna": true` nur wenn angehakt; Backend liest fehlend = false und gibt `sauna` in `list_workouts` aus (`get_workout` reicht das Dokument ohnehin durch). Keine neue Anfrage, `OwnKeyPrivacyTest` unberührt.
- **Backup:** `BWorkout.sauna` mit Default false, ältere Backups ohne Feld importieren als „keine Sauna“. **Teilen:** Sauna wird wie Gefühl und Notiz nicht geteilt (persönliche Angabe, das Share-Format beschreibt nur die Übungen).
- Zweiter Gegencheck (Sonnet): Wischen nach unten wird im Initial-Pass ohne Konsumieren erkannt (die scrollbare Mittelspalte verschluckte die Geste); TalkBack bekommt die Aktion „Zurück“ als Custom Action am Container, das X ist `Role.Button`; Test-Teardown wartet auf Idle vor dem Schließen der DB. Nicht umgesetzt: Timer-Pause beim Halten (optional, Auto-Weiter stoppt ohnehin auf der letzten Seite). Offen bleibt iOS (Geste/VoiceOver nur in der iOS-CI, `story:close` als Identifier im UI-Test).

## Verlauf-Tab entfernt, Kalender-Tag, Trainings nachträglich bearbeiten (Session AL, 3.11.)
- **Auftrag (Fabian):** „Der History-Tab soll komplett entfernt werden … im Kalender auf das Training tippen … soll sich das Training öffnen und man soll auch im Nachhinein Trainings bearbeiten können, zum Beispiel das mit der Sauna … Bilder hinzufügen oder entfernen können an dem Tag.“
- **Navigation:** Vier Tabs. Die Verlaufsliste entfällt ersatzlos; die Kalender-Statistik (Trainings, Fotos, Wochen in Folge) bleibt oben im Kalender.
- **Tab-Namen und Icons (Ergänzung Fabian: „passendere Icons und zutreffendere Namen … nicht Photos oder so“):** je ein Wort, DE/EN:
  - **Workouts / Workouts** (Klemmbrett mit Plan, `Icon.Workouts`; vorher „Training“ mit Blitz): Der Tab listet die gespeicherten Pläne, und laut Begriffsregel ist „Workout“ der Plan, „Training“ die durchgeführte Einheit. Die Hantel bleibt dem Übungskatalog-Knopf auf derselben Seite vorbehalten.
  - **Coach / Coach** (Sprechblase, unverändert): Chat mit dem KI-Coach, Name und Icon passen.
  - **Kalender / Calendar** (Kalenderblatt, `Icon.Calendar`; vorher „Fotos“ mit Kamera): enthält jetzt Trainings und Fotos pro Tag. „Tagebuch“ wäre auch gegangen, „Kalender“ beschreibt aber direkt, was man sieht.
  - **Profil / Profile** (Kopf und Schultern, `Icon.Profile`; vorher Schieberegler): dort stehen Profil, KI-Zugang und Einstellungen; das Personen-Icon passt zum Namen.
  - `TabBarTest` prüft auf 360 dp (DE hell, EN dunkel), dass genau diese vier Tabs da sind und jedes Label in einer Zeile vollständig in seinen Tab passt. Verweise im Text („Training → „+““) sagen jetzt „Workouts → „+““.
- **Tipp auf einen Tag** (jeder Tag bis heute, auch leere; Zukunft nicht antippbar; im Vergleichsmodus wie bisher Foto-Auswahl) → Tagesansicht `DayRoute(date)`: Trainings des Tages als Liste (Uhrzeit, Dauer, Sätze, Volumen, Fotos, Sauna) → Trainingsdetail; darunter Fotos (antippen = Vollbild, „×“ → „Entfernen?“ → zweiter Tipp entfernt, gleiches Bestätigungsmuster wie überall) und „Foto aufnehmen“ / „Aus Fotos wählen“. Bisher öffnete ein Fototag direkt das Vollbild; jetzt immer erst der Tag, damit Fotos und Trainings an einer Stelle liegen.
- **Fotos ohne Training (DB v9):** `workout_photo.sessionId` ist nullable (AutoMigration 8→9; Room baut die Tabelle neu und kopiert alle Zeilen, Fremdschlüssel CASCADE und Indizes bleiben). Migrationstest `migration8to9KeepsPhotosAndAllowsPhotoWithoutWorkout`. Ein in der Tagesansicht hinzugefügtes Foto hängt am letzten Training des Tages (Coach und Trainingsdetail sehen es), sonst an keinem. Zeitpunkt: heute = jetzt, frühere Tage = 12:00 Uhr Ortszeit, damit es sicher auf dem angetippten Tag landet (`dayPhotoTarget`). „Alle Daten löschen“/Backup-Restore löschen Fotos jetzt explizit (sonst blieben Fotos ohne Training als Zeilen stehen; vom Sonnet-Testagenten gefunden). **Training löschen behält dessen Fotos** am Kalendertag (sie werden Fotos ohne Training); ein Foto verschwindet nur, wenn man es selbst entfernt (Befund des Sonnet-Gegenchecks: sonst hätte das Löschen eines Trainings auch nachträglich am Tag hinzugefügte Fotos mitgenommen). Backup: `BPhoto.workoutId` nullable, alte Backups unverändert lesbar; ein Backup mit Fotos ohne Training trägt Version 2, damit eine ältere App „App aktualisieren“ meldet statt „nicht lesbar“. Backups ohne solche Fotos bleiben Version 1. Vollbild: ohne Training keine Zeile „Training ansehen“. KI-Chat-Sync (`ChatPhotoRef.workoutId`) war schon nullable.
- **Trainingsdetail:** wie bisher (Teilen, Löschen, Als Workout speichern, Coach-Auswertung, KI-Chat-Notizen, Fotos), neu mit Stift oben rechts „Training bearbeiten“. Das Sauna-Häkchen ist dort nicht mehr direkt umschaltbar, sondern Teil des Bearbeitens (ein klares Speichern/Verwerfen statt zweier Wege); angezeigt wird „Sauna ✓“. Die Ansicht lädt nach jeder Änderung neu (`WorkoutRepository.changes`).
- **Bearbeiten (`WorkoutEditRoute`):** Gefühl, Sauna, Notiz und alle Sätze mit denselben Eingaben wie im Training (`WorkoutSetRow`, aus `WorkoutScreen` geteilt): Gewicht/Wdh./Zeit ändern, abhaken/öffnen, „Satz hinzufügen“ (zählt als erledigt, Zeitpunkt = Trainingsende), Satz per Wischen entfernen. Übungen hinzufügen/ersetzen/umsortieren bewusst nicht (nicht verlangt, würde den Bearbeiten-Screen zum zweiten Workout-Editor machen). Werte nie negativ (Eingabe erlaubt kein Minus, `nonNegative()` als Absicherung). „Speichern“ schreibt alles in einer Transaktion (`updateFinished`: Status, Start/Ende, Name, KI-Status und Fotos bleiben); Zurück mit Änderungen → „Nicht gespeichert. Nochmal Zurück zum Verwerfen.“ wie im Workout-Editor.
- **Folgen:** Statistik, Bestwerte, Übungsfortschritt, Coach-Kontext und KI-Chat-Sync lesen aus den Satz-Zeilen und nutzen damit die neuen Werte (Sync über `onChanged`). Gespeicherte Coach-Auswertungen werden nicht verändert (eigene Tabelle); der Bearbeiten-Screen sagt das.
- **Bildlader (Nebenbefund Session AI):** `setSingletonImageLoaderFactory` setzte Coils prozessweiten Lader während der Komposition. Hatte vorher irgendein Screen ein Bild gezeichnet (z. B. `DiaryScreenTest` im selben Robolectric-Prozess), gab es schon Coils Standard-Lader und `setSafe` warf. Jetzt stellt Android den Lader über die Application bereit (`OverfitApp : SingletonImageLoader.Factory`), iOS setzt ihn in `MainViewController` vor dem ersten Screen (`overfitImageLoader()` in `ui/components/ImageLoading.kt`). Damit nutzen auch Einzel-Screen-Tests denselben Lader (mit SVG).

## CI-Wackler: Room-Teardown in UI-Tests und iOS-Simulator-Boot (Session CI-Flaky)
- **Room „connection pool has been closed“ (`CoachStoryTest`, ähnlich möglich in jedem UI-Test mit DB):** `@After` läuft *innerhalb* der Compose-Rule; die Komposition lebt dann noch und sammelt Room-Flows, `database.close()` rennt gegen diese Abfragen. Lokal nicht deterministisch reproduzierbar (3 Klassen je 5× in einer JVM: grün), die Ursache folgt aus der Rule-Reihenfolge. Behebung für alle Robolectric-UI-Tests: neue Rule `AppTeardown` (`app/src/test/.../ui/AppTeardown.kt`) mit `order = 0`, die Compose-Rule `order = 1`. Sie läuft damit nach dem Abbau der Komposition, bricht dann `AppData.scope` ab und schließt zuletzt die DB. Die 17 UI-Tests mit DB nutzen sie; manuelle `@After`-Closes entfallen (Regel in CLAUDE.md). `UiFeedbackTest` (DB pro `launchApp()`) schloss die DB nie und hat jetzt ebenfalls den Teardown; sein sporadischer Fehler (Inhalt noch nicht geladen) war schon früher durch Warten auf sichtbaren Text behoben.
- **iOS-Simulator-Boot (Run 41740930529):** Der Schritt „Simulator wählen und starten“ bootet mit 240-s-Timeout (`perl alarm`, macOS hat kein `timeout`) bis zu 3-mal (= 2 Wiederholungen): Versuch 2 fährt alle Simulatoren herunter und setzt das Gerät zurück (`erase`), Versuch 3 legt es neu an (`delete` + `create`). Erfolg heißt `bootstatus -b` ok *und* „Booted“ in `simctl list`; sonst Fehler mit Geräteliste. Vor `xcodebuild test` wird nochmal geprüft, dass das Gerät „Booted“ ist. Kein Test-Retry, nur Infrastruktur.
## Zahlenfelder im Training (Session AM, 4.11.)
- **Auftrag (Fabian):** „immer noch die Zahlenfelder im Workout Modus, eine 1 ist nicht lesbar. Ist ein Textfeld wirklich so schwer? Wenn bestätigt wird soll auch der Cursor raus (oder zum nächsten Eintrag springen).“
- **Ursache (belegt, `docs/screenshots/audit/vorher/satz-*.png`):** `SetField` nutzte den Titelstil (34 sp) in Feldern von 40–80 dp Breite (360-dp-Handy, Einheit „KG“/„WDH.“ daneben). Passt der Text nicht, scrollt ein einzeiliges `BasicTextField` waagerecht: „102,5“ und „1000“ erschienen leer, bei Schrift 1,3 auch „11“ in Wdh.; „1“ passte gerade noch knapp. Frühere Versuche (u. a. PR #58) haben Breiten verschoben, die Schrift aber zu groß gelassen.
- **Lösung:** schlichtes Feld (48 dp hoch, eigener Stil `Theme.type.number` = 20 sp SemiBold mit tabellarischen Ziffern `tnum`, zentriert, 1-dp-Rand, beim Fokus dunkler Rand). Die Einheit steht einmal als Spaltenkopf über den Sätzen (`SetColumnsHeader`: „SATZ · KG · WDH.“ bzw. „SET · KG · REPS“, Zeit „SEK.“/„sec“), dadurch beide Felder gleich breit; „1000“ passt auch bei Schrift 1,3. Platzhalter (Zielwert) grau-normal statt sehr hellgrau, damit er lesbar ist, aber nicht wie ein eingetragener Wert aussieht.
- **Tastatur:** Gewicht → „Weiter“ springt zu Wdh./Zeit, letzte Eingabe des Satzes → „Weiter“ zum ersten Feld des nächsten Satzes derselben Übung, beim letzten Satz „Fertig“: Tastatur zu, Cursor weg. Abhaken eines Satzes nimmt den Fokus ebenfalls weg. Gilt im Training, im Workout-Editor und beim Bearbeiten eines Trainings. Alle übrigen einzeiligen Felder (Suche, Notiz, Pause, Schlüssel, Einstellungen) schließen bei „Fertig“ ebenfalls Tastatur und Cursor.
- **Fokus-Fänger:** Nach `clearFocus()` gibt Android den Fokus an die Root-View zurück, Compose setzt ihn dann auf das erste fokussierbare Element – das erste Textfeld (Cursor und Tastatur wären wieder da; im Robolectric-Test reproduziert). `OverfitTheme` legt deshalb ein unsichtbares 1-dp-`focusTarget` an den Anfang, das diesen Fokus aufnimmt. Test: `SetFieldFocusTest`.

## Panels statt Haarlinien, Audit-Funde (Session AM, 4.11.)
- **Auftrag (Fabian):** „Beachte auch Panels, welche sich nicht vom Hintergrund abheben, davon gibt es noch viele.“ Damit ändert sich die Regel „Haarlinien statt Cards“ (CLAUDE.md angepasst).
- **Komponente (`ui/components/Components.kt`):** `Panel` = Fläche `surface` (hell #F2F2F7 auf Weiß, dunkel #1C1C1E auf Schwarz), 1-dp-Rand und 1-dp-Trennlinien in `hairline`, 16-dp-Ecken; keine Schatten, keine Akzentfarbe. `PanelSection(titel)` = Abschnittstitel + Panel, `PanelItem` für freien Inhalt, `Hint` für kurze graue Erklärtexte direkt unter dem Panel (Fabian: „Inline Texte finde ich auch okay und modern“). `ListRow`/`CheckRow` und eigene Zeilen über `Modifier.panelRow()`/`RowDivider()` erkennen über `LocalInPanel` selbst, ob sie im Panel stehen; graue Knöpfe, Chips und Segmente werden im Panel eine Stufe dunkler, damit sie sichtbar bleiben. Notizfelder (`MultilineInput`) sind eigene umrandete Flächen.
- **Wo:** Profil/Einstellungen, Workout-Liste, Workout-Detail, Neues Workout, Teilen/Import, Trainingsabschluss (Zahlen, Bestwerte, Gefühl/Sauna), Trainingsdetail (Zahlen, Notiz, je Übung ein Panel), Kalendertag, Coach-Tab und Coach-Screens, KI-Chat, Guthaben/Einladungen/Web-Aufladen, Backup, KI-Zugang, Übungsdetail und -formular. Fließtext, Überschriften und der eine `PrimaryButton` bleiben frei. Workout-Editor und laufendes Training hatten schon graue `Block`-Flächen.
- **Weitere Audit-Funde (360 dp, `docs/screenshots/audit/tour-vorher` → `tour-nachher`):** „Darstellung“ brach in den Einstellungen mitten im Wort um → `ListRow` setzt das Steuerelement in die nächste Zeile, wenn beides nicht passt (FlowRow). Knopftexte („Satz hinzufü…“, „Übe…“, „Beenden? Nochmal tipp…“) wurden abgeschnitten → Knopftext schrumpft bis 12 sp, bevor er gekürzt wird. Übungsnotizen im Training/Editor waren einzeilig abgeschnitten → bis 3 Zeilen. Eingabe in Einstellungszeilen ist so breit wie der Wert (min. 64 dp, 48 dp hohe Tippfläche) statt fest 120 dp. Profil-Tab hieß oben „Einstellungen“ → „Profil“ wie der Tab; der Abschnitt „Profil“ darin heißt jetzt „Über dich“. „Standard-Pause (Sekunden)“ → „Standard-Pause (s)“. Graue Hilfstexte im Trainingsdetail (offene Sätze) von „faint“ auf „muted“, weil sie auf der Panelfläche sonst kaum lesbar wären.

## Popups für Bestätigungen (Session AM, 4.11.)
- **Auftrag (Fabian):** „Baue gerne auch PopUps ein, die mag ich.“ Bisher: Doppeltipp-Muster („Beenden? Nochmal tippen“), das sich nach 4 s von selbst zurücksetzte und auf 360 dp abgeschnitten war.
- **Eine Komponente (`ui/components/Dialogs.kt`):** `OverfitDialog` (zentrierte, runde Fläche mit 1-dp-Rand auf abgedunkeltem Hintergrund, Tippen daneben oder Zurück schließt) und `ConfirmDialog` (Frage als Titel, optional kurze Erklärung, „Abbrechen“ + Aktion; rot für Zerstörendes, schwarz für Positives). `ConfirmButton` und `HeaderDeleteButton` öffnen jetzt diesen Dialog statt sich „scharf“ zu schalten; Signaturen bleiben, neu `message` und `actionLabel` (kurzer Aktionstext, z. B. „Verwerfen“).
- **Wo:** Training beenden (auch „Laufendes beenden“), Training verwerfen, Workout/Training/Übung/Foto löschen, Chat löschen, Coach-Gedächtnis löschen, KI-Chat trennen/abschalten, alle Daten löschen, Backup einspielen („Alle Daten durch das Backup ersetzen?“), Foto vom Kalendertag entfernen. Beim Löschen eines Workouts sagt der Dialog, dass bisherige Trainings im Kalender bleiben (vorher kurz unter dem Titel).
- **Bewusst nicht:** Auswahlen und kurze Eingaben bleiben inline (Segmente, Felder, ⋮-Menü), weil sie dort schneller sind; kurze Erklärtexte bleiben inline (`Hint`). „Nicht gespeichert. Nochmal Zurück zum Verwerfen.“ beim Zurück aus Editoren bleibt ein Hinweis, weil der Zurück-Weg (auch Systemgeste) dort schon die Bestätigung ist.
- **Tests:** Dialogknöpfe tragen die Tags `dialog:confirm`/`dialog:cancel`; `UiFeedbackTest` prüft Abbrechen (nichts passiert) und Bestätigen (Aktion genau einmal). Robolectric zeichnet Dialogfenster nicht in Screenshots, deshalb `DialogShotTest` mit `DialogCard` (Belege `docs/screenshots/audit/popups/`). `ConfirmArming`/`ConfirmArea` bleiben vorerst ungenutzt im Code (nur `ConfirmArea` hüllt die App noch ein), Aufräumen später.
- **Nachtrag: Kriterien aus dem impeccable-Skill (`.claude/skills/impeccable/reference/clarify.md`, `android.md`, `audit.native.md`, über PR #72 bereitgestellt):** (1) „Für zerstörende Aktionen Objekt und Folge nennen“ → Dialogtitel „Workout löschen?“, „Training löschen?“, „Übung löschen?“, „Chat löschen?“ statt „Wirklich löschen?“; die Folge steht dort, wo sie nicht offensichtlich ist (Workout: Trainings bleiben; KI-Chat trennen: Server-Daten weg). (2) „Tippflächen mindestens 48 dp“ → Icon-Knöpfe 48 dp (vorher 44), Suchfeld-Löschen 48 dp (vorher 32), kleine Pillen-Knöpfe und Chips bleiben sichtbar 40 dp, haben aber 4 dp Tippfläche ober- und unterhalb. (3) „Text darf bei realistischen Breiten nicht brechen“ → `SmallButton` schrumpft wie die anderen Knöpfe, Pausenleiste enger, „Überspringen“ ganz lesbar. (4) „Dialoge nur für Entscheidungen, die unterbrechen müssen“ → bestätigt die Auswahl oben (keine Popups für Auswahlen/Eingaben). Material-3-Komponenten, Roboto und Dynamic Color aus `android.md` gelten nicht: Overfit-Designsystem (schwarz-weiß, Foundation, Inter) hat laut CLAUDE.md Vorrang.

## Kalender als Liste, Workouts als schwebende Karten (4.11.)
- **Umschalter:** Rechts oben in der Titelzeile des Kalender-Tabs ein `HeaderIconButton` (48 dp) mit neuem `Icon.List`; er zeigt immer die andere Ansicht (Liste in der Kalenderansicht, Kalender in der Listenansicht), Beschreibung „Als Liste anzeigen“ / „Als Kalender anzeigen“. Die Wahl steht im `KeyValueStore` (`diary.view`, `DiaryViewPref`) und gilt beim nächsten Start. „Vergleichen“ gibt es nur in der Kalenderansicht (Auswahl über Kalenderfelder), darum fehlt der Knopf in der Liste.
- **Liste:** neueste oben, nach Monat gruppiert, je Monat ein `Panel`; Zeile = Tageszahl + Wochentag, Workout-Name(n), Dauer · Sätze · Volumen · Sauna (über alle Trainings des Tages summiert), Foto-Vorschau 48 dp rechts. Tage nur mit Foto zeigen „n Fotos“. Tipp öffnet dieselbe Tagesansicht wie der Kalender. Leer: „Noch nichts in deiner Liste.“ plus Hinweis. Kein Eintrag für leere Tage (die gibt es nur im Kalender zum Foto-Hinzufügen).
- **`FloatingCard`** (`ui/components/Components.kt`): eigene Fläche (`surface`), 1-dp-Rand, `PanelShape`, Schatten 6 dp in Hell; in Dunkel ist ein Schatten auf Schwarz unsichtbar, dort trägt der kräftigere Rand (`faint`) die Abhebung. Gedrückt: `surfacePressed`. Abstand zwischen Karten `Space.m`. Abgrenzung: `Panel` für zusammengehörige Zeilen, `FloatingCard` für ein einzelnes, eigenständiges Element. Genutzt in der Workouts-Liste (`WorkoutRow`, Mindesthöhe 72 dp, ganze Karte tippbar).
- Belege: `docs/screenshots/kalender-liste/vorher|nachher` (360 dp, hell/dunkel).

## Übungsbild: Start | Ende statt Animation (Session AN, 4.11.)
- **Auftrag (Fabian, mit Screenshot einer anderen App):** „Ich will das auch, dass das so nebeneinander ist, weil die Animationen extrem komisch aussehen. Die Linien haben unterschiedliche Stärke, die sind an unterschiedlichen Positionen.“ Hebt die Frame-Animation aus Session AH auf.
- **Befund (alle 906 Frames in Chromium gerendert, Kontaktbögen aller 302 Übungen angesehen):** Frame 2 ist meist eine **dicker nachgezeichnete Kopie von Frame 1** (Bryl Lim, `animation frames`) – daher die springende Linienstärke. Frame 1 (meist direkt aus Everkinetic `…-tension.svg`) und Frame 3 zeigen die beiden Endpunkte der Bewegung. Die Figuren stehen je Frame an anderer Stelle im 512er-Raster. Auch Frame 1 und 3 haben oft unterschiedliche Linienstärke (Messung t = 2·Fläche/Umfang: bei 124 von 302 Paaren > 25 % Unterschied).
- **Lösung:** Detailansicht zeigt ein ruhiges Paar **Frame 1 + Frame 3** in einem `Panel` (zwei Flächen, 1-dp-Trennlinie, dezente Beschriftung „Start“/„Ende“, weil die Reihenfolge sonst nicht eindeutig ist). Frame 2 wird nicht mehr gebündelt. Listen-Icons bleiben Frame 1.
- **Welche Pose ist Start:** Standard Frame 3 = Ausgangsposition, Frame 1 = Endposition (Everkinetic „tension“). 32 Ausnahmen, bei denen Frame 1 die übliche Ausgangsposition zeigt (Drücken im Liegen mit gestreckten Armen, Dips oben, Kniebeuge im Stand, Kreuzheben-Varianten am Boden bzw. RDL im Stand, Negativ-Klimmzug oben …), von Hand festgelegt in `START_AT_FRAME_1` (`tools/catalog/import_catalog.py`). Bei Bewegungen ohne klare Richtung (Cardio, Dead Bug) gilt einfach der Standard.
- **Nur ein Bild (24 Übungen, `SINGLE_POSE`):** Halteübungen (Plank, Side Plank, Hangs, Wall Sit, Hollow/L-Sit/Superman Hold, Pallof Hold, Farmer Carry), fast gleiche Cardio-Frames (Stair Climber, Laufband) und die 10 statischen Dehnungen (`isStretch`). Die dynamischen „Stretches“ (Arm Circles, Cat-Cow, Leg Swings, World's Greatest) behalten das Paar. Das Skript bricht ab, wenn eine neue Dehnübung keiner Gruppe zugeordnet ist. Auch das Einzelbild wird auf die Figur zugeschnitten (Sonnet-Gegencheck: Plank ließ sonst das halbe Feld leer).
- **Gleiche Größe, gleiche Skalierung, gleicher Boden:** `tools/catalog/measure_frames.js` (Playwright/Chromium, nur Entwickler-Werkzeug) misst je Frame Bounding-Box und Linienstärke aus den unveränderten Quell-SVGs und schreibt `tools/catalog/frame_metrics.json` (eingecheckt). Der Import berechnet daraus `catalog/poses.json`: Reihenfolge, **ein** Ausschnitt `size` (größte Breite/Höhe beider Figuren + Rand) und je Pose dessen Ursprung, sodass die Figur mittig und mit der Unterkante auf derselben Linie steht. Die App zeichnet jeweils das ganze 512er-Bild im selben Maßstab und verschiebt es (kein Zoom pro Pose). Ausschnitte breiter als 1,4 : 1 (45 Übungen, z. B. Liegestütz, Crunch, Glute Bridge) stehen untereinander statt nebeneinander, sonst würden sie auf 360 dp winzig.
- **Linienstärke:** Die dünner gezeichnete Pose eines Paars bekommt im Import eine Kontur in Füllfarbe (`stroke-width` = Differenz der gemessenen Stärken, `stroke-linejoin="round"`, ab 0,3 Einheiten). Das macht jede Linie gleichmäßig dicker, die Form bleibt. Dünner machen geht nicht, deshalb immer auf die dickere angleichen. Betrifft auch Frame 1, also das Listen-Icon (gleiche Pose, etwas kräftigere Linie). Lizenzhinweis (`aboutWorkoutGuide`) nennt Kontur und Zuschnitt als Änderung (CC BY-SA 4.0).
- **Daten statt DB:** `poses.json` (≈ 25 KB, ein Eintrag je Übung) wird beim ersten Öffnen einer Detailansicht gelesen und im Speicher gehalten; `exercises.json` und Room bleiben unverändert (keine Migration). Größe der Übungsgrafiken 23 MB → 14 MB (580 statt 906 SVGs).
- **Tests:** `ExerciseFramesTest` (Stichproben der Reihenfolge, Einzelbilder, jede Paar-Datei vorhanden, Ausschnitte gleich groß und im Raster, keine ungenutzten Frames), `ExerciseSvgIntegrityTest` (580 Dateien, Kontur-Markup und nie auf beiden Posen; Referenzen ohne Frame 2). Belege `docs/screenshots/uebungsbild/vorher|nachher/` (vorher: die drei animierten Frames aus main; nachher: Robolectric, 360 dp, hell/dunkel, 10 Übungen).

## Nur Dunkelmodus, schlichte Schalter (Session AQ, 4.11.)
- **Auftrag (Fabian):** „Nimm den Light mode raus. Es gibt nur dunkel und du musst weniger testen.“ und „Die Switch buttons sollen wie normal links aus und rechts an sein … einfache switches ohne Schrift reichen.“
- **Immer dunkel:** `ThemeMode`, `LightColors`, `Colors.isDark` und die Zeile „Darstellung“ (System/Dunkel/Hell) im Profil sind weg; `OverfitTheme { }` nimmt keinen Modus mehr. Gespeicherte Einstellungen mit `"theme"` lesen sich weiter (der Store nutzt `ignoreUnknownKeys`), beim nächsten Speichern fällt das Feld weg – keine eigene Migration nötig (Test `olderStateWithLightThemeStillDecodes_themeIsGone`). Android: Status- und Navigationsleiste immer helle Symbole auf dunkel (`SystemBarStyle.dark`), Fenster- und Splash-Hintergrund waren schon schwarz. iOS: `UIUserInterfaceStyle = Dark` und `UIStatusBarStyleLightContent` im Info.plist (XcodeGen-Spec), `IosAppearance` erzwingt Dark zusätzlich an allen Fenstern (Tastatur, Teilen-Blatt, Picker); Startargument `-overfitTheme` entfällt. Die Web-Seite (`web/`) bleibt unverändert.
- **FloatingCard:** Der Schatten war nur im Hell-Modus sichtbar und entfällt; Rand `faint` und hellere Fläche tragen die Abhebung.
- **Tests:** Hell-Varianten der Screenshot-Tests entfallen bzw. laufen jetzt dunkel (Robolectric `ScreenshotTest`, `ExerciseHeroShotTest`, `CalendarListShotTest`, `SetFieldShotTest`, `DialogShotTest`, `TabBarTest`; iOS `testScreensLight`, `testOnboardingLight`). Das ist kein Abschalten von Tests, sondern Wegfall des Features. Neu `DarkOnlyAndSwitchTest.systemLight_appIsStillDark` (Robolectric `notnight`: Palette = `DarkColors`, Hintergrund schwarz). Auf iOS läuft der Simulator in seiner Standard-Darstellung hell; die Screenshots zeigen trotzdem dunkel.
- **Schalter (`SwitchRow`):** Statt der Segmente „An | Aus“ (An stand links, wirkte umgekehrt) ein schlichter Schalter ohne Text: 52 × 32 dp Spur, Knopf 24 dp; aus = Knopf links, schwarze Spur mit 1,5-dp-Rand `muted` (≥ 3 : 1 auf Schwarz, `faint` wäre nur ~2 : 1; Sonnet-Gegencheck), grauer Knopf; an = Knopf rechts, weiß gefüllte Spur, schwarzer Knopf. Knopf gleitet (`BiasAlignment`, folgt der Schreibrichtung, in RTL gespiegelt). Ganze Zeile tippbar, Semantik `Role.Switch` + an/aus-Zustand für TalkBack/VoiceOver. Genutzt für Vibration, Ticken vor Pausenende, Coach „Fotos senden“, KI-Chat „Fotos“ (Einwilligung und Sync). Texte „An/Aus“ entfernt. Tests: Knopfposition aus/an, RTL gespiegelt, kein „An/Aus“-Text, Rolle Switch.

## Doppeltipp statt Popups (Session AQ, 4.11.)
- **Auftrag (Fabian):** „Diese Pop-ups finde ich nicht sehr gut. Das soll wieder wie früher sein … Reicht einfach, wenn ein weißer Mülleimer rot wird und man dann nochmal drauf tippt.“ Hebt „Popups für Bestätigungen“ (oben) auf.
- **Muster:** Erster Tipp schaltet „scharf“, zweiter Tipp innerhalb von 3 s (`CONFIRM_ARMED_MILLIS`, vorher 4 s) führt aus. Sonst fällt der Knopf zurück; ebenso bei Tipp woanders, beim Scrollen bzw. Ziehen (auch wenn der Finger auf dem Knopf aufsetzt, `ConfirmArea` prüft die Touch-Slop) und wenn ein anderer Knopf scharf geschaltet wird. Kurzes Haptik-Feedback beim Scharfschalten.
- **Aussehen scharf:** Icon-Knöpfe (Mülleimer im Kopf, „×“ am Tagesfoto) – nur das Icon wird rot. Text-Knöpfe (`ConfirmButton`: Beenden, Laufendes beenden, Training verwerfen, Alle Daten löschen, Chat löschen, Coach-Gedächtnis löschen, Key entfernen, KI-Chat trennen, Backup einspielen) – Beschriftung und 1,5-dp-Rahmen rot, **Text bleibt gleich** (kein „Nochmal tippen“, also keine Breitenänderung und kein Abschneiden auf 360 dp). Der schwarze Hauptknopf „Backup einspielen“ wird dabei hell mit rotem Rahmen.
- **Rot = Token `Colors.armed`** (`#FF453A` wie `negative`, nur dunkel, da es keinen Hell-Modus mehr gibt; Kontrast auf Schwarz 5,5 : 1): bewusst die einzige Akzentfarbe im Bedienrahmen, nur für scharf geschaltete Bestätigungen. Auch „Beenden“ wird rot (nicht grün): ein einziges, sofort lernbares Signal „gleich passiert etwas, das man nicht zurücknehmen kann“.
- **Barrierefreiheit:** Scharf bekommt der Knopf `stateDescription` „zum Bestätigen erneut tippen“ / „tap again to confirm“; Icon-Knöpfe zusätzlich die Beschreibung „Löschen – zum Bestätigen erneut tippen“, damit TalkBack/VoiceOver es beim Fokus ansagen. Test-Tag (iOS: accessibilityIdentifier) `confirm:armed` nur im scharfen Zustand.
- **Entfernt:** `Dialogs.kt` (`OverfitDialog`, `ConfirmDialog`), `Confirm`-Enum, `DialogShotTest`, die Popup-Texte (Fragen wie „Workout löschen?“, „Abbrechen“ bleibt für Editoren). Die Zusatzinfos aus den Popups entfallen: „Trainings bleiben im Kalender“ (Löschen eines Workouts) war nur ein Beruhigungssatz; „KI-Chat trennen“ hat darunter schon den Hinweis `disableHint`.
- **Tests:** `UiFeedbackTest` (erster Tipp scharf mit Tag und Zustandsbeschreibung, Tipp woanders, gegenseitiges Entschärfen, zweiter Tipp führt genau einmal aus, Icon-Beschreibung, Timeout knapp davor/danach, Scrollen). Alle Flows tippen zweimal (zweiter Tipp auf `confirm:armed`). iOS-XCUITest: wartet nach dem ersten Tipp auf das Element `confirm:armed` und tippt genau dieses an (kein Koordinaten-Doppeltipp); war die Zeit inzwischen abgelaufen (langsamer Screenshot), ist es danach wieder scharf und bekommt einen weiteren Tipp.

## Karten in Listen (Session AQ, 4.11.)
- **Auftrag (Fabian):** „Diese schwebenden Karten sollen größtenteils in die Listen übernommen werden … wenn man ein Training anschaut, dann sollen auch die Übungen einzelne Karten sein mit den Bildern links, so wie das auch während dem Training dargestellt wird.“
- **Eine Kopfzeile für alle Übungskarten (`ExerciseCardHeader`, `Blocks.kt`):** Übungsbild 48 dp (Frame 1, Tipp öffnet die Übungsdetails), Name, bis zu zwei graue Zeilen. Das laufende Training nutzt sie selbst (plus Pfeil und ⋮), ebenso Workout-Detail, Trainingsdetail und „Training nachträglich bearbeiten“; so sehen Ansehen und Trainieren gleich aus, nur ohne Eingabe. Der Workout-Editor hatte die Karte schon (Bild mit „?“).
- **`Block` = Karte:** bekam den 1-dp-Rand (`faint`) von `FloatingCard`; alle Übungsblöcke (laufendes Training, Editor, Bearbeiten) sind damit sichtbar eigene Karten. Abstand zwischen Übungskarten `Space.s` wie im laufenden Training.
- **Wo jetzt Karten:** Workout-Detail (je Übung, „4 Sätze · 60 kg × 8“), Trainingsdetail (je Übung mit „1/4 erledigt“, Hinweis hinzugefügt/ersetzt und den Sätzen darunter), Kalender-Liste (je Tag eine `FloatingCard`, Abstand `Space.m`), Tagesansicht (je Training eine `FloatingCard`). Tipp aufs Bild im Workout-Detail, Trainingsdetail und „Training nachträglich bearbeiten“ öffnet die Übung schreibgeschützt (wie im Training); der Workout-Editor behält sein Bild mit „?“ (öffnet die bearbeitbaren Details, wie bisher).
- **Abstände:** zwischen eigenständigen Objekten (Workouts, Tage, Trainings) `Space.m` wie in der Workouts-Liste, zwischen Übungskarten `Space.s` wie im laufenden Training (dichter, weil sie zu einem Workout gehören).
- **Bewusst Panels geblieben:** Einstellungen/Profil, Zusammenfassungen (Dauer, Sätze …), Notizen und der Übungskatalog. Der Katalog ist eine Auswahl-Liste mit 300 Einträgen und Suche; Karten mit Abständen würden die Hälfte weniger Einträge pro Bildschirm zeigen, ohne dass ein Eintrag „eigenständig“ angesehen wird.
- Foto-Vorschau in der Kalender-Liste: Platzhalter `surfacePressed`, weil er sonst auf der Kartenfläche verschwindet.
- **Tests:** `ExerciseImageTapTest.workoutDetail_exerciseCardImageOpensReadOnlyDetails` (Karte mit Satz-Zusammenfassung, Bild → Details ohne Speichern), `trainingDetail_exerciseCardImageOpensDetails`. Belege `docs/screenshots/karten/vorher|nachher` (360 dp, dunkel).

## Knöpfe wie in der Banking-App-Vorlage (6.11.)
- Fabian: „So sollen die Buttons aussehen und etwas kleiner animiert werden wenn man sie tapped. Keinen Rahmen haben wie aktuell.“
- `PrimaryButton`: weiß, ohne Rand, 64 dp, Radius 24 dp, Text schwarz, fett (18 sp), linksbündig, optional `icon` rechts. `SecondaryButton`/`SmallButton`/`Chip`: dunkelgrau (`surface`), Text weiß; kompakte Knöpfe 44 dp sichtbar + 2 dp Rand = 48 dp Tippfläche.
- `Modifier.pressScale()` (Scale 0,96 in 100 ms, Feder beim Loslassen, kein Ripple, keine Farbänderung) auf allen Knöpfen, Chips, Icon-Knöpfen (0,88) und klickbaren `FloatingCard` (0,98). Eine eigene Einstellung „reduzierte Bewegung“ gibt es im Projekt nicht; Compose überspringt die Animation aber selbst, wenn die System-Animationsskala 0 ist.
- Scharf geschaltet (Doppeltipp): Text-Knopf voll rot (`Colors.armedFill` #D32F2F) mit weißem Text, kein Rahmen. Eigenes Token, weil Weiß auf dem Icon-Rot #FF453A nur 3,4:1 hat, auf #D32F2F 5:1. Icon-Knöpfe (Mülleimer, Foto-×) bleiben rotes Icon.
- Karten: `FloatingCard` ohne Rand, Radius 24 dp; die Fläche #1C1C1E hebt sich wie in der Vorlage vom Schwarz ab. `Panel` (Gruppen mit Trennlinien) behält Rand und 16 dp, weil der Rand dort die Trennlinien liefert und sie keine Knöpfe sind.
- Kontrast (`ButtonContrastTest`): Schwarz auf Weiß, Weiß auf Dunkelgrau (auch im Panel), Weiß auf `armedFill` je ≥ 4,5:1. Belege `docs/screenshots/buttons/vorher|nachher/`.
- Gegencheck-Befunde: Karten 0,98 und Icons 0,88 sind bewusst (große Fläche wirkt bei 0,96 zu wuchtig, kleines Icon sieht bei 0,96 nicht gedrückt aus). Zeilen in Panels (`pressableRow`) behalten die graue Druckfläche ohne Scale, weil sich eine Zeile innerhalb der Trennlinien nicht verkleinern soll. Tippflächen aller Galerie-Knöpfe ≥ 48 dp sind per Test abgesichert (`ButtonShotTest`).
