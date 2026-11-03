# Overfit

Minimalistische App für Android und iOS zum Loggen von Krafttraining und Calisthenics – mit Pausentimer und KI-Coach.
Schwarz-weiß, Deutsch und Englisch, ohne Werbung, mit deinem eigenen API-Key. Gebaut mit Kotlin Multiplatform und Compose Multiplatform.
Spezifikation: [SPEC.md](SPEC.md) · Fortschritt: [docs/PROGRESS.md](docs/PROGRESS.md) · Entscheidungen: [docs/DECISIONS.md](docs/DECISIONS.md)

## App aufs Handy laden und installieren

1. Am Handy im Browser (z. B. Chrome) bei **github.com** einloggen – das Repo ist privat, ohne Login gibt es nur „404".
2. Im Repo auf **Releases** gehen und das Pre-Release **„Overfit latest"** öffnen.
   Direkter Weg: `https://github.com/Bigfire3/overfit/releases/tag/latest`
3. Unter **Assets** auf **`overfit.apk`** tippen. Der Download startet.
4. Download öffnen. Beim ersten Mal fragt Android, ob der Browser **„Unbekannte Apps installieren"** darf →
   in den Einstellungen für diesen Browser erlauben, zurück und **Installieren** antippen.
   (Das ist nur einmalig nötig.)
5. Play Protect warnt eventuell vor einer unbekannten App → „Trotzdem installieren".

## Updates

- Jeder Push auf `main` (also jeder gemergte Pull Request) baut in GitHub Actions ein neues APK und ersetzt
  `overfit.apk` im Release „latest". Die Release-Beschreibung zeigt Version, Branch und Commit.
- Pushes auf Arbeits-Branches (`claude/**`, `ccr-*`) bauen und testen nur. Ihr APK verändert „latest" nicht,
  liegt aber im jeweiligen Actions-Lauf unter **Artifacts** (`overfit-<Nummer>`, als ZIP) zum Vorab-Testen bereit.
- Zum Aktualisieren einfach wieder `overfit.apk` aus „latest" laden und installieren – Android meldet
  „Update installieren". **Keine Deinstallation nötig, alle Daten bleiben erhalten.**
- Das funktioniert, weil jedes APK mit demselben Schlüssel signiert ist (siehe „Signatur“) und der `versionCode`
  automatisch mit jeder CI-Ausführung steigt.
- Achtung: Ein **älteres** APK lässt sich nicht über ein neueres installieren (Android verbietet Downgrades).
- Feste Versionen: Ein Git-Tag `v*` (z. B. `v1.0`) erzeugt zusätzlich ein eigenes Release mit `overfit-v1.0.apk`
  und dem App Bundle `overfit-v1.0.aab` für Google Play.

## Signatur

Der Release-Schlüssel liegt **nicht** im Repo. CI liest ihn aus zwei GitHub-Actions-Secrets
(Settings → Secrets and variables → Actions → Repository secrets):

| Secret | Inhalt |
|---|---|
| `OVERFIT_KEYSTORE_BASE64` | PKCS12-Keystore (Alias `overfit`), Base64-kodiert in einer Zeile |
| `OVERFIT_KEYSTORE_PASSWORD` | Passwort für Keystore und Schlüssel |

Fehlen die Secrets, bricht der Android-Job nach den Tests mit einer klaren Meldung ab. Den Schlüssel nie ersetzen,
sonst lassen sich bestehende Installationen nicht mehr aktualisieren. Lokal signiert bauen:

```bash
OVERFIT_KEYSTORE_FILE=/pfad/overfit-release.p12 OVERFIT_KEYSTORE_PASSWORD=… ./gradlew :app:assembleRelease :app:bundleRelease
```

Ohne diese Variablen entsteht ein unsigniertes Release-APK.

## Projektaufbau

| Ordner | Inhalt |
|---|---|
| `shared/` | Kotlin Multiplatform: komplette UI (Compose Multiplatform) und Logik für Android und iOS |
| `app/` | Android-Hülle: Activity, Signatur, Release-APK und App Bundle |
| `iosApp/` | iOS-Hülle: XcodeGen-Spec und SwiftUI-Host für die gemeinsame UI |

## Selbst bauen

Voraussetzungen: JDK 21, Android SDK (Plattform 37). `local.properties` mit `sdk.dir=…` oder `ANDROID_HOME` setzen.

```bash
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleRelease
```

Das APK liegt danach unter `app/build/outputs/apk/release/` (signiert nur mit den Variablen aus „Signatur“).

### iOS (nur auf dem Mac)

Voraussetzungen: Xcode, JDK 21, [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
cd iosApp && xcodegen && open Overfit.xcodeproj
```

In Xcode einen iPhone-Simulator wählen und starten. Der Kotlin-Teil wird beim Build automatisch über Gradle gebaut.

## Lizenzen

Code: [Apache License 2.0](LICENSE).
Übungsgrafiken: Workout Guide (Bryl Lim, Everkinetic), CC BY-SA 4.0. Ergänzend free-exercise-db (Unlicense).
Schrift: Inter (SIL Open Font License 1.1, siehe `licenses/Inter-OFL.txt`). Details im „Über"-Screen der App.
