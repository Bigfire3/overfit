# Overfit

Minimalistische App für Android und iOS zum Loggen von Krafttraining und Calisthenics – mit Pausentimer und KI-Coach.
Schwarz-weiß, Deutsch und Englisch, ohne Werbung, mit deinem eigenen API-Key. Gebaut mit Kotlin Multiplatform und Compose Multiplatform.
Spezifikation: [SPEC.md](SPEC.md) · Fortschritt: [docs/PROGRESS.md](docs/PROGRESS.md) · Entscheidungen: [docs/DECISIONS.md](docs/DECISIONS.md)

## Funktionen

- **Training loggen**: Übungskatalog (über 1000 Übungen, deutsche Suche, Filter), eigene Übungen, Vorlagen; Workout-Modus mit
  Sätzen abhaken, Werten, Notizen je Übung, Satz/Übung hinzufügen, ersetzen, umsortieren; läuft nach App-Neustart weiter
- **Pausentimer** mit ±15 s und Überspringen, auch im Hintergrund (Benachrichtigung, Alarm, Haptik; Schalter „Vibration“)
- **Beenden-Flow**: Zusammenfassung, neue Bestwerte, Gefühl, Notiz, Körperfotos (nur app-privat); **Verlauf** mit Details, Fortschritt je Übung
- **Foto-Tagebuch**: Zeitachse, Vollbild, zwei Fotos vergleichen
- **Teilen und Importieren**: Vorlagen und Trainings als Link, QR-Code oder Datei; Beispielvorlagen Push/Pull/Legs;
  **KI-Vorlage** per Chat (ChatGPT, Claude, Gemini) erstellen und aus der Zwischenablage einfügen
- **KI-Coach**: mit eigenem API-Key oder „Fuel“ (Coach-Guthaben); **KI-Chat-Verbindung** (Claude, ChatGPT, Gemini) oder „Kontext kopieren“
- **Fuel und Spot**: Guthaben in Euro; Freunde per QR „spotten“, beide bekommen 1 € Fuel
- **Backup** als eine ZIP-Datei (Export/Import), **Alle Daten löschen**
- Deutsch und Englisch, Hell/Dunkel/System, kg/lb, ohne Werbung und Tracking

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

## iPhone: TestFlight

Der Workflow „iOS TestFlight“ (`.github/workflows/ios-testflight.yml`, nur manuell) baut ein signiertes Archiv und lädt
es zu TestFlight hoch. Build-Nummer = Run-Nummer, Bundle-ID `app.overfit` (änderbar über die Variable `IOS_BUNDLE_ID`).
Einmalig einrichten:

1. **Apple Developer Program** beitreten: developer.apple.com/programs → „Enroll“ (99 €/Jahr, als Einzelperson mit
   Apple-ID, Bestätigung dauert meist 1–2 Tage).
2. **Team-ID** notieren: developer.apple.com/account → Membership details → „Team ID“ (10 Zeichen).
3. **App ID** (optional, Xcode legt sie sonst beim ersten Lauf selbst an): Certificates, Identifiers & Profiles →
   Identifiers → „+“ → App IDs → App → Bundle ID „Explicit“ `app.overfit`. Ist sie vergeben, eine andere wählen
   (z. B. `de.<dein-name>.overfit`) und in GitHub als Variable `IOS_BUNDLE_ID` eintragen.
4. **App in App Store Connect** anlegen: appstoreconnect.apple.com → Apps → „+“ → Neue App → Plattform iOS,
   Name „Overfit“ (falls vergeben: „Overfit Training“), Sprache Deutsch, Bundle-ID aus Schritt 3, SKU `overfit`.
5. **API-Schlüssel**: App Store Connect → Benutzer und Zugriff → Integrationen → App Store Connect API →
   Team-Schlüssel → „+“ → Name „GitHub“, Zugriff **Admin** → Erzeugen. Die `.p8`-Datei herunterladen (geht nur einmal),
   „Schlüssel-ID“ und oben „Issuer-ID“ notieren. (Admin statt App Manager: nur damit darf der Schlüssel das
   Verteilungszertifikat in der Cloud verwalten, das die automatische Signierung braucht; Begründung in DECISIONS.md.)
6. **Secrets in GitHub**: Repo → Settings → Secrets and variables → Actions → „New repository secret“:
   - `APP_STORE_CONNECT_KEY_ID` = Schlüssel-ID
   - `APP_STORE_CONNECT_ISSUER_ID` = Issuer-ID
   - `APP_STORE_CONNECT_KEY_P8` = kompletter Inhalt der `.p8`-Datei (mit `-----BEGIN PRIVATE KEY-----` und `END`-Zeile)
   - `APPLE_TEAM_ID` = Team-ID
7. **Bauen**: Actions → „iOS TestFlight“ → „Run workflow“. Fehlt ein Secret, bricht der Lauf sofort mit Liste der
   fehlenden Namen ab. Nach dem Upload verarbeitet Apple den Build (5–30 Minuten).
8. **Interne Tester**: App Store Connect → App → TestFlight → Interne Tests → „+“ Gruppe „Ich“ → dich (deine Apple-ID
   ist als Kontoinhaber automatisch Benutzer) hinzufügen, Build zur Gruppe hinzufügen. Interne Builds brauchen keine
   Beta-Review. Die Frage nach Verschlüsselung beantwortet die App selbst (`ITSAppUsesNonExemptEncryption = NO`).
9. **iPhone**: App „TestFlight“ aus dem App Store laden, mit derselben Apple-ID anmelden → Overfit → „Installieren“.
   Jeder neue Lauf erscheint dort als Update. TestFlight-Builds laufen 90 Tage.

### iPhone ohne bezahltes Konto (Mac nötig)

Mit einem Mac geht es auch kostenlos, aber umständlicher:

1. Xcode aus dem Mac App Store, dann JDK 21 und XcodeGen (`brew install openjdk@21 xcodegen`).
2. Repo klonen, `cd iosApp && xcodegen && open Overfit.xcodeproj`.
3. Xcode → Settings → Accounts → „+“ → Apple-ID (kostenlos) anmelden.
4. Target „Overfit“ → Signing & Capabilities → Team „<Name> (Personal Team)“, Bundle Identifier eindeutig machen
   (z. B. `de.<dein-name>.overfit`, die kostenlose Signierung verlangt eine noch nicht vergebene ID).
5. iPhone per Kabel anschließen, „Vertrauen“ tippen; am iPhone Einstellungen → Datenschutz & Sicherheit →
   Entwicklermodus einschalten (Neustart).
6. In Xcode oben das iPhone wählen → Run (⌘R). Beim ersten Start am iPhone: Einstellungen → Allgemein →
   VPN & Geräteverwaltung → Entwickler-App vertrauen.
7. Die App läuft **7 Tage**, dann neu aus Xcode installieren. Maximal 3 Apps gleichzeitig pro kostenloser Apple-ID.

## Übungskatalog neu erzeugen

Der Katalog (`shared/src/commonMain/composeResources/files/catalog/`) wird mit einem Skript aus Workout Guide gebaut
(302 Übungen mit SVG-Grafik, Commit-SHA oben im Skript). free-exercise-db ist seit 3.11.2026 entfernt; fehlende Übungen
legt man in der App als eigene Übung an (auf Wunsch per KI ausgefüllt).

```bash
python3 tools/catalog/import_catalog.py
```

Das Skript braucht nur Python 3. Es lädt die Quelle (Cache unter `tools/catalog/.cache/`) und schreibt `exercises.json`
und die Workout-Guide-SVGs. Danach den Katalog des Backends neu erzeugen: `cd backend && node scripts/build-catalog.mjs`.
Beim nächsten Start spielt die App einen geänderten Katalog automatisch ein. Eigene Einstellungen an Übungen bleiben
dabei erhalten.

## Lizenzen

Code: [MIT License](LICENSE). Beiträge sind willkommen, siehe [CONTRIBUTING.md](CONTRIBUTING.md).
Übungsgrafiken: Workout Guide (Bryl Lim), basierend auf Grafiken von Everkinetic, CC BY-SA 4.0 (unverändert
mitgeliefert, Lizenztext in `shared/src/commonMain/composeResources/files/catalog/LICENSE-ASSETS-workout-guide.txt`).
Schrift: Inter (SIL Open Font License 1.1, siehe `licenses/Inter-OFL.txt`). Details im „Über"-Screen der App.
