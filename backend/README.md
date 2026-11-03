# Overfit-Backend (Fuel & Spot)

Cloudflare Worker (TypeScript) + D1. Schlank, Open Source, speichert nur Konto-ID, Saldo, Buchungen und Spot-Zuordnungen –
**keine Trainingsinhalte, keine Fotos** (KI-Anfragen werden nur durchgeleitet). API-Vertrag: [`docs/backend-api.md`](../docs/backend-api.md).

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
5. `npm run deploy` → die Worker-URL (`https://overfit-backend.<konto>.workers.dev`) der App-Session mitteilen
   (oder eigene Domain unter Workers & Routes verbinden).

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
| `RL_*` | siehe API-Doku | Rate-Limits |

Preise ändern oder Modelle ergänzen: `PRICE_TABLE` setzen (Wechselkurs und Listenpreise selbst pflegen) und neu deployen.

## Aufbau

- `src/index.ts` Router · `auth.ts` Signaturprüfung/Replay-Schutz · `ledger.ts` Saldo, Reservierung, Abrechnung ·
  `messages.ts` KI-Proxy · `purchases.ts` · `spot.ts` · `account.ts` (Konto, Wiederherstellung) · `verifiers.ts` (Store-/Attestierungs-Schnittstellen)
- Guthaben als „Posten“ (`lots`): Käufe verfallen nie, Boni nach 180 Tagen; Verbrauch zuerst verfallende Posten. Reservierung =
  ein atomares `INSERT … SELECT … WHERE saldo ≥ betrag` (D1 serialisiert Schreibzugriffe) – parallele Anfragen können nicht überziehen.
  Reservierungen älter als 15 min gelten als verwaist und zählen nicht mehr.
