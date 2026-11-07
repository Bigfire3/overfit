# Manager-Übergabe (Koordinations-Session)

Für die Claude-Session, die Fabians Feedback koordiniert. Stand: 7.11.2026. Keine Secrets in diese Datei.

## Rolle
- Fabian (Owner) gibt Feedback, oft per Handy mit Screenshots, auf Deutsch. Der Manager setzt es nicht selbst um, sondern startet je Thema eine Sub-Session (`create_session`, source `https://github.com/Bigfire3/overfit`, Tag `overfit-feedback-3-11`, Titel „<Buchstabe>: <Thema>“) mit einem Briefing per `append_system_prompt`. Kleine Doku- oder Konfig-Änderungen macht der Manager selbst (eigener Branch, PR, nach Sonnet-Gegencheck und grüner CI selbst mergen).
- Fabian, wörtlich: „Der Entwicklungsprozess soll durch meine fehlende Zustimmung oder Meinung nicht aufgehalten werden.“ Selbst entscheiden. Ausnahmen: Geld und Recht entscheidet Fabian.
- Modelle (Fabian, 7.11.): Sub-Sessions und Umsetzungs-Subagents laufen auf Opus 5.5 (effort medium), der Gegencheck vor jedem Merge auf Sonnet 5.5 (effort high). Siehe CLAUDE.md.
- Antworten an Fabian kurz, deutsch, mit konkreten Handy-Testschritten und APK-Version. Push-Benachrichtigung nur bei neuer APK oder Wichtigem, wenn er wahrscheinlich weg ist.

## Ablauf je Sub-Session
1. Briefing: „## Rahmen“ (Repo, Koordination = Manager-Session-ID, Regeln aus CLAUDE.md, Fabian nichts fragen, Draft-PR, subscribe_pr_activity, Turn beenden statt pollen, nach ca. 20 min ohne Event Check-Runs selbst prüfen, Sonnet-Gegencheck, selbst mergen, parallele Sessions und Konfliktstellen nennen, Bericht per send_message) und „## Deine Aufgabe“ (Fabians Feedback wörtlich, dann konkrete Punkte und Testerwartungen).
2. Nach dem Bericht: Release „latest“ prüfen (`get_release_by_tag` mit Tag `latest`; `get_latest_release` liefert 404, weil Prerelease). Body nennt Version und Commit. Ein Merge nach main baut die APK in ca. 7–10 min: https://github.com/Bigfire3/overfit/releases/download/latest/overfit.apk
3. Fabian Version, Änderungen und Handy-Tests melden.

## Bekannte Stolpersteine
- **PR-Events gehen verloren.** Sub-Sessions warten dann stundenlang auf grüne CI. Bei Stillstand: `get_session` bzw. Check-Runs des PRs ansehen und die Session per `send_message` anstoßen.
- **iOS-CI dauert 25–30 min** (XCUITests im Simulator). Simulator-Boot hat seit #68 Timeout und Wiederholung. Ein roter Lauf, der den PR nicht betrifft: einmal neu starten, im PR kommentieren, Ursache an die zuständige Session geben.
- **create_session-Briefings** dürfen Fabians aktuellen Anweisungen nicht widersprechen, sonst lehnt das System das Anlegen ab.
- **Marketplace-Plugins laden in Cloud-Sessions nicht.** Deshalb liegt der Skill „impeccable“ im Repo (`.claude/skills/impeccable`, Hooks in `.claude/settings.json`, #72).
- **Backend-Auto-Deploy** nach Merges nur, wenn die Repo-Variable `BACKEND_DEPLOY_MODE` gesetzt ist. Sonst manuell: Actions → „Backend deployen“ → mode=test. Testserver: https://overfit-backend-test.overfit.workers.dev (`/v1/health`, `/v1/config`).

## Unverhandelbar (Fabian)
- Mit eigenem API-Schlüssel null Backend-Anfragen ohne Opt-in (OwnKeyPrivacyTest).
- Secrets nur als GitHub-Secrets, nie committen; database_id nur zur Laufzeit.
- Fabians E-Mail-Adresse geht an keinen Dienst.
- Tests nie abschalten. Wackelige Tests ursächlich beheben.
- Vor jedem Merge ein Sonnet-Gegencheck (CLAUDE.md).

## Offene To-dos für Fabian (bei Gelegenheit erinnern)
- Geldfragen (offen seit 7.11.):
  - Aufschlag `MARKUP_BP` 28 % → 18 % senken? Die 10-%-Rücklage für Geschenke entfällt, weil es keine Einladungen mehr gibt. Empfehlung: ja.
  - Voreinstellung „gratis“ (Workers AI): Sollen Nutzer den Listenpreis zahlen oder 0? Empfehlung: Listenpreis.
  - Preise für OpenAI und Workers AI in `backend/llm-routing.jsonc` vor Produktion prüfen.
- Ausgabenlimit für `ANTHROPIC_API_KEY` in der Anthropic-Konsole setzen.
- Test-Guthaben (es gibt kein Startguthaben mehr): App öffnen, dann Actions → „Backend deployen“ mit mode=test und credit_eur.
- Löschbare Secrets: `INVITE_SIGNING_KEY`/`SPOT_SIGNING_KEY`, `PLAY_INTEGRITY_SERVICE_ACCOUNT`, `APPLE_APP_ATTEST_TEAM_ID`. `GEMINI_API_KEY` behalten, falls er die Voreinstellung „gemini“ nutzen will.
- Repo-Variable `BACKEND_DEPLOY_MODE=test` für automatische Backend-Deploys.
- Stripe-Testkonto + Secrets `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` (später Live-Varianten).
- TestFlight-Secrets laut README „iPhone: TestFlight“ (Apple Developer hat er), danach Workflow „iOS TestFlight“.
- Rechtsseiten (Impressum, Datenschutz, AGB/Widerruf) – Recht = Fabians Entscheidung.
- Offene Fragen ohne Antwort:
  - Fotos pro Tag und pro Training oder nur pro Training?
  - Claude-Connector (MCP) auf dem Testserver prüfen und Einrichtung aufschreiben?
- Offene Idee: Overfit-Skill bzw. MCP-Plugin für KI-Apps (Schritt 1 ohne Backend: Skill + `.overfit`-Export). Wartet auf Fabians Go.
- Verworfen (7.11.): Geräte-Umrechner für Maschinen verschiedener Marken.

## KI im Backend (Stand 7.11.)
- Guthaben-Anfragen laufen über eine Anbieter-Schicht im Worker (Anthropic, OpenAI, Gemini, Workers AI, OpenRouter). Die Routing-Tabelle steht in `backend/llm-routing.jsonc`: Stufe lite/standard/pro → Modell + Ausweich-Modell + Preis.
- Umschalten: Actions → „Backend deployen“ → Feld „KI-Anbieter“ (claude, openai, gemini, gratis, gemischt, openrouter) oder Repo-Variable `LLM_PRESET`. Die Wahl wird im Worker gespeichert und gilt, bis neu gewählt wird. Anleitung: `docs/anbieter-umstellen.md`.
- Abrechnung: echte Tokens × Preis des antwortenden Modells + Aufschlag. Beim Ausweichen zahlt der Nutzer das günstigere der beiden Modelle. Das Anfrage-Protokoll ist in der App unter Profil → Guthaben → Abrechnung.
- Guthaben läuft nie ab. Kein Startguthaben, keine Einladungen.
- App-DB: v10 body_weight, v11 ai_request, v12 Coach-Chat.

## Verlauf seit 3.11. abends (Sessions)
AF #58 Feedback-Runde 4 · #59 Name „Overfit“ · AG #60 Ticken, nicht wegwischbare Pausen-Benachrichtigung, Settings „Benachrichtigungen“ · AH #61 Übungsbild → Vollbild-Animation · U #43 iOS testbar, TestFlight-Workflow · AI #63 Sauna (DB v8) · AJ #62 Coach-Story · #64 Sonnet-Gegencheck-Regel · AK #65 SVG-Hotfix · #66/#72 impeccable · AL #67 Verlauf-Tab weg, Kalender-Tag, Trainings bearbeiten (DB v9), Tabs Workouts/Coach/Kalender/Profil · AN #68 CI-Stabilität (AppTeardown, Simulator-Boot) · AM #69–#71 UI-Audit (Zahlenfelder, Panels, Popups) · #87 Modellregel · AU #82/#91 KI repariert, nur Claude, Abrechnung, Einladungen entfernt · AY #88 Import → Editor · AZ #89 Körpergewicht, Zukunftsmonate, Heute · BA #90 Muskel-Heatmap · BB #93 Coach-Tab als Chat · BC #92 grüne Satzzeilen, Eingabefelder, Cursor ans Ende · BD #94/#95 KI-Anbieter umschaltbar.
Nächster freier Buchstabe: BE.
