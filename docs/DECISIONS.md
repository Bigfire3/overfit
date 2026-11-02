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
