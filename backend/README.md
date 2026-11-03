# Overfit-Backend (Guthaben, Einladungen, KI-Chat-Verbindung)

Cloudflare Worker (TypeScript) + D1 (+ R2 für freigegebene Fotos). Schlank, Open Source. Für Guthaben und Einladungen speichert es nur
Konto-ID, Saldo, Buchungen und Einladungs-Zuordnungen – **keine Trainingsinhalte, keine Fotos** (KI-Anfragen werden nur durchgeleitet).
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
GEMINI_API_KEY=AIza...              # Gemini 3.6 Flash; oder GEMINI_BASE_URL=http://localhost:9999 mit eigenem Fake
ANTHROPIC_API_KEY=sk-ant-...        # Claude Sonnet 5.5; oder ANTHROPIC_BASE_URL=…
SPOT_SIGNING_KEY=irgendein-langer-zufallswert
ATTESTATION_MODE=mock               # nur mit DEV_MODE=true wirksam
PURCHASE_MODE=mock
```

Lokal gibt es kein Workers-AI-Binding und kein AI Gateway: die Stufe `lite` ist dort nicht verfügbar, Gemini und Anthropic werden
direkt angesprochen. Die Tests ersetzen beides durch Mocks (`test/tiers.test.ts`).

Im Dev-Modus akzeptieren Kauf und Einladung Token der Form `mock:<id>` statt echter Store-/Attestierungs-Belege. In Produktion
(`DEV_MODE` nicht `true`) werden die Mock-Modi ignoriert: ohne echte Prüfer sind Käufe und Einladungen abgeschaltet (503).

## Deployment ohne Terminal (empfohlen): der Deploy-Knopf

Alles im Browser, nichts installieren. Der Workflow `.github/workflows/backend-deploy.yml` legt Datenbank, Bucket und AI Gateway an, spielt die
Migrationen ein, deployt, überträgt die Secrets und prüft `/v1/health` und `/v1/config`. Er ist idempotent: beliebig oft startbar.

**Kosten:** Cloudflare Free reicht (Workers, D1, R2 haben ein kostenloses Kontingent, keine Kreditkarte nötig für den Free-Tarif).
KI-Kosten entstehen nur, wenn jemand Guthaben nutzt (dann vom Guthaben abgebucht, mit 8 % Aufschlag) – wer seinen eigenen Key nutzt,
verursacht dir keine Kosten und geht gar nicht über das Backend. Im Testbetrieb sind Limit und Gesamt-Guthaben niedrig (siehe unten).

**Einmalig einrichten**

1. **Cloudflare-Konto:** dash.cloudflare.com → Sign up (E-Mail, Passwort, Free-Tarif).
2. **workers.dev-Subdomain wählen:** im Dashboard links „Workers & Pages“ → Subdomain-Namen vergeben (z. B. `fabian`). Deine Worker-Adresse
   wird `https://overfit-backend-test.fabian.workers.dev` (Test) bzw. `https://overfit-backend.fabian.workers.dev` (Produktion).
3. **R2 aktivieren** (für KI-Chat-Fotos): links „R2 Object Storage“ → Free-Tarif wählen.
4. **Account-ID kopieren:** „Workers & Pages“ → rechts „Account ID“ (Kopieren-Symbol).
5. **API-Token erstellen:** oben rechts Profil → My Profile → API Tokens → Create Token → „Create Custom Token“ (ganz unten):
   Berechtigungen (jeweils Zeile „Account“):
   - **Workers Scripts – Edit**
   - **D1 – Edit**
   - **Workers R2 Storage – Edit**
   - **Account Settings – Read**
   - **Workers AI – Read** (Workers-AI-Binding für die Stufe `lite`; der Workflow prüft das Recht vorab)
   - **AI Gateway – Edit** (AI Gateway anlegen und lesen; „Edit“ schließt „Read“ ein)

   Hast du schon einen Token mit den ersten vier Rechten: beim Token auf **Edit** → die zwei Zeilen ergänzen → Update Token. Der Wert
   bleibt gleich, das GitHub-Secret muss nicht geändert werden.

   Account Resources: „Include → dein Konto“ → Continue to summary → Create Token → den Wert **sofort kopieren** (wird nur einmal gezeigt).
6. **GitHub-Secrets eintragen:** GitHub → Repo → Settings → Secrets and variables → Actions → **New repository secret**:

   | Secret | Pflicht | Inhalt |
   |---|---|---|
   | `CLOUDFLARE_API_TOKEN` | ja | der Token aus Schritt 5 |
   | `CLOUDFLARE_ACCOUNT_ID` | ja | Account-ID aus Schritt 4 |
   | `GEMINI_API_KEY` | mindestens einer der beiden KI-Keys (nur beim ersten Mal, danach merkt sich der Worker ihn) | Google-AI-Studio-Key für Gemini 3.6 Flash (aistudio.google.com/apikey → Create API key, Projekt mit Abrechnung; Ausgabenlimit im Google-Cloud-Projekt setzen) |
   | `ANTHROPIC_API_KEY` | optional, siehe oben | Anthropic-Key für Claude Sonnet 5.5 (console.anthropic.com → API Keys; am besten eigener Workspace mit Ausgabenlimit). Fehlt er, ist Claude Sonnet nicht verfügbar und die App blendet es aus |
   | `SPOT_SIGNING_KEY`, `SYNC_ENCRYPTION_KEY` | nein | werden, wenn nicht gesetzt, einmalig zufällig erzeugt und im Worker gespeichert. `SYNC_ENCRYPTION_KEY` nie ändern |
   | `ADMIN_SECRET` | nein | nur Testbetrieb; sonst bei jedem Lauf neu erzeugt |
   | `GOOGLE_PLAY_*`, `PLAY_INTEGRITY_SERVICE_ACCOUNT`, `APPLE_*` | nein | Store-Prüfer (siehe Tabelle unten); fehlen sie, überspringt der Workflow sie mit Hinweis |

   Fehlt ein Pflicht-Secret, bricht der Lauf mit einer Anleitung in der Zusammenfassung ab.
7. **Run workflow:** GitHub → Actions → „Backend deployen“ → Run workflow → `mode` = **test** → Run. Nach 2–3 Minuten steht in der
   Zusammenfassung (oben auf der Lauf-Seite) die Worker-Adresse.
8. **`OVERFIT_BACKEND_URL` setzen:** GitHub → Settings → Secrets and variables → Actions → Tab **Variables** → New repository variable:
   Name `OVERFIT_BACKEND_URL`, Wert = die Adresse aus der Zusammenfassung (ohne `/` am Ende). Das kann der Workflow nicht selbst (GITHUB_TOKEN
   darf keine Variablen schreiben), steht deshalb als letzter Schritt in der Zusammenfassung.
9. **Nächsten App-Build abwarten:** nach dem nächsten Merge nach main baut „Android“ ein neues APK (oder Actions → Android → Run workflow);
   dann „Overfit latest“ installieren. Ab jetzt zeigt die App beim Guthaben den Server („Testserver“-Hinweis im Testbetrieb).

**Testbetrieb (`mode = test`) – für Handys ohne Store-Konten.** Test und Produktion sind getrennte Instanzen (eigener Worker, Datenbank, Bucket),
Testdaten gelangen nie in die Produktion; beim Wechsel auf `production` ändert sich die Adresse (Schritt 8 wiederholen).
- Keine Geräteprüfung (Play Integrity/App Attest): Einladungen gehen mit seitlich installierten APKs (Test-Token, eine Einladung je Gerät).
- Jedes neue Konto bekommt **1 € Test-Guthaben** automatisch (Guthaben öffnen → „Guthaben ansehen“). Mehr: Run workflow mit `credit_eur` (z. B. `5`) →
  schreibt allen bestehenden Konten bis zu 10 € gut. Alles Test-Guthaben zusammen ist auf 20 € gedeckelt (`TEST_CREDIT_CAP_MICRO`).
  Ist es aufgebraucht: Instanz zurücksetzen (Datenbank `overfit-test` im Dashboard unter Storage & Databases → D1 löschen, Workflow neu starten).
- Käufe gibt es im Testbetrieb nicht (kein Store); Guthaben nur so. Niedrige Limits: `max_tokens` 2048, 5 KI-Anfragen/Minute, 3 Einladungen je Einladendem
  und Monat, Gesamtbudget für Einladungsboni 10 €.
- Markierung: `GET /v1/config` liefert `"mode": "test"`, die App zeigt unter Guthaben „Testserver“. Der Workflow prüft, dass der Modus stimmt.
- Sicherheit: Der Testmodus braucht `DEPLOY_MODE=test` (exakt). Die Admin-Gutschrift gibt es nur dort und nur mit `ADMIN_SECRET`; in Produktion antwortet
  der Pfad 404 und ein `test:`-Token wird abgelehnt (beides in `backend/test/testmode.test.ts`).

**Produktion (`mode = production`)** erst, wenn Play-/Apple-Konten und die Store-Prüfer (`src/verifiers.ts`, noch nicht implementiert) da sind.

**Automatisch deployen:** Repo-Variable `BACKEND_DEPLOY_MODE` = `test` (oder `production`) anlegen, dann deployt jeder Merge nach main, der
`backend/` ändert, von allein. Ohne die Variable oder ohne Cloudflare-Secrets passiert nichts.

## Deployment von Hand (Terminal, Alternative)

1. Cloudflare-Konto anlegen, `npx wrangler login`.
2. Datenbank: `npx wrangler d1 create overfit` → die ausgegebene `database_id` lokal in `backend/wrangler.jsonc` eintragen (nicht committen).
3. Migrationen einspielen: `npm run db:migrate:remote`.
4. Secrets setzen (`npx wrangler secret put <NAME>`), Tabelle unten.
5. KI-Chat: R2 aktivieren und `npx wrangler r2 bucket create overfit-chat-photos`. Optional `PUBLIC_BASE_URL` in `vars`.
6. `npm run deploy` → Worker-URL als Repo-Variable `OVERFIT_BACKEND_URL` eintragen.

Secrets (im Workflow oder von Hand):

   | Secret | Zweck | Nötig für |
   |---|---|---|
   | `GEMINI_API_KEY` | Google-AI-Studio-Key des Betreibers | Gemini 3.6 Flash (Google) |
   | `ANTHROPIC_API_KEY` | Anthropic-Key des Betreibers (Workspace mit Ausgabelimit empfohlen) | Claude Sonnet 5.5 (Anthropic) |
   | `SPOT_SIGNING_KEY` | Zufälliger String ≥ 32 Zeichen (`openssl rand -base64 48`); signiert Einladungs-Tokens und Challenges | Einladungen |
   | `SYNC_ENCRYPTION_KEY` | Zufällige 32 Byte als Base64url; verschlüsselt alle KI-Chat-Daten (die OAuth-Anmeldung des MCP-Servers braucht kein eigenes Geheimnis). **Nie ändern** (sonst sind gespeicherte Daten unlesbar; die App lädt dann beim nächsten Sync neu hoch) | KI-Chat |
   | `ADMIN_SECRET` | ≥ 16 Zeichen, nur Testbetrieb (`POST /v1/admin/credit`) | Test-Guthaben |
   | `GOOGLE_PLAY_SERVICE_ACCOUNT`, `GOOGLE_PLAY_PACKAGE_NAME` (`app.overfit`) | Dienstkonto mit Play-Developer-API-Zugriff | Käufe Android |
   | `PLAY_INTEGRITY_SERVICE_ACCOUNT` | Dienstkonto für `decodeIntegrityToken` | Einladungen Android |
   | `APPLE_IAP_KEY`, `APPLE_IAP_KEY_ID`, `APPLE_IAP_ISSUER_ID`, `APPLE_BUNDLE_ID` | App-Store-Server-API-Schlüssel (.p8) | Käufe iOS |
   | `APPLE_APP_ATTEST_TEAM_ID` | Team-ID für App Attest | Einladungen iOS |

   **Stand:** Die echten Prüfer für Google Play / Apple (`src/verifiers.ts`) sind noch nicht implementiert – nur die
   Schnittstellen, die Mock-Varianten (Dev) und der Test-Prüfer (Testbetrieb). Käufe und Einladungen antworten in Produktion daher mit
   `purchases_unavailable` bzw. `attestation_unavailable`, bis die Prüfer mit echten Zugangsdaten nachgezogen werden
   (eigene Aufgabe, setzt die Store-Konten aus Phase 6 voraus). `/v1/messages`, Konten und Wiederherstellung laufen schon
   mit einem KI-Key allein.

Von Hand zusätzlich: in `wrangler.jsonc` `"ai": { "binding": "AI" }` und `vars.AI_GATEWAY_ID` ergänzen und das Gateway im Dashboard
anlegen (AI → AI Gateway → Create). Der Workflow erledigt das selbst (`scripts/deploy-config.mjs`).

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
| `MARKUP_BP` | 2800 | Einziger Aufschlag auf alle Modellpreise (Basispunkte, 2800 = 28 %); Herleitung in docs/monetization.md. Web: Guthaben = Preis; Store: Guthaben laut `PACKAGES`, Store-Preis offen darüber |
| `SPOT_STARTER_MICRO` / `SPOT_REWARD_MICRO` | 1 000 000 | Startguthaben (Eingeladener) / Belohnung (Einladender) |
| `SPOT_TOKEN_TTL_SEC` | 45 | Gültigkeit des QR-Tokens |
| `SPOT_MONTHLY_LIMIT_PER_INVITER` | 10 | Einladungen pro Einladendem und Monat |
| `SPOT_GLOBAL_MONTHLY_BUDGET_MICRO` | 50 000 000 | Budget für alle Einladungsboni im Monat (jede Einladung = Start + Belohnung) |
| `SPOT_EXPIRY_DAYS` / `REWARD_EXPIRY_DAYS` | 180 | Verfall der Boni |
| `MAX_TOKENS_CAP` | 8192 | Obergrenze für `max_tokens` (begrenzt die Reservierung) |
| `MAX_BODY_BYTES` | 8 388 608 | Größtes Request-Body (Fotos) |
| `IMAGE_TOKENS_RESERVE` | 3000 | Pauschale Eingabe-Token je Bild bei der Reservierung |
| `PRICE_TABLE` | Tabelle in `src/providers.ts` | JSON: `{"<modell>":{"in":…,"cacheRead":…,"cacheWrite":…,"out":…}}` in **µ€ je 1 Mio. Token**; überschreibt einzelne Modelle der Tabelle |
| `AI_GATEWAY_ID` | leer (direkt) | Name des Cloudflare AI Gateways; setzt der Deploy-Workflow (`overfit` bzw. `overfit-test`) |
| `UPSTREAM_TIMEOUT_MS` / `UPSTREAM_BUDGET_MS` | 100 000 / 140 000 | Zeitlimit je Anbieter-Versuch / für Anfrage samt Ausweichen |
| `PACKAGES` | 5 € Guthaben für 6,99 € / 10 € für 13,99 € | JSON: `{"<store-produkt-id>":{"priceMicro":<Store-Preis>,"creditMicro":<Guthaben>}}`; ohne `creditMicro` gilt Guthaben = Preis |
| `WEB_PACKAGES` | 5 € / 10 € / 20 € / 50 € | Pakete der Kaufseite, JSON `{"web.10":{"priceMicro":10000000}}`; Guthaben = Preis (1:1) |
| `STRIPE_INVOICES` | aus | `on` = zu jedem Web-Kauf eine Stripe-Rechnung (PDF) mit §-19-Fußzeile; kostet bei Stripe extra (Repo-Variable) |
| `WEB_PURCHASE_CHANNELS` | `sideload` | Wo die App „Im Web aufladen“ zeigt: `sideload` (APK), `store` (Play/App Store); Repo-Variable |
| `WEB_PURCHASE` | an | `off` schaltet den Web-Kauf ab, auch mit Stripe-Secrets |
| `WEB_PURCHASE_URL` | `https://bigfire3.github.io/overfit/guthaben/` | Adresse der Kaufseite (Ziel des Kauflinks) |
| `WEB_LINK_TTL_SEC` | 900 | Gültigkeit des Kauflinks aus der App |
| `CHAT_MAX_DOCS` / `CHAT_MAX_BYTES` | 5000 / 25 MB | Grenzen der KI-Chat-Daten je Konto (ohne Fotos) |
| `CHAT_MAX_PHOTOS` / `CHAT_MAX_PHOTO_BYTES` | 300 / 3 MB | Fotos je Konto / Größe je Foto |
| `CHAT_INBOX_MAX` | 50 | Offene Eingangs-Einträge, bevor der Chat nichts mehr ablegen kann |
| `PUBLIC_BASE_URL` | Origin der Anfrage | Issuer/Resource für OAuth (bei eigener Domain setzen) |
| `SHARE_BASE_URL` | `https://bigfire3.github.io/overfit/t` | Basis der Import-Links, die `create_template` liefert |
| `DEPLOY_MODE` | `production` | `test` = Testbetrieb (siehe oben); setzt der Deploy-Workflow |
| `TEST_STARTER_MICRO` / `TEST_CREDIT_CAP_MICRO` | 1 000 000 / 20 000 000 | Test-Startguthaben je Konto / Obergrenze für alles Test-Guthaben |
| `RL_*` | siehe API-Doku | Rate-Limits (`RL_MCP_PER_MIN` 120 je Verbindung) |

## Web-Kauf (Stripe, Testmodus)

Guthaben lässt sich auf `https://bigfire3.github.io/overfit/guthaben/` kaufen: aus der App mit Kauflink („Im Web aufladen“,
15 Minuten gültig, ohne Login) oder ohne App als **Guthaben-Code** (Geschenk, in der App unter „Code einlösen“). Bezahlt wird auf der
gehosteten **Stripe-Checkout-Seite**. Verkäufer ist Fabian als Kleinunternehmer (§ 19 UStG, keine USt, kein Stripe Tax); Stripe ist nur
Zahlungsdienstleister. Gebucht wird nur per Webhook, idempotent; Erstattung und Chargeback holen Guthaben zurück (nie unter 0) und
markieren den Vorfall. Vertrag: `docs/backend-api.md` → „Web-Kauf“. Rechnung und Schwellen: docs/monetization.md.

Secrets (beide nötig, sonst bleibt der Web-Kauf aus, ohne Abbruch): `STRIPE_SECRET_KEY` und `STRIPE_WEBHOOK_SECRET` (`whsec_…`).
Ein Publishable Key ist nicht nötig (gehosteter Checkout, kein Stripe.js). Der Schlüssel bestimmt den Modus: Der **Testbetrieb** nimmt nur
`sk_test_…` (bzw. eingeschränkt `rk_test_…`), die **Produktion** nur `sk_live_…`. So erzeugen Testkarten nie echtes Guthaben und echte
Zahlungen nie bloß Test-Guthaben. Zusätzlich ignoriert der Webhook Ereignisse, deren `livemode` nicht passt.

### Einrichten, alles im Browser (Testmodus)
1. **dashboard.stripe.com/register** → Konto anlegen (E-Mail bestätigen). Für den Testmodus braucht es noch keine Prüfung.
   Oben rechts muss **„Testmodus“** (bzw. die Sandbox) aktiv sein.
2. **Einstellungen → Unternehmen → Öffentliche Unternehmensangaben** (Settings → Business → Public details): *Öffentlicher Name*
   (z. B. `overfit`, so heißt es im Checkout und auf dem Beleg), Support-E-Mail, Website `https://bigfire3.github.io/overfit/`.
   *Statement descriptor* (Text auf dem Kontoauszug), z. B. `OVERFIT`.
3. **§-19-Hinweis auf Belegen und Rechnungen:**
   - Der Checkout zeigt ihn automatisch (setzt das Backend über `custom_text`), ebenso Kaufseite und Bestätigung.
   - **Einstellungen → Billing → Rechnungsvorlage** (Settings → Billing → Invoice template): *Standard-Fußzeile* =
     `Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.` → Speichern. Das gilt für jede Stripe-Rechnung.
   - **Einstellungen → Unternehmen → Kunden-E-Mails** (Customer emails): *Erfolgreiche Zahlungen* einschalten. Diese Zahlungsbelege haben
     kein freies Fußzeilenfeld; wer eine Rechnung mit dem Hinweis will, setzt die Repo-Variable `STRIPE_INVOICES = on` (Stripe erzeugt dann
     zu jedem Kauf eine Rechnung mit der Fußzeile; kostet bei Stripe pro Rechnung extra). Im Testmodus verschickt Stripe keine Belege
     automatisch; im Zahlungsdetail lässt sich der Beleg ansehen bzw. von Hand senden.
4. **Einstellungen → Zahlungen → Zahlungsmethoden** (Payment methods): Karten sind an. Optional Apple Pay, Google Pay, Link, Klarna,
   SEPA-Lastschrift, EPS, iDEAL, Bancontact einschalten; der Checkout zeigt automatisch, was passt. Keine Zahlart mit Zusatzaufwand nötig.
5. **Entwickler → API-Schlüssel** (Developers → API keys): *Geheimschlüssel* (`sk_test_…`) → Anzeigen → kopieren.
6. **Entwickler → Webhooks** (je nach Dashboard auch *Workbench → Webhooks*) → **Ziel hinzufügen / Endpunkt hinzufügen**:
   - URL: `<Worker-Adresse>/v1/webhooks/payment` (Worker-Adresse = Repo-Variable `OVERFIT_BACKEND_URL`; die Deploy-Zusammenfassung nennt die URL),
   - Ereignisse: `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `charge.refunded`, `charge.dispute.created`,
   - Speichern → beim Endpunkt *Signaturgeheimnis* (Signing secret, `whsec_…`) → Anzeigen → kopieren.
7. GitHub → Settings → Secrets and variables → Actions → **New repository secret**: `STRIPE_SECRET_KEY` (Schritt 5),
   `STRIPE_WEBHOOK_SECRET` (Schritt 6). Alte `PADDLE_*`-Secrets können gelöscht werden.
8. Actions → **Backend deployen** → Run workflow (`mode` = test). Die Zusammenfassung zeigt „Web-Kauf: An, Testmodus (Stripe)“ und die
   Webhook-URL (muss mit Schritt 6 übereinstimmen).
9. Actions → **Web veröffentlichen (GitHub Pages)** → Run workflow, damit die Kaufseite die Backend-Adresse kennt.
10. Testkauf: Karte `4242 4242 4242 4242`, Ablauf in der Zukunft, beliebige CVC, beliebiger Name/E-Mail/Land.
    Ablehnung testen: `4000 0000 0000 0002`. Chargeback testen: `4000 0000 0000 0259`.
11. Erstattung testen: Stripe → **Zahlungen** → Kauf öffnen → **Erstatten** (voll oder teilweise) → Guthaben sinkt um den Betrag (nie unter 0).

### Für echte Zahlungen zusätzlich
- Stripe → **Konto aktivieren**: Rechtsform (Einzelunternehmen), Identität, Anschrift, Bankkonto, Steuerangaben (Kleinunternehmer,
  Steuernummer), Produktbeschreibung. Stripe prüft das; erst dann gibt es Live-Schlüssel.
- Im Live-Modus Schritte 2–6 wiederholen (Schlüssel und Webhook sind je Modus getrennt), dann in GitHub **`STRIPE_LIVE_SECRET_KEY`**
  (`sk_live_…`) und **`STRIPE_LIVE_WEBHOOK_SECRET`** anlegen und **Backend deployen** mit `mode` = production. Die Testwerte
  (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`) bleiben für den Testbetrieb stehen.
- Impressum, Datenschutzerklärung, AGB und Widerrufsbelehrung für digitale Inhalte (die Kaufseite zeigt Platzhalter „folgt vor dem
  Start“), juristische Prüfung. Kleinunternehmer-Schwellen beobachten (`scripts/usage-report.mjs`, docs/monetization.md).

## KI-Modelle, Preise und AI Gateway

Die Stufen-IDs `standard`, `pro`, `lite` sind nur intern (Protokoll zwischen App und Backend). Nutzer sehen überall die echten
Modellnamen samt Anbieter; `GET /v1/config` liefert dafür `displayName`, `vendor`, `label` und die Preise.

| Stufe | Anbieter, Modell | Bilder | Ausweichen bei Ausfall | Listenpreis je MTok (ein / aus) |
|---|---|---|---|---|
| `standard` (Voreinstellung) | Google Gemini `gemini-3.6-flash` (native API, `responseJsonSchema`) | ja | → `pro` | 0,75 $ / 3,75 $ (ab 2027-01-01: 1,50 $ / 7,50 $) |
| `pro` | Anthropic `claude-sonnet-5-5` | ja | → `standard` | 2 $ / 10 $ |
| `lite` | Workers AI `@cf/meta/llama-3.1-8b-instruct` (JSON-Modus) | nein (400 `images_not_supported`) | → `standard` | 25 608 / 75 147 Neurons (0,282 $ / 0,827 $) |

- Die App schickt das Anthropic-Format mit `model` = Stufe; das Backend bildet es auf den Anbieter ab und antwortet im Anthropic-Format.
- Preise stehen in `src/providers.ts` (`MODEL_PRICES`, mit Gültig-ab-Datum, Wechselkurs `EUR_PER_USD`). Ändern: Tabelle anpassen
  und deployen, oder kurzfristig `PRICE_TABLE` setzen.
- Ausweichen (5xx, 429, Zeitüberschreitung, Netzwerk): berechnet wird höchstens der Preis der angefragten Stufe für die tatsächlich
  verbrauchten Token; die Differenz trägt die Marge. Ablehnungen (4xx) weichen nicht aus.
- AI Gateway: alle Aufrufe (Gemini, Anthropic über `getUrl`, Workers AI über die `gateway`-Option) laufen über `AI_GATEWAY_ID`.
  Protokolle und Kosten: dash.cloudflare.com → AI → AI Gateway → `overfit` bzw. `overfit-test`. Der Workflow legt es mit
  Protokollen an, Rate-Limit 600 Anfragen/Minute (gleitend, Notbremse) und Cache aus. Anfragen mit Körperfotos werden nicht
  inhaltlich protokolliert: Sie bekommen `cf-aig-collect-log-payload: false`, das Gateway speichert dann weder Anfrage noch Antwort,
  führt aber Metadaten (Tokens, Kosten, Modell, Dauer, Status) weiter. Nicht `cf-aig-collect-log: false`, das würde auch Kosten
  und Tokens unterdrücken. Jede Anfrage trägt `cf-aig-metadata` `{ tier, photo, fallback }` (Filter im Dashboard). Caching kann im Dashboard eingeschaltet werden; Coach-Anfragen sind aber fast nie gleich.

## Nutzung und Marge (inhaltsfrei)

`GET /v1/admin/usage?from=2026-11-01&to=2026-12-01` mit `Authorization: Bearer <ADMIN_SECRET>` (auch in Produktion, nur mit gesetztem
ADMIN_SECRET) fasst das Ledger zusammen: je Modell, Stufe, Ausweichung und Foto-Anfrage Anzahl, Tokens, berechnet (€), Anbieterkosten
und Marge. Es liest nur Zahlen, nie Prompts, Antworten oder Fotos. Als Tabelle:

```bash
ADMIN_SECRET=… node scripts/usage-report.mjs https://<worker-domain> 2026-11-01 2026-12-01
```

## Aufbau

- `src/chat/`: `mcp.ts` MCP-Server (Streamable HTTP, JSON-RPC) · `tools.ts` Tools · `oauth.ts` OAuth 2.1 (DCR, CIMD, PKCE,
  Refresh-Rotation) · `pages.ts` Anmeldeseite · `sync.ts` Geräte-Endpunkte · `data.ts` Lesen + Kennzahlen · `vault.ts`
  AES-GCM · `catalog.ts` Übungskatalog (`catalog.gen.json` aus dem App-Katalog, neu erzeugen mit `node scripts/build-catalog.mjs`)
- `src/index.ts` Router · `auth.ts` Signaturprüfung/Replay-Schutz · `ledger.ts` Saldo, Reservierung, Abrechnung ·
  `messages.ts` KI-Proxy (Reservierung, Ausweichen, Abrechnung) · `providers.ts` Stufen, Preise, Anbieter-Adapter · `purchases.ts` · `spot.ts` (Einladungen) · `account.ts` (Konto, Wiederherstellung) · `verifiers.ts` (Store-/Attestierungs-Schnittstellen)
- Guthaben als „Posten“ (`lots`): Käufe verfallen nie, Boni nach 180 Tagen; Verbrauch zuerst verfallende Posten. Reservierung =
  ein atomares `INSERT … SELECT … WHERE saldo ≥ betrag` (D1 serialisiert Schreibzugriffe) – parallele Anfragen können nicht überziehen.
  Reservierungen älter als 15 min gelten als verwaist und zählen nicht mehr.
