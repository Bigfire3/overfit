# Overfit – Arbeitsregeln für Claude Code

## Kontext
- Vollständige Spezifikation: SPEC.md. Fortschritt: docs/PROGRESS.md. Entscheidungen: docs/DECISIONS.md.
- Lies zu Beginn jeder Session CLAUDE.md und docs/PROGRESS.md. Aus SPEC.md nur die Abschnitte, die du für die aktuelle Phase brauchst.

## Arbeitsweise
- **Den Owner nur bei extrem wichtigen Entscheidungen fragen** (z. B. Tech-Stack, Zielgruppe, Geld, Datenverlust). Alles andere selbst sinnvoll entscheiden, umsetzen und in docs/DECISIONS.md bzw. im PR begründen.
- Aufgaben stehen zusätzlich als GitHub-Issues im Repo; Commits und PRs referenzieren die Issues.

## Architektur (Kurzfassung)
- Kotlin Multiplatform: `:shared` enthält UI (Compose Multiplatform) und Logik für Android und iOS. `:app` ist die Android-Hülle (Signatur, CI-APK), `iosApp/` die iOS-Hülle (XcodeGen-Spec + SwiftUI-Host).
- Designsystem in `shared/.../ui/theme` und `ui/components`: schwarz-weiß, keine Akzentfarbe, nur Compose Foundation (kein Material), Schrift Inter, Haarlinien statt Cards, ein `PrimaryButton` pro Screen, `LineIcon` für Icons.
- Alle Texte in `shared/.../i18n/Strings.kt` (Deutsch + Englisch, fehlende Übersetzung = Compile-Fehler). Keine Android-`strings.xml` für UI-Texte.
- Plattformcode nur über `expect`/`actual` bzw. Interfaces (`KeyValueStore`, `SecretStore`) in `androidMain`/`iosMain`.

## Phasen-Regel (spart Tokens)
- Pro Session genau eine Phase: die nächste offene laut PROGRESS.md, außer ich sage ausdrücklich etwas anderes.
- Phase fertig heißt: Build und Tests grün (lokal oder in CI), PROGRESS.md aktualisiert (erledigt, offen, was ich auf dem Handy testen soll), committet, gepusht, Pull Request nach main erstellt **und selbst gemergt** (siehe Git & CI). Dann STOPP und auf mich warten.

## Modelle & Effort
- Hauptsession: Opus 5.5, Effort high. Kein Ultracode, keine automatischen Workflows.
- Einfache, klar abgegrenzte Aufgaben an die Subagents in .claude/agents/ (kmp-worker, test-writer: Sonnet 5.5, effort medium). Sind die Agent-Dateien in der laufenden Session nicht verfügbar, nutze das Agent-Tool mit Modell Sonnet und effort medium.
- Bei dir bleiben: Architektur, Datenmodell + Migrationen, Workout-State, Pausentimer/Hintergrund-Logik, KI-Schnittstelle, Reviews der Subagent-Ergebnisse.
- Maximal 2 Subagents parallel.

## Sparregeln
- Nur Dateien lesen, die du brauchst. Keine kompletten Verzeichnis-Dumps.
- Build- und Testausgaben gekürzt (z. B. | tail -n 60), bei Fehlern gezielt die relevante Stelle ansehen.
- Gezielte Gradle-Tasks statt Komplett-Builds, Gradle-Daemon und Build-Cache nutzen.
- Web-Recherche nur für konkret benötigte Versionsnummern oder APIs.
- Kurze Zwischenberichte, keine langen Zusammenfassungen.
- Unklarheiten: vernünftige Annahme treffen, in docs/DECISIONS.md notieren, weitermachen. Nur bei echten Blockern fragen.

## Lokal bauen in der Claude-Umgebung
- Android SDK nach `~/android-sdk` installieren (cmdline-tools, `platforms;android-37.0`, `build-tools;36.0.0`), `local.properties` mit `sdk.dir`.
- Maven Central antwortet hinter dem Proxy mit 429 → Gradle immer mit `-I tools/claude-env-maven-mirror.gradle.kts` aufrufen (Google-Mirror, auch für Robolectric).
- Checks: `./gradlew -I tools/claude-env-maven-mirror.gradle.kts :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest :app:assembleRelease`
- iOS-Code lässt sich unter Linux kompilieren (Kotlin/Native-Klibs), aber nicht linken oder starten. Das prüft die iOS-CI auf macOS.

## Git & CI
- Du kannst nur auf deinen Session-Branch pushen. Android-CI baut auch dort, iOS-CI nur für PRs nach main. Am Phasenende PR nach main.
- **PRs selbst mergen** (Anweisung des Owners): sobald die CI (Android + iOS) auf dem aktuellen Stand grün ist, keine Merge-Konflikte und keine offenen Review-Kommentare bestehen. Draft-Status vorher aufheben, Merge-Methode „Merge commit“ wie bei PR #1. Bei roter CI oder offenen Fragen nicht mergen.
- Wird das Pushen der Workflow-Datei abgelehnt (fehlende Berechtigung), sag mir genau, was ich wo freigeben muss.
