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
  Stufen hero/title/body/label/caption. Dunkel als Standard, Hell/System wählbar. Erledigte Sätze später über Kontrast +
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
- **Scannen:** Android in der App mit CameraX + ZXing core (Apache-2.0, keine Play-Dienste). **iOS: kein In-App-Scanner,
  die Kamera-App reicht**, weil der QR ein Link ist (`overfit://`-Link öffnet die App direkt, der https-Link den
  Browser/die Web-Seite mit „In Overfit öffnen“). Grund: AVFoundation-Scanner lässt sich unter Linux nicht testen, und der
  Nutzen gegenüber der Kamera-App ist klein. Die Import-Seite zeigt auf iOS einen Hinweis statt des Scan-Knopfs.
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
- **Duo:** Nur ein Teaser-Eintrag „Duo · bald“ auf dem Startbildschirm und ein Text-Screen, keine Datenerhebung.
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
- **Duo-Modus** (zwei Handys trainieren gemeinsam: synchrone Sätze, gemeinsamer Pausentimer) wird nur angeteasert: Eintrag
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
  Profil → Coach → Coach-Einstellungen änderbar. Fuel ist fest `claude-sonnet-5-5` (Backend erlaubt nur dieses). `effort`
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
- **Farbpalette:** Schwarz-weiß bleibt die Basis. Neu in `Colors` (Theme.kt), je hell/dunkel:
  `surface` (graue Hervorhebung: markierte Zeile, Chip, Auswahl, Eingabefeld), `surfacePressed` (gedrückt/gezogen),
  `positive` (grün: besser als zuvor, Bestwert, erledigt), `negative` (rot: schlechter, verpasst, destruktive Bestätigung)
  und die blassen Hintergründe `positiveSubtle`/`negativeSubtle` (z. B. Delta-Badge in der Auswertung).
  Werte angelehnt an die iOS-Systemfarben (gut lesbar auf Schwarz bzw. Weiß). Grün/Rot nur für Bedeutung in Daten,
  nie als Deko oder Markenfarbe; Buttons bleiben schwarz-weiß.
- **iOS-CI pausiert:** macOS-Minuten kosten 10x und waren am 3.11. rund 90 % der Actions-Kosten. `ios.yml` läuft nur noch
  per „Run workflow“. Ersatz: Die Android-CI kompiliert `iosMain` (`:shared:compileKotlinIosSimulatorArm64`, Linux).
  Gemergt wird bei grüner Android-CI; die iOS-CI vor einem iOS-Release oder nach größeren iOS-Änderungen von Hand starten.

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
- **Tab 1:** DE „Training“, EN „Workouts“ (im Englischen liest sich der Plural als Liste besser), Icon Blitz. Der Tab zeigt nur noch: laufendes Training oben („Läuft gerade“ + „Training fortsetzen“ als PrimaryButton), Liste der Workouts (Name, Übungen, „zuletzt vor N Tagen“), Coach-Tipps, Duo. Begrüßung und Gewicht sind raus (Gewicht steht im Profil). Der Übungen-Tab zeigt nur noch Übungen.
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
