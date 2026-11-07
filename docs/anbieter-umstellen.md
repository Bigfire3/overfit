# KI-Anbieter umstellen (Guthaben)

Hinter den drei Guthaben-Stufen der App (günstig = `lite`, normal = `standard`, stark = `pro`) steckt je ein KI-Modell.
Welches, bestimmt eine **Voreinstellung**. Umschalten geht ohne Programmieren, auch vom Handy in der GitHub-App.
Mit eigenem API-Schlüssel in der App ändert sich nichts: der läuft immer direkt beim Anbieter, ohne Server.

## Die Voreinstellungen

| Name | günstig / normal / stark | Ausweichen bei Ausfall | nötiges GitHub-Secret |
|---|---|---|---|
| `claude` (Standard) | Claude Haiku 5.5 / Sonnet 5.5 / Opus 5.5 | jeweils ein anderes Claude-Modell | `ANTHROPIC_API_KEY` |
| `openai` | GPT-5 mini / GPT-5 / GPT-5 | GPT-5 bzw. GPT-5 mini | `OPENAI_API_KEY` |
| `gemini` | Gemini 3.6 Flash (alle drei) | keins | `GEMINI_API_KEY` |
| `gratis` | Llama 4 Scout / Llama 4 Scout / Llama 3.3 70B (Cloudflare Workers AI) | Claude Haiku 5.5 (auch für Fotos, Llama kann keine) | keins (optional `ANTHROPIC_API_KEY` fürs Ausweichen und Fotos) |
| `gemischt` | Gemini 3.6 Flash / Claude Sonnet 5.5 / Claude Opus 5.5 | über Anbietergrenzen (Gemini ↔ Claude) | `ANTHROPIC_API_KEY` und `GEMINI_API_KEY` |
| `openrouter` | Claude Haiku / Sonnet / Opus über OpenRouter | jeweils ein anderes Claude-Modell | `OPENROUTER_API_KEY` |

`gratis`: Cloudflare schenkt 10 000 „Neurons“ pro Tag (grob einige Dutzend Coach-Auswertungen). Ist das Tageskontingent
aufgebraucht, antwortet das Ausweich-Modell Claude Haiku (wenn der Anthropic-Schlüssel da ist).

## So stellst du um (Handy, GitHub-App)

1. GitHub-App öffnen → Repository **overfit** → **Actions** → **Backend deployen**.
2. **Run workflow** (oben rechts bzw. „…“).
3. `mode`: **test** (Testserver) oder **production**.
4. **KI-Anbieter**: gewünschte Voreinstellung wählen, z. B. `openai`. „wie-bisher“ lässt alles, wie es ist.
5. **Run workflow** tippen. Nach etwa 2 Minuten steht in der Zusammenfassung des Laufs „KI-Voreinstellung im Betrieb: …“ mit
   den Modellen je Stufe.
6. In der App: Coach → Einstellungen → Modell zeigt die neuen Namen und Kosten (nach spätestens 10 Minuten oder nach App-Neustart).

Die Auswahl bleibt gespeichert, auch für spätere automatische Deploys. Wer lieber eine feste Einstellung will: Repo-Variable
`LLM_PRESET` (Settings → Secrets and variables → Actions → Variables) mit dem Namen anlegen; die gilt dann bei jedem Deploy
mit „wie-bisher“ (eine Auswahl beim Start geht immer vor).

## Schlüssel eintragen

GitHub → Repository → **Settings → Secrets and variables → Actions → New repository secret**, Name genau wie in der Tabelle:

- `ANTHROPIC_API_KEY`: console.anthropic.com → API Keys (beginnt mit `sk-ant-`)
- `OPENAI_API_KEY`: platform.openai.com → API keys (beginnt mit `sk-`)
- `GEMINI_API_KEY`: aistudio.google.com → Get API key
- `OPENROUTER_API_KEY`: openrouter.ai → Keys (beginnt mit `sk-or-`)

Am besten beim Anbieter ein Ausgabenlimit setzen. Danach einmal „Backend deployen“ starten.

## Was passiert, wenn ein Schlüssel fehlt?

Der Deploy geht nicht still kaputt: Fehlt für die gewählte Voreinstellung ein Schlüssel, zeigt der Lauf eine gelbe Warnung
(„openai ist unvollständig, es fehlt OPENAI_API_KEY. Der Server nutzt stattdessen claude“), und der Server nimmt die erste
vollständige Voreinstellung (Reihenfolge `fallbackOrder` in `backend/llm-routing.jsonc`). Ist gar keine vollständig, bricht der
Deploy mit einer Anleitung ab.

## Preise und Modelle ändern

Alles steht in [`backend/llm-routing.jsonc`](../backend/llm-routing.jsonc) (auch im Browser auf github.com mit dem Stift-Symbol
bearbeitbar): unter `models` Preis in US-Dollar je 1 Mio. Token (`in`/`out`, ohne Aufschlag), Anzeigename, Anbieter; unter
`presets` die Zuordnung Stufe → Modell und Ausweich-Modell. Nach dem Speichern (Commit nach `main`) einmal „Backend deployen“.
Abgerechnet wird immer aus den echten Token des Modells, das geantwortet hat, plus Aufschlag (`MARKUP_BP`, 28 %); beim
Ausweichen zahlt der Nutzer das günstigere der beiden Modelle.
