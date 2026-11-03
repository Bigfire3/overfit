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
- **Spot-Link im QR:** `https://bigfire3.github.io/overfit/spot#<token>` (Token im Fragment). Die Seite `/spot` existiert noch nicht (Web-Fallback zeigt dort 404); die App akzeptiert Link oder nacktes Token.
- **QR-Code:** eigener kleiner Encoder in `ui/components/QrCode.kt` (Byte-Modus, ECC M, Version 1–12), gegen Referenzmatrizen getestet. Der QR-**Scanner** kommt aus Session B: `AppDependencies.qrScanner` ist der Einhängepunkt; bis dahin lässt sich der Link einfügen.
- **Attestierung:** Android Play Integrity (Nonce = base64url(SHA-256(challenge))), iOS App Attest (clientDataHash = SHA-256(challenge); Token = JSON `{"keyId","attestation"(base64url)}`). Das Format gegenüber dem späteren Server-Prüfer ist damit festgelegt und muss dort so gelesen werden. Ohne Support/Konfiguration → `attestation_unavailable` + freundliche Meldung.
- **Reveal:** schwarzer Vollbildschirm (immer schwarz/weiß, unabhängig vom Theme), Haptik `Reveal` (zwei Pulse), 1,1 s Schwarz, Zahl zählt 2,4 s ease-out hoch (Inter, tabellarische Ziffern), am Ende `RevealFinish`; Tippen schließt erst nach dem Ende.
- **Haptics:** API 1:1 von Session A übernommen (`Haptics`, `HapticPattern`, `AndroidHaptics`, `IosHaptics`); beim Merge identisch.

### Zur Prüfung durch Fabian
- Spot-Link-Domain (`bigfire3.github.io/overfit/spot`) und die zugehörige Web-Seite.
- Name „Fuel · 4,20 €“ in EN als „€4.20“ formatiert (DE „4,20 €“).
