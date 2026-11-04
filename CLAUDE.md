# Overfit – Arbeitsregeln für Claude Code

## Kontext
- Vollständige Spezifikation: SPEC.md. Fortschritt: docs/PROGRESS.md. Entscheidungen: docs/DECISIONS.md.
- Lies zu Beginn jeder Session CLAUDE.md und docs/PROGRESS.md. Aus SPEC.md nur die Abschnitte, die du für die aktuelle Phase brauchst.

## Arbeitsweise
- **Den Owner nur bei extrem wichtigen Entscheidungen fragen** (z. B. Tech-Stack, Zielgruppe, Geld, Datenverlust). Alles andere selbst sinnvoll entscheiden, umsetzen und in docs/DECISIONS.md bzw. im PR begründen.
- Aufgaben stehen zusätzlich als GitHub-Issues im Repo; Commits und PRs referenzieren die Issues.

## Architektur (Kurzfassung)
- Kotlin Multiplatform: `:shared` enthält UI (Compose Multiplatform) und Logik für Android und iOS. `:app` ist die Android-Hülle (Signatur, CI-APK), `iosApp/` die iOS-Hülle (XcodeGen-Spec + SwiftUI-Host).
- Designsystem in `shared/.../ui/theme` und `ui/components`: schwarz-weiß, keine Akzentfarbe (einzige Ausnahme: Token `Colors.armed`-Rot für scharf geschaltete Bestätigungen), nur Compose Foundation (kein Material), Schrift Inter, zusammengehörige Zeilen in `Panel`/`PanelSection` (graue Fläche, 1-dp-Rand, runde Ecken, Trennlinien; Ersatz für „Haarlinien statt Cards“, siehe DECISIONS 4.11.), einzelne Elemente einer Liste (Workouts) als eigene schwebende `FloatingCard` (Fläche, Rand, dezenter Schatten, Abstand dazwischen), kurze Erklärtexte als `Hint` darunter, ein `PrimaryButton` pro Screen, `LineIcon` für Icons. Bestätigungen per Doppeltipp (`ConfirmButton`, `HeaderDeleteButton`: erster Tipp färbt Icon bzw. Text+Rahmen rot, zweiter Tipp in 3 s führt aus), keine Popups.
- Skill „impeccable“ (Design-Audit, `/impeccable audit|critique|polish …`, Referenzen u. a. `reference/android.md`, `audit.native.md`; vendored unter `.claude/skills/impeccable`, Hooks in `.claude/settings.json`; die Hooks prüfen nur Web-Dateien wie `web/`, nicht Kotlin): Seine Hinweise sind Anregungen. Bei Widerspruch gilt das Overfit-Designsystem oben (schwarz-weiß, keine Akzentfarbe, Foundation, Panels). Für Compose gelten seine Regeln sinngemäß.
- Alle Texte in `shared/.../i18n/Strings.kt` (Deutsch + Englisch, fehlende Übersetzung = Compile-Fehler). Keine Android-`strings.xml` für UI-Texte.
- Plattformcode nur über `expect`/`actual` bzw. Interfaces (`KeyValueStore`, `SecretStore`) in `androidMain`/`iosMain`.

## Phasen-Regel (spart Tokens)
- Pro Session genau eine Phase: die nächste offene laut PROGRESS.md, außer ich sage ausdrücklich etwas anderes.
- Phase fertig heißt: Build und Tests grün (lokal oder in CI), PROGRESS.md aktualisiert (erledigt, offen, was ich auf dem Handy testen soll), committet, gepusht, Pull Request nach main erstellt und selbst gemergt (siehe Git & CI). Dann STOPP und auf mich warten.

## Modelle & Effort
- Hauptsession: Opus 5.5, Effort high. Kein Ultracode, keine automatischen Workflows.
- Einfache, klar abgegrenzte Aufgaben an die Subagents in .claude/agents/ (kmp-worker, test-writer: Sonnet 5.5, effort medium). Sind die Agent-Dateien in der laufenden Session nicht verfügbar, nutze das Agent-Tool mit Modell Sonnet und effort medium.
- Bei dir bleiben: Architektur, Datenmodell + Migrationen, Workout-State, Pausentimer/Hintergrund-Logik, KI-Schnittstelle, Reviews der Subagent-Ergebnisse.
- Maximal 2 Subagents parallel.
- **Gegencheck mit Sonnet** (Owner: „Überprüfe dich mit Sonnet am Agenten immer gegen.“): Vor jedem Merge prüft ein frischer Sonnet-Subagent (effort medium, zählt ins Limit oben) die Änderung unabhängig. Er bekommt Aufgabe und Diff, nicht die eigene Schlussfolgerung, sucht Fehler und prüft das Ergebnis selbst nach: Build/Tests ausführen, bei UI und Bildern Robolectric-Screenshots bzw. gerenderte Bilder ansehen (geht etwas lokal nicht, z. B. iOS, sagen, was offen bleibt). Befunde beheben oder begründet verwerfen und in der PR-Beschreibung bzw. im Bericht nennen. Nach größeren Fixes erneut prüfen lassen.

## Sparregeln
- Nur Dateien lesen, die du brauchst. Keine kompletten Verzeichnis-Dumps.
- Build- und Testausgaben gekürzt (z. B. | tail -n 60), bei Fehlern gezielt die relevante Stelle ansehen.
- Gezielte Gradle-Tasks statt Komplett-Builds, Gradle-Daemon und Build-Cache nutzen.
- Web-Recherche nur für konkret benötigte Versionsnummern oder APIs.
- Kurze Zwischenberichte, keine langen Zusammenfassungen.
- Unklarheiten: vernünftige Annahme treffen, in docs/DECISIONS.md notieren, weitermachen. Nur bei echten Blockern fragen.

## Tests
- UI-Tests (Robolectric/Compose): nie `compose.waitUntil { state }` mit reiner State-Bedingung, das pumpt den Main-Looper nicht und führt zu wackeligen Tests. Stattdessen `compose.waitUntilState { … }` (`app/src/test/.../ui/TestWaits.kt`) oder auf sichtbaren Text warten. Vor einem Klick sicherstellen, dass der Inhalt geladen ist (nicht nur der Button sichtbar).
- UI-Tests mit Room-Datenbank: `@get:Rule(order = 0) val teardown = AppTeardown({ data }, { database })` vor der Compose-Rule (`order = 1`), nie `database.close()` in `@After` (läuft noch innerhalb der Compose-Rule → „connection pool has been closed“).
- Tests nie abschalten, um grün zu werden. Ein wackeliger Test ist ein Fehler, den du findest und behebst.

## Lokal bauen in der Claude-Umgebung
- Android SDK nach `~/android-sdk` installieren (cmdline-tools, `platforms;android-37.0`, `build-tools;36.0.0`), `local.properties` mit `sdk.dir`.
- Maven Central antwortet hinter dem Proxy mit 429 → Gradle immer mit `-I tools/claude-env-maven-mirror.gradle.kts` aufrufen (Google-Mirror, auch für Robolectric).
- Checks: `./gradlew -I tools/claude-env-maven-mirror.gradle.kts :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :app:testDebugUnitTest :app:assembleRelease`
- iOS-Code lässt sich unter Linux kompilieren (Kotlin/Native-Klibs), aber nicht linken oder starten. Das prüft die iOS-CI auf macOS.

## Git & CI
- Du kannst nur auf deinen Session-Branch pushen. Android-CI baut auch dort (ohne Release), iOS-CI nur für PRs nach main (öffentliches Repo, kostenlos). Am Phasenende PR nach main.
- Eigene PRs nach main merge selbstständig, ohne zu fragen, sobald: Sonnet-Gegencheck erledigt (siehe Modelle & Effort), CI (Android und iOS) auf dem aktuellen Head grün, kein Merge-Konflikt, keine offenen Review-Kommentare. Draft-Status vorher aufheben. Merge-Methode: Merge-Commit. PRs anderer Sessions nicht mergen, sondern mir melden.
- Nur Merges nach main aktualisieren das Release „latest“ (neue APK fürs Handy). Nach dem Merge sag mir, was ich testen soll.
- Nach einem Merge Folgearbeit nicht auf dem alten Stand stapeln: Session-Branch neu von main starten.
- Wird das Pushen der Workflow-Datei abgelehnt (fehlende Berechtigung), sag mir genau, was ich wo freigeben muss.
