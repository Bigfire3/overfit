# overfit

Minimalistischer KI-Fitness-Coach. Expo (React Native + TypeScript) für iOS, Android und Web.

@AGENTS.md

## Arbeitsweise

- **Owner nur bei extrem wichtigen Entscheidungen fragen.** Alles andere selbst sinnvoll entscheiden, umsetzen und im PR/Issue begründen.
- Aufgaben stehen als GitHub-Issues im Repo. Commits/PRs referenzieren die Issues.
- Vor jedem Push: `npm run check` (Typecheck, Lint, Tests).
- UI-Änderungen im Browser prüfen (`npx expo start --web`).

## Produktentscheidungen

| Thema | Entscheidung |
|---|---|
| Prinzip | Extrem minimalistisch – in Funktion und Design |
| Onboarding | Name, Alter, Gewicht, KI-Zugang. Eine Frage pro Screen. Nichts weiter. |
| Sprache | Deutsch & Englisch. Aus der Gerätesprache übernommen, in den Settings änderbar. Nicht im Onboarding. App-Sprache = Antwortsprache des Coaches. |
| Design | Schwarz-weiß, angelehnt an den Stil von Trade Republic (Inspiration, keine Kopie von Logo/Name/Hausschrift/Screens). Dark als Standard, Light optional. |
| Monetarisierung | Keine Werbung, kein Tracking, keine Analytics-SDKs. |
| KI | Zuerst nur eigener API-Key (BYOK). Anbieter noch nicht final → Provider-Abstraktion (`src/ai`). Start: OpenRouter, Anthropic, OpenAI. |
| Credits | Später, über eigenes Backend als zweite Provider-Implementierung (Issue #13). |
| Daten | Lokal auf dem Gerät. API-Key in Keychain/Keystore (`expo-secure-store`), nie auf eigenem Server. Gewicht ist ein Gesundheitsdatum (DSGVO Art. 9). |
| Datenmodell | Alter als Geburtsjahr, Gewicht als Zeitreihe `{date, kg}` (intern immer kg). |
| Fehlende Infos | Ziel, Größe usw. fragt der Coach im Gespräch nach und merkt sie sich (Issue #11). |

## Designsystem (`src/theme`, `src/components/ui.tsx`)

- Farben: Schwarz, Weiß, wenige Grautöne. **Keine Akzentfarbe.**
- Typo: Inter, nur die Stufen `hero`, `title`, `body`, `label`, `caption`.
- Eine große Hero-Zahl/-Aussage pro Screen, viel Weißraum.
- Keine Cards, keine Schatten, keine Verläufe. Listen mit Haarlinien (`Row`).
- Ein primärer Button pro Screen (`PrimaryButton`: invertiert, volle Breite, unten, Pill).
- Dünne Linien-Icons (`Icon`), sparsam.
- Alle Texte über `src/i18n` (`de.ts` ist die Quelle, `en.ts` muss denselben Typ erfüllen).

## Struktur

```
src/app/          Screens (Expo Router)
src/components/   UI-Bausteine des Designsystems
src/features/     Screen-übergreifende Feature-Komponenten
src/domain/       Reine Logik (unit-getestet), u. a. Profil-Block fürs Modell
src/ai/           Provider-Abstraktion + Key-Validierung
src/store/        App-State (AsyncStorage) + sicherer Key-Speicher
src/i18n/         Übersetzungen DE/EN
src/theme/        Design-Tokens
```

## Technische Hinweise

- `metro.config.js` zwingt `@anthropic-ai/sdk` auf den CommonJS-Build – der ESM-Build crasht unter Metro (zirkuläre Imports).
- Im Web gibt es keinen Keychain; dort liegt der Key in `localStorage` (`secureKey.web.ts`) – nur für Entwicklung/Vorschau.
