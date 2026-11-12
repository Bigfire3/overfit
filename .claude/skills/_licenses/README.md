# Projekt-Skills (Drittanbieter)

Installiert am 12.11.2026 mit der `skills`-CLI (https://skills.sh) für Claude Code auf Projektebene,
als Kopie (`--copy`), damit Cloud-Sessions sie ohne Symlinks und ohne Marketplace-Plugins laden:

```sh
npx skills add https://github.com/pbakaus/impeccable/tree/main/.claude/skills/impeccable --agent claude-code --skill '*' --copy -y
npx skills add jakubkrehel/skills  --agent claude-code --skill '*' --copy -y
```

Bei impeccable den Pfad zur Claude-Variante angeben: `npx skills add pbakaus/impeccable` allein nimmt die
generische Variante aus `.agents/skills/` (Codex-Befehle `$impeccable`, Pfade `.agents/skills/…`, ohne
`argument-hint`).

| Quelle | Commit | Skills | Lizenz |
|---|---|---|---|
| https://github.com/pbakaus/impeccable | b4bff16 (Skill-Version 4.8.2) | `impeccable` aus `.claude/skills/impeccable` (+ Agenten `.claude/agents/impeccable-*.md` aus `.claude/agents`) | Apache-2.0, `impeccable-LICENSE`, `impeccable-NOTICE.md` |
| https://github.com/jakubkrehel/skills | a72290d | `afk`, `better-accessibility`, `better-colors`, `better-interface`, `better-layout`, `better-typography`, `better-ui`, `better-writing`, `break`, `build-design`, `explain-interface`, `interface-review`, `state-machine`, `variant` | MIT, `jakubkrehel-skills-LICENSE` |

Die Skill-Ordner bleiben unverändert, wie die CLI sie installiert (Hashes in `skills-lock.json`).
Lizenztexte liegen deshalb hier und nicht in den Skill-Ordnern.

Aktualisieren: `npx skills update -p -y` (oder die beiden `add`-Befehle oben erneut), danach die
Agenten aus `.claude/agents` des impeccable-Repos nach `.claude/agents/` kopieren und die Tabelle anpassen.

`agents/openai.yaml` in den Krehel-Skill-Ordnern ist eine Konfiguration für Codex und wird von
Claude Code ignoriert.
