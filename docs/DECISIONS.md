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
