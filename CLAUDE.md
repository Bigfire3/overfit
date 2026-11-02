# Overfit – Arbeitsregeln für Claude Code

## Kontext
- Vollständige Spezifikation: SPEC.md. Fortschritt: docs/PROGRESS.md. Entscheidungen: docs/DECISIONS.md.
- Lies zu Beginn jeder Session CLAUDE.md und docs/PROGRESS.md. Aus SPEC.md nur die Abschnitte, die du für die aktuelle Phase brauchst.

## Phasen-Regel (spart Tokens)
- Pro Session genau eine Phase: die nächste offene laut PROGRESS.md, außer ich sage ausdrücklich etwas anderes.
- Phase fertig heißt: Build und Tests grün (lokal oder in CI), PROGRESS.md aktualisiert (erledigt, offen, was ich auf dem Handy testen soll), committet, gepusht, Pull Request nach main erstellt und selbst gemergt (siehe Git & CI). Dann STOPP und auf mich warten.

## Modelle & Effort
- Hauptsession: Opus 5.5, Effort high. Kein Ultracode, keine automatischen Workflows.
- Einfache, klar abgegrenzte Aufgaben an die Subagents in .claude/agents/ (android-worker, test-writer: Sonnet 5.5, effort medium). Sind die Agent-Dateien in der laufenden Session nicht verfügbar, nutze das Agent-Tool mit Modell Sonnet und effort medium.
- Bei dir bleiben: Architektur, Datenmodell + Migrationen, Workout-State, Pausentimer/Hintergrund-Logik, KI-Schnittstelle, Reviews der Subagent-Ergebnisse.
- Maximal 2 Subagents parallel.

## Sparregeln
- Nur Dateien lesen, die du brauchst. Keine kompletten Verzeichnis-Dumps.
- Build- und Testausgaben gekürzt (z. B. | tail -n 60), bei Fehlern gezielt die relevante Stelle ansehen.
- Gezielte Gradle-Tasks statt Komplett-Builds, Gradle-Daemon und Build-Cache nutzen.
- Web-Recherche nur für konkret benötigte Versionsnummern oder APIs.
- Kurze Zwischenberichte, keine langen Zusammenfassungen.
- Unklarheiten: vernünftige Annahme treffen, in docs/DECISIONS.md notieren, weitermachen. Nur bei echten Blockern fragen.

## Git & CI
- Du kannst nur auf deinen Session-Branch pushen. CI baut auch dort. Am Phasenende PR nach main.
- Eigene PRs nach main merge selbstständig, ohne zu fragen, sobald: CI auf dem aktuellen Head grün, kein Merge-Konflikt, keine offenen Review-Kommentare. Merge-Methode: Merge-Commit. PRs anderer Sessions nicht mergen, sondern mir melden.
- Nur Merges nach main aktualisieren das Release „latest“ (neue APK fürs Handy). Nach dem Merge sag mir, was ich testen soll.
- Nach einem Merge Folgearbeit nicht auf dem alten Stand stapeln: Session-Branch neu von main starten.
- Wird das Pushen der Workflow-Datei abgelehnt (fehlende Berechtigung), sag mir genau, was ich wo freigeben muss.
