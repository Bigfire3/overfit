# Overfit

Persönliche Android-App zum Loggen von Krafttraining und Calisthenics – mit Pausentimer und KI-Coach.
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
- Das funktioniert, weil jedes APK mit demselben Schlüssel signiert ist (`signing/`) und der `versionCode`
  automatisch mit jeder CI-Ausführung steigt.
- Achtung: Ein **älteres** APK lässt sich nicht über ein neueres installieren (Android verbietet Downgrades).
- Feste Versionen: Ein Git-Tag `v*` (z. B. `v1.0`) erzeugt zusätzlich ein eigenes Release mit `overfit-v1.0.apk`.

## Selbst bauen

Voraussetzungen: JDK 21, Android SDK (Plattform 37). `local.properties` mit `sdk.dir=…` oder `ANDROID_HOME` setzen.

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Das signierte APK liegt danach unter `app/build/outputs/apk/release/app-release.apk`.

## Übungskatalog neu erzeugen

Der Katalog (`app/src/main/assets/catalog/`) wird mit einem Skript aus zwei fest gepinnten Quellen gebaut
(Workout Guide und free-exercise-db, Commit-SHAs oben im Skript):

```bash
python3 tools/catalog/import_catalog.py
```

Das Skript braucht nur Python 3, lädt die Quellen (Cache unter `tools/catalog/.cache/`), führt Dubletten
zusammen (Workout Guide hat Vorrang) und schreibt `exercises.json` plus die Workout-Guide-SVGs. Beim nächsten
App-Start spielt die App einen geänderten Katalog automatisch ein; eigene Einstellungen an Übungen bleiben erhalten.

## Lizenzen

Übungsgrafiken: Workout Guide (Bryl Lim, Everkinetic), CC BY-SA 4.0. Ergänzend free-exercise-db (Unlicense).
Details im „Über"-Screen der App.
