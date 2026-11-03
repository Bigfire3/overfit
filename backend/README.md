# Overfit-Backend (Fuel, Spot, KI-Chat-Verbindung)

Cloudflare Worker (TypeScript) + D1 (+ R2 für freigegebene Fotos). Schlank, Open Source. Für Fuel und Spot speichert es nur
Konto-ID, Saldo, Buchungen und Spot-Zuordnungen – **keine Trainingsinhalte, keine Fotos** (KI-Anfragen werden nur durchgeleitet).
**Ausnahme, nur nach ausdrücklichem Opt-in in der App („KI-Chat verbinden“):** Trainingsdaten (und nur mit eigenem Häkchen
Körperfotos) werden verschlüsselt gespeichert, damit Claude, ChatGPT und Gemini sie über den MCP-Server unter `/mcp` lesen
können. Abschalten löscht alles. API-Vertrag: [`docs/backend-api.md`](../docs/backend-api.md).

## Lokal entwickeln (ohne Cloudflare-Konto)

```sh
cd backend
npm ci
npm test            # Vitest in Miniflare (workerd), D1 lokal
npm run typecheck
npm run db:migrate:local
npm run dev         # wrangler dev, lokaler D1, DEV_MODE=true
```

Für `npm run dev` Secrets lokal in `backend/.dev.vars` (nicht committen):

```
ANTHROPIC_API_KEY=sk-ant-...        # oder ANTHROPIC_BASE_URL=http://localhost:9999 mit eigenem Fake
SPOT_SIGNING_KEY=irgendein-langer-zufallswert
ATTESTATION_MODE=mock               # nur mit DEV_MODE=true wirksam
PURCHASE_MODE=mock
```

Im Dev-Modus akzeptieren Kauf und Spot Token der Form `mock:<id>` statt echter Store-/Attestierungs-Belege. In Produktion
(`DEV_MODE` nicht `true`) werden die Mock-Modi ignoriert: ohne echte Prüfer sind Käufe und Spot abgeschaltet (503).

## Deployment (braucht Fabians Cloudflare-Konto)

1. Cloudflare-Konto anlegen (Free reicht), `npx wrangler login`.
2. Datenbank: `npx wrangler d1 create overfit` → die ausgegebene `database_id` in `backend/wrangler.jsonc` eintragen.
3. Migrationen einspielen: `npm run db:migrate:remote`.
4. Secrets setzen (`npx wrangler secret put <NAME>`):

   | Secret | Zweck | Nötig für |
   |---|---|---|
   | `ANTHROPIC_API_KEY` | Anthropic-Key des Betreibers (Workspace mit Ausgabelimit empfohlen) | `/v1/messages` |
   | `SPOT_SIGNING_KEY` | Zufälliger String ≥ 32 Zeichen (`openssl rand -base64 48`); signiert Spot-Tokens und Challenges | Spot |
   | `GOOGLE_PLAY_SERVICE_ACCOUNT` | JSON des Dienstkontos mit Play-Developer-API-Zugriff | Käufe Android |
   | `GOOGLE_PLAY_PACKAGE_NAME` | `app.overfit` | Käufe Android |
   | `PLAY_INTEGRITY_SERVICE_ACCOUNT` | Dienstkonto für `decodeIntegrityToken` | Spot Android |
   | `APPLE_IAP_KEY`, `APPLE_IAP_KEY_ID`, `APPLE_IAP_ISSUER_ID`, `APPLE_BUNDLE_ID` | App-Store-Server-API-Schlüssel (.p8) | Käufe iOS |
   | `APPLE_APP_ATTEST_TEAM_ID` | Team-ID für App Attest | Spot iOS |

   **Stand:** Die echten Prüfer für Google Play / Apple (`src/verifiers.ts`) sind noch nicht implementiert – nur die
   Schnittstellen und die Mock-Varianten für Tests. Käufe und Spot antworten in Produktion daher mit
   `purchases_unavailable` bzw. `attestation_unavailable`, bis die Prüfer mit echten Zugangsdaten nachgezogen werden
   (eigene Aufgabe, setzt die Store-Konten aus Phase 6 voraus). `/v1/messages`, Konten und Wiederherstellung laufen schon
   mit `ANTHROPIC_API_KEY` allein.
   | `SYNC_ENCRYPTION_KEY` | Zufällige 32 Byte als Base64url (`openssl rand 32 \| basenc --base64url \| tr -d =`); verschlüsselt alle KI-Chat-Daten. **Nie ändern** (sonst sind gespeicherte Daten unlesbar; die App lädt dann beim nächsten Sync neu hoch) | KI-Chat |
5. Für die KI-Chat-Verbindung: R2 aktivieren (Dashboard → R2, kostenloses Kontingent reicht) und
   `npx wrangler r2 bucket create overfit-chat-photos`. Optional in `wrangler.jsonc` unter `vars` `PUBLIC_BASE_URL` auf die
   endgültige Adresse setzen (z. B. `https://api.overfit.app`), sonst gilt die Adresse der jeweiligen Anfrage.
6. `npm run deploy` → die Worker-URL (`https://overfit-backend.<konto>.workers.dev`) der App-Session mitteilen
   (oder eigene Domain unter Workers & Routes verbinden).

## KI-Chat als Connector eintragen (nach dem Deployment)

Server-URL für alle Plattformen: **`https://<worker-domain>/mcp`** (z. B. `https://overfit-backend.<konto>.workers.dev/mcp`).
Anmeldung immer gleich: Die Plattform öffnet die Overfit-Anmeldeseite → in der App *Profil → KI-Chat verbinden → Code
anzeigen* → 8-stelligen Kopplungscode eintippen → „Verbinden“. Kein Konto, kein Passwort. Trennen: in der App (einzelne
Verbindung oder „KI-Chat trennen“ = alles löschen).

**Claude (claude.ai, Desktop, Mobile; Custom Connectors je nach Tarif):**
1. claude.ai → Einstellungen → Connectors → „Custom Connector hinzufügen“ (bei Team/Enterprise: Organisations-Owner unter
   Admin-Einstellungen → Connectors).
2. Name „Overfit“, URL `https://<worker-domain>/mcp`, „Erweiterte Einstellungen“ leer lassen (Claude registriert sich per
   Dynamic Client Registration selbst) → Hinzufügen → „Verbinden“ → Kopplungscode eingeben.
3. Im Chat über „+“ / Werkzeuge „Overfit“ aktivieren. Danach ist der Connector auch in der Claude-App am Handy verfügbar.
4. Später fürs Verzeichnis (für alle Nutzer ohne Eintragen): Einreichung über das Connector-Directory-Formular von Anthropic.

**ChatGPT (Plus/Pro/Business/Enterprise/Edu, wo „Developer Mode“ verfügbar ist):**
1. chatgpt.com → Einstellungen → Apps & Connectors → Erweitert → **Entwicklermodus** einschalten.
2. Apps & Connectors → „Erstellen“: Name „Overfit“, Beschreibung „Trainingsdaten und Vorlagen aus Overfit“, MCP-Server-URL
   `https://<worker-domain>/mcp`, Authentifizierung **OAuth** (Client-Registrierung automatisch) → Erstellen → anmelden mit
   Kopplungscode.
3. Im Chat über „+“ → Entwicklermodus / Overfit auswählen. Schreib-Tools (`create_template`, `add_note`) fragt ChatGPT vor dem
   Ausführen ab. Für eine öffentliche App später: Einreichung im OpenAI-Apps-Verzeichnis (Apps SDK, Prüfung durch OpenAI).
4. Ist der Entwicklermodus im Tarif nicht verfügbar: Ersatzweg wie bei Gemini (unten) – „Kontext kopieren“ aus der App,
   Vorlagen kommen als Overfit-Link zurück.

**Gemini (gemini.google.com):**
- Wo „Einstellungen → Verbundene Apps → Eigene Apps“ angeboten wird (Stand Herbst 2026: privates Google-Konto, 18+, USA,
  Englisch): „Eigene App hinzufügen“ → URL `https://<worker-domain>/mcp` → anmelden mit Kopplungscode. Danach auch in der
  Gemini-App am Handy nutzbar.
- **Sonst (z. B. in Deutschland) Ersatzweg:** In der App *KI-Chat verbinden → Gemini → Gem-Anleitung kopieren*; in Gemini
  „Gems → Neues Gem“, Name „Overfit Coach“, Anleitung einfügen, speichern. Vor jedem Gespräch in der App „Kontext kopieren“
  und in den Chat einfügen (Profil, Gedächtnis, letzte Trainings, Fortschritt als kompakter Text; Fotos einzeln anhängen).
  Das Gem antwortet mit einem `https://bigfire3.github.io/overfit/t#…`-Link für neue Vorlagen → antippen → Import-Vorschau
  in der App (bzw. Text kopieren → die App erkennt den Link in der Zwischenablage).

Prüfen ohne Chat: `npx @modelcontextprotocol/inspector` → Transport „Streamable HTTP“, URL wie oben → „Connect“ durchläuft
den gleichen OAuth-Ablauf.

## Konfiguration (`vars` in `wrangler.jsonc`, alle Beträge in Mikro-Euro)

| Variable | Standard | Bedeutung |
|---|---|---|
| `MARKUP_BP` | 800 | Aufschlag auf API-Preise (Basispunkte, 800 = 8 %) |
| `STORE_FEE_BP` | 1500 | Store-Gebühr für die Paket-Gutschrift |
| `SPOT_STARTER_MICRO` / `SPOT_REWARD_MICRO` | 1 000 000 | Startguthaben (Eingeladener) / Belohnung (Einladender) |
| `SPOT_TOKEN_TTL_SEC` | 45 | Gültigkeit des QR-Tokens |
| `SPOT_MONTHLY_LIMIT_PER_INVITER` | 10 | Spots pro Einladendem und Monat |
| `SPOT_GLOBAL_MONTHLY_BUDGET_MICRO` | 50 000 000 | Budget für alle Spots im Monat (jeder Spot = Start + Belohnung) |
| `SPOT_EXPIRY_DAYS` / `REWARD_EXPIRY_DAYS` | 180 | Verfall der Boni |
| `MAX_TOKENS_CAP` | 8192 | Obergrenze für `max_tokens` (begrenzt die Reservierung) |
| `MAX_BODY_BYTES` | 8 388 608 | Größtes Request-Body (Fotos) |
| `IMAGE_TOKENS_RESERVE` | 3000 | Pauschale Eingabe-Token je Bild bei der Reservierung |
| `PRICE_TABLE` | Sonnet 5.5 | JSON: `{"<modell>":{"in":…,"cacheRead":…,"cacheWrite":…,"out":…}}` in **µ€ je 1 Mio. Token** (USD-Listenpreis × Wechselkurs). Nur Modelle hier sind erlaubt. |
| `PACKAGES` | 5 € / 10 € | JSON: `{"<store-produkt-id>":{"priceMicro":…}}` |
| `CHAT_MAX_DOCS` / `CHAT_MAX_BYTES` | 5000 / 25 MB | Grenzen der KI-Chat-Daten je Konto (ohne Fotos) |
| `CHAT_MAX_PHOTOS` / `CHAT_MAX_PHOTO_BYTES` | 300 / 3 MB | Fotos je Konto / Größe je Foto |
| `CHAT_INBOX_MAX` | 50 | Offene Eingangs-Einträge, bevor der Chat nichts mehr ablegen kann |
| `PUBLIC_BASE_URL` | Origin der Anfrage | Issuer/Resource für OAuth (bei eigener Domain setzen) |
| `SHARE_BASE_URL` | `https://bigfire3.github.io/overfit/t` | Basis der Import-Links, die `create_template` liefert |
| `RL_*` | siehe API-Doku | Rate-Limits (`RL_MCP_PER_MIN` 120 je Verbindung) |

Preise ändern oder Modelle ergänzen: `PRICE_TABLE` setzen (Wechselkurs und Listenpreise selbst pflegen) und neu deployen.

## Aufbau

- `src/chat/`: `mcp.ts` MCP-Server (Streamable HTTP, JSON-RPC) · `tools.ts` Tools · `oauth.ts` OAuth 2.1 (DCR, CIMD, PKCE,
  Refresh-Rotation) · `pages.ts` Anmeldeseite · `sync.ts` Geräte-Endpunkte · `data.ts` Lesen + Kennzahlen · `vault.ts`
  AES-GCM · `catalog.ts` Übungskatalog (`catalog.gen.json` aus dem App-Katalog, neu erzeugen mit `node scripts/build-catalog.mjs`)
- `src/index.ts` Router · `auth.ts` Signaturprüfung/Replay-Schutz · `ledger.ts` Saldo, Reservierung, Abrechnung ·
  `messages.ts` KI-Proxy · `purchases.ts` · `spot.ts` · `account.ts` (Konto, Wiederherstellung) · `verifiers.ts` (Store-/Attestierungs-Schnittstellen)
- Guthaben als „Posten“ (`lots`): Käufe verfallen nie, Boni nach 180 Tagen; Verbrauch zuerst verfallende Posten. Reservierung =
  ein atomares `INSERT … SELECT … WHERE saldo ≥ betrag` (D1 serialisiert Schreibzugriffe) – parallele Anfragen können nicht überziehen.
  Reservierungen älter als 15 min gelten als verwaist und zählen nicht mehr.
