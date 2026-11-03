# Overfit-Backend: API-Vertrag (Fuel, Spot, KI-Chat-Verbindung)

Grundlage für die App-Anbindung. Implementierung: `backend/` (Cloudflare Worker + D1). Basis-URL: `https://<worker-domain>`, alle Pfade unter `/v1`.
Das Backend speichert **keine Trainingsinhalte und keine Fotos** (Anfragen werden nur durchgeleitet), sondern Konto-ID, Saldo, Buchungen (Modell, Token, Betrag) und Spot-Zuordnungen. **Einzige Ausnahme:** die freiwillige KI-Chat-Verbindung (unten), die der Nutzer ausdrücklich einschaltet; Abschalten löscht alles.

## Beträge

- Alle Beträge sind **Integer in Mikro-Euro (µ€)**: `1 € = 1_000_000`. Nie Float. Anzeige in der App: `4_200_000` → „4,20 €“ (kaufmännisch runden, nur zur Anzeige).
- Felder heißen `…Micro`. Das Backend rechnet immer in Euro; Preise pro Modell in µ€ je 1 Mio. Token stehen in `GET /v1/config`.
- Aufschlag (`markupBp`) und Store-Gebühr (`storeFeeBp`) in Basispunkten (800 = 8 %). Alles ist serverseitig konfigurierbar; die App liest Werte aus `/v1/config` und rechnet nichts selbst nach.

## Signatur-Schema (alle Endpunkte außer `/v1/health` und `/v1/config`)

Identität = ECDSA-P-256-Schlüsselpaar im Android Keystore bzw. in der Secure Enclave. Der private Schlüssel verlässt das Gerät nie.

**Öffentlicher Schlüssel:** entweder 65 Byte unkomprimierter Punkt (`0x04‖X‖Y`, iOS `x963Representation`) oder SPKI-DER (Android `publicKey.encoded`, 91 Byte), Base64url ohne Padding.
**Geräte-ID:** `base64url(SHA-256(65-Byte-Rohpunkt))` (ohne Padding, 43 Zeichen).

**Header jeder Anfrage:**

| Header | Inhalt |
|---|---|
| `X-Overfit-Device` | Geräte-ID |
| `X-Overfit-Timestamp` | Unix-Sekunden (dezimal). Abweichung zur Serverzeit höchstens ±300 s, sonst `timestamp_out_of_range` (mit `details.serverTime`) |
| `X-Overfit-Nonce` | 16–64 Zeichen `[A-Za-z0-9_-]`, zufällig, **pro Anfrage neu** (z. B. 16 Zufallsbytes als Base64url). Wiederverwendung → `replay` |
| `X-Overfit-Signature` | Base64url (ohne Padding) der ECDSA-SHA-256-Signatur über den kanonischen String. Roh `r‖s` (64 Byte; WebCrypto/CryptoKit `rawRepresentation`) **oder** DER (Android `Signature.getInstance("SHA256withECDSA")`) – beides wird akzeptiert |

**Kanonischer String** (UTF-8, Zeilentrenner `\n`, kein abschließender Zeilenumbruch):

```
OVERFIT-V1
<METHODE in Großbuchstaben>
<Pfad inkl. Query-String, genau wie gesendet, z. B. /v1/account/ledger?limit=20>
<Timestamp>
<Nonce>
<SHA-256 des Request-Bodys als Hex, klein; leerer Body = e3b0c442…b855>
```

Beispiel (`PUT /v1/account/profile`, Body `{"displayName":"Fabian"}`):

```
OVERFIT-V1
PUT
/v1/account/profile
1762128000
q3xK9mZc2YpWb7LdEoT5Ag
84adfca0d14b5eeabe8c85f59d4ad3aa7975a28d924e5a43d24e268a50bd7b7c
```

Der Body muss **byte-identisch** gesendet werden wie gehasht (am besten den serialisierten String einmal erzeugen und für Hash und Request verwenden). `Content-Type: application/json`.

```
PUT /v1/account/profile HTTP/1.1
X-Overfit-Device: <deviceId>
X-Overfit-Timestamp: 1762128000
X-Overfit-Nonce: q3xK9mZc2YpWb7LdEoT5Ag
X-Overfit-Signature: <base64url(Signatur)>
Content-Type: application/json

{"displayName":"Fabian"}
```

Referenzimplementierung des Clients (WebCrypto): `backend/test/helpers.ts` (`Client.sign`).

## Fehlerformat

Jeder Fehler: passender HTTP-Status und

```json
{ "error": { "code": "insufficient_funds", "message": "Guthaben reicht nicht", "details": { "requiredMicro": 10053, "availableMicro": 200 } } }
```

`message` ist deutsch und nur für Logs gedacht; die App zeigt eigene Texte pro `code`. 429 enthält zusätzlich `Retry-After` (Sekunden).

| Status | code | Bedeutung / App-Reaktion |
|---|---|---|
| 400 | `invalid_request` | Body/Parameter ungültig |
| 400 | `model_not_allowed` | Modell nicht in der Whitelist (`details.allowed`) |
| 400 | `invalid_max_tokens` | `max_tokens` fehlt oder > `maxTokensCap` |
| 400 | `streaming_not_supported` | `stream` ist nicht erlaubt |
| 400 | `upstream_rejected` | Anthropic hat die Anfrage abgelehnt, **nichts abgebucht** (`details.upstreamStatus`) |
| 400 | `unknown_product` | Paket-ID unbekannt |
| 400 | `invalid_recovery_code` | Format falsch |
| 400 | `spot_token_invalid`, `spot_self`, `challenge_invalid` | Spot-Code kaputt / eigener Code / Challenge ungültig oder benutzt |
| 401 | `unauthorized`, `unknown_device`, `bad_signature`, `replay`, `timestamp_out_of_range` | Signatur-Probleme. `unknown_device` → neu registrieren (`POST /v1/devices`) |
| 402 | `insufficient_funds` | Guthaben reicht nicht für die Reservierung (`details.requiredMicro`, `availableMicro`) |
| 402 | `purchase_invalid` | Store bestätigt den Kauf nicht |
| 403 | `attestation_failed` | Geräteprüfung fehlgeschlagen (`details.reason`) |
| 404 | `invalid_recovery_code` | Kein Konto zu diesem Code; `not_found` bei unbekanntem Endpunkt |
| 405 | `method_not_allowed` | |
| 403 | `photos_not_enabled` | Foto-Upload ohne Foto-Freigabe |
| 409 | `chat_not_enabled` | KI-Chat-Verbindung nicht eingeschaltet (`POST /v1/chat/enable`) |
| 413 | `sync_quota_exceeded`, `doc_too_large`, `photo_too_large` | Speichergrenze der KI-Chat-Verbindung (`details.maxDocs`/`maxBytes`/`maxPhotos`) |
| 409 | `spot_token_used`, `spot_already_redeemed`, `spot_device_used` | Spot schon eingelöst (Token / dieses Konto / dieses Gerät) |
| 410 | `spot_token_expired` | QR-Code abgelaufen, neu scannen |
| 413 | `body_too_large` | Anfrage größer als `MAX_BODY_BYTES` (Standard 8 MB) |
| 429 | `rate_limited` | Zu viele Anfragen (`details.retryAfterSec`) |
| 429 | `spot_inviter_limit` | Einladender hat diesen Monat alle Spots vergeben |
| 429 | `spot_budget_exhausted` | Globales Monatsbudget für Spots aufgebraucht |
| 500 | `internal_error` | |
| 502 | `upstream_unavailable` | Anthropic nicht verfügbar/überlastet, **nichts abgebucht** |
| 503 | `not_configured` | Server-Secret fehlt (Betreiberfehler) |
| 503 | `purchases_unavailable` | Käufe sind (noch) nicht freigeschaltet → Kauf-UI ausblenden |
| 503 | `attestation_unavailable` | Spot ist (noch) nicht freigeschaltet |
| 504 | `upstream_unavailable` | Zeitüberschreitung, **nichts abgebucht** |

## Öffentliche Endpunkte (ohne Signatur)

### `GET /v1/health` → `{ "ok": true }`

### `GET /v1/config`
```json
{
  "currency": "EUR", "unit": "micro-euro", "markupBp": 800, "storeFeeBp": 1500, "maxTokensCap": 8192,
  "models": { "claude-sonnet-5-5": { "in": 1840000, "cacheRead": 184000, "cacheWrite": 2300000, "out": 9200000 } },
  "packages": [ { "productId": "overfit.fuel.10", "priceMicro": 10000000, "creditMicro": 7870370 } ],
  "spot": { "starterMicro": 1000000, "rewardMicro": 1000000, "tokenTtlSec": 45, "monthlyLimitPerInviter": 10, "expiryDays": 180 }
}
```
Modellpreise in µ€ je 1 Mio. Token (für Kostenschätzung in der App). `productId` entspricht der Produkt-ID im Play-/App-Store.

## Geräte & Konto

### `POST /v1/devices` – Registrierung
Signiert mit dem **neuen** Schlüssel (beweist Besitz); `X-Overfit-Device` = daraus abgeleitete Geräte-ID.
Body: `{ "publicKey": "<base64url>" }`. → `201` (neu) oder `200` (schon registriert, idempotent):
`{ "deviceId": "...", ...Konto }` (Konto-Objekt siehe unten). Limit pro IP: 20/Stunde (konfigurierbar).

### `GET /v1/account` → Konto
```json
{
  "deviceId": "…", // nur bei /v1/devices
  "accountId": "…",
  "balanceMicro": 1000000,
  "expiring": [ { "amountMicro": 1000000, "expiresAt": 1777777777 } ],
  "displayName": "Fabian",
  "hasRecoveryCode": true,
  "spot": { "invitedThisMonth": 2, "monthlyLimit": 10, "redeemedStarter": false }
}
```
`balanceMicro` = nutzbares Guthaben (nicht verfallen) minus laufende Reservierungen. `expiring` = noch offene, verfallende Posten (Startguthaben/Belohnungen).

### `PUT /v1/account/profile`
Body `{ "displayName": "Fabian" }` (≤ 24 Zeichen, `null` oder `""` löscht). → `{ "displayName": "Fabian" }`. Der Name wird nur beim Einlösen eines Spots an den Eingeladenen ausgeliefert (freiwillig).

### `GET /v1/account/ledger?limit=50&before=<id>`
`limit` 1–200. → `{ "entries": [ { "id": 12, "ts": 1762128000, "type": "usage|purchase|spot_bonus|spot_reward", "amountMicro": -6956, "model": "...", "inputTokens": 1000, "cacheReadTokens": 0, "cacheWriteTokens": 0, "outputTokens": 500 } ], "next": 12 }` (neueste zuerst; `next` = `before` für die nächste Seite, `null` am Ende). Verbrauch negativ, Gutschrift positiv.

## Wiederherstellungscode (Umzug aufs neue Handy)

### `POST /v1/recovery/code`
Body leer. Erzeugt (oder ersetzt) den Code: `{ "code": "ABCD-EFGH-JKLM-NPQR-STUV-WXYZ" }` (120 Bit, Alphabet ohne 0/O/1/I/L). Der Server speichert nur einen Hash; Klartext wird **nur hier einmal** geliefert. Die App zeigt ihn an und lässt ihn sichern.

### `POST /v1/recovery/restore`
Das **neue** Gerät registriert sich normal (`POST /v1/devices`, leeres Konto) und ruft dann auf: Body `{ "code": "…" }` (Groß-/Kleinschreibung, Bindestriche und Leerzeichen egal). Wirkung: das aufrufende Gerät übernimmt das Konto (Saldo, Buchungen), **alle anderen Geräte des Kontos werden abgemeldet**, ein Guthaben des vorübergehenden leeren Kontos wird übernommen, der Code wird ungültig (neuen erzeugen!). → Konto-Objekt. Fehler: `400/404 invalid_recovery_code`. Limit: 10 Versuche/Stunde je Gerät.

## KI-Proxy

### `POST /v1/messages`
Body: Anthropic-Messages-Request, unverändert (`model`, `max_tokens`, `system`, `messages`, `tools` … auch Bilder als Base64). Einschränkungen: `model` in der Whitelist (`/v1/config`), `max_tokens` ≤ `maxTokensCap`, **kein** `stream`. Limit: 10 Anfragen/Minute je Gerät.

Ablauf: Höchstkosten (geschätzte Eingabe + `max_tokens` × Ausgabepreis, mit Aufschlag) werden atomar reserviert (sonst `402 insufficient_funds`), die Anfrage geht an Anthropic, danach wird nach `usage` abgerechnet: `ceil((input×in + cache_read×cacheRead + cache_creation×cacheWrite + output×out) / 1e6 × (1 + markup))`, der Rest der Reservierung wird freigegeben. Bei jedem Fehler (Netz, 5xx, 4xx) wird vollständig freigegeben. Verbrauch → zuerst verfallende Posten (Startguthaben), dann Kauf-Guthaben.

Antwort `200`: die Anthropic-Antwort unverändert, **plus** Feld `overfit`:
```json
{ "id": "msg_…", "content": [ … ], "usage": { … }, "overfit": { "costMicro": 6956, "balanceMicro": 7863414, "currency": "EUR" } }
```
und Header `X-Overfit-Cost-Micro`, `X-Overfit-Balance-Micro`. Die App zeigt „Fuel · 7,86 €“ und die Kosten der Auswertung daraus. Bilder werden bei der Reservierung pauschal (3000 Token je Bild) gezählt, nicht nach Base64-Länge. Timeout zum Anbieter 120 s.

## Kauf

### `POST /v1/purchases/verify`
Body `{ "platform": "google"|"apple", "productId": "overfit.fuel.10", "purchaseToken": "<Play purchaseToken | Apple signierte Transaktion (JWS)>" }`.
→ `{ "productId": "…", "creditedMicro": 7870370, "alreadyCredited": false, "balanceMicro": 7870370 }`.
Gutschrift = `Preis × (1 − storeFeeBp) / (1 + markupBp)` (abgerundet), **idempotent pro Kauf** (`alreadyCredited: true` bei Wiederholung, auch von anderen Konten – ein Kauf zählt nur einmal). Die App ruft das nach jedem erfolgreichen Store-Kauf auf (und beim Start für noch nicht bestätigte Käufe) und schließt die Transaktion erst danach ab. Ohne Store-Zugangsdaten im Backend: `503 purchases_unavailable`.

## Geplant: Web-Kauf und Fuel-Codes (noch nicht gebaut)
Siehe docs/monetization.md. Nur Vertrag, keine Implementierung.

### `POST /v1/purchases/web-link` (signiert)
Body `{ "productId": "overfit.fuel.10", "locale": "de" }` → `{ "url": "https://…/buy?t=<token>", "expiresAt": "…" }`.
Token: signiert (HMAC), enthält anonyme Konto-ID, Produkt, Ablauf (≤ 15 Minuten), einmalig nutzbar. Die App öffnet `url` im Browser; kein Login nötig. Nur in Ländern/Stores ausgeliefert, in denen der Link erlaubt ist (Konfiguration in `/v1/config`).

### `POST /v1/webhooks/payment` (Merchant of Record, Signatur des Anbieters)
Body: Zahlungsereignis (Paddle/Lemon Squeezy/Stripe) mit Token aus dem Kauflink oder Code-Bestellung. Prüft die Anbieter-Signatur, bucht Fuel **idempotent pro Transaktions-ID** (`Preis × (1 − Anbietergebühr) / (1 + markupBp)`), `200` auch bei Wiederholung.

### `POST /v1/codes/redeem` (signiert)
Body `{ "code": "XXXX-XXXX-XXXX" }` → `{ "creditedMicro": …, "balanceMicro": … }`. Codes entstehen per Webhook (Kauf ohne App, Geschenk), sind einmalig einlösbar; Fehler `invalid_code`, `already_redeemed`.

## Spot (Einladungen)

Ablauf: Einladender holt Token → zeigt QR (Token + Installationslink, alle ~30 s neu holen) → Neuer scannt → App holt Challenge → Play Integrity / App Attest mit der Challenge → Einlösen.

### `POST /v1/spot/token` (Einladender)
Body leer. → `{ "token": "<body>.<sig>", "expiresAt": 1762128045, "ttlSec": 45 }`. Server-signiert (HMAC), einmalig einlösbar, gültig `ttlSec` (Standard 45 s). QR-Inhalt wie in der App festgelegt, z. B. `https://<domain>/spot#<token>` (Token ist URL-sicher, besteht aus `[A-Za-z0-9_.-]`). Kein Limit außer dem allgemeinen.

### `POST /v1/spot/challenge` (Eingeladener)
Body leer. → `{ "challenge": "<opaque>", "expiresAt": … }`. Gültig 5 min, an das Gerät gebunden, einmalig. Die App übergibt sie als Play-Integrity-`nonce` bzw. als App-Attest-`clientDataHash`-Grundlage (Details legt die App-Session mit dem Verifier fest).

### `POST /v1/spot/redeem` (Eingeladener)
Body:
```json
{ "token": "<aus dem QR>", "attestation": { "platform": "google"|"apple", "token": "<Integrity-Token | App-Attest-Objekt>", "challenge": "<von /spot/challenge>" } }
```
→ `200`:
```json
{ "grantedMicro": 1000000, "expiresAt": 1777777777, "inviter": { "displayName": "Fabian" }, "balanceMicro": 1000000 }
```
`inviter.displayName` ist `null`, wenn der Einladende keinen Namen gesetzt hat (Reveal zeigt dann „Spot“ ohne Namen). Regeln: Token gültig & unbenutzt; einmal pro Konto **und** pro Gerät (Gerätekennung aus der Attestierung, übersteht Neuinstallation); höchstens 10 Spots/Monat je Einladendem; globales Monatsbudget (jeder Spot kostet Startguthaben + Belohnung); Startguthaben verfällt nach 180 Tagen, gilt nur für Coach-Anfragen und ist nicht auszahlbar. Der Einladende bekommt seine Gutschrift (1 €, ebenfalls 180 Tage gültig) automatisch bei der **ersten abgerechneten Coach-Anfrage** des Eingeladenen – ohne Meldung an den Eingeladenen. Ohne konfigurierte Attestierung: `503 attestation_unavailable` (sideloaded APKs bekommen kein Startguthaben).

## Rate-Limits (Standardwerte, konfigurierbar)
Gerät: 60 Anfragen/min, `/v1/messages`: 10/min, Registrierung: 20/h je IP, Kauf/Challenge/Einlösen/Restore: 10/h je Gerät (Einlösen/Restore zusätzlich 30/h je IP). Überschreitung → `429 rate_limited`.

## Lokaler Dev-Modus
`wrangler dev --var DEV_MODE:true --var ATTESTATION_MODE:mock --var PURCHASE_MODE:mock` (siehe `backend/README.md`): Attestierungs- und Kauf-Token der Form `mock:<id>` werden akzeptiert (nur mit `DEV_MODE=true`; in Produktion werden die Mock-Modi ignoriert). `ANTHROPIC_BASE_URL` kann auf einen lokalen Fake zeigen.

## KI-Chat-Verbindung (Claude, ChatGPT, Gemini per MCP)

Freiwillig und getrennt von Fuel: Die App lädt Trainingsdaten hoch, ein KI-Chat liest sie über den Remote-MCP-Server
`https://<worker-domain>/mcp` und legt Vorlagen und Notizen in einen **Eingang**, den die App abholt. Alles hängt am Konto der
anonymen Geräte-Identität (Wiederherstellungscode nimmt es mit). Inhalte liegen AES-256-GCM-verschlüsselt (Schlüssel je
Konto aus einem Server-Geheimnis abgeleitet; keine Ende-zu-Ende-Verschlüsselung, weil der Server die Daten für den Chat
lesen muss). Fotos nur mit eigener Freigabe.

### Ablauf in der App
1. „KI-Chat verbinden“ → Hinweis + Häkchen „Körperfotos teilen“ (Standard aus) → `POST /v1/chat/enable`.
2. Vollständiger Upload: `POST /v1/chat/sync` mit `reset: true` im ersten Paket, danach weitere Pakete (je ≤ 500 Einträge)
   ohne `reset`. Bei Foto-Freigabe Fotos einzeln mit `PUT /v1/chat/photos/<id>`.
3. Danach inkrementell: nach jeder Änderung (Satz abgehakt, Training beendet, Vorlage gespeichert, Profil/Gedächtnis
   geändert) die geänderten Dokumente hochladen – gebündelt, höchstens alle ~10 s, laufendes Training mindestens beim
   Beenden und beim Wechsel in den Hintergrund. Gelöschtes über `deletes`.
4. „Mit Chat verbinden“ → `POST /v1/chat/pairing` → Code groß anzeigen (läuft nach 10 min ab, Countdown) + Anleitung je
   Plattform. Sobald `GET /v1/chat` eine neue Verbindung zeigt: „Verbunden mit Claude“.
5. Beim Öffnen der App (und beim Zurückkehren in den Vordergrund): `GET /v1/chat/inbox` → Vorschau → Übernehmen/Verwerfen →
   `POST /v1/chat/inbox/ack` mit den erledigten IDs.
6. Abschalten: `POST /v1/chat/disable` (löscht serverseitig alles, trennt alle Chats). Einzelne Chats trennen:
   `POST /v1/chat/connections/revoke`.

### `POST /v1/chat/enable`
Body `{ "photos": false }`. Schaltet ein bzw. ändert nur die Foto-Freigabe (idempotent). `photos: false` bei bisher
`true` **löscht alle hochgeladenen Fotos**. → Status (wie `GET /v1/chat`).

### `GET /v1/chat` → Status
```json
{ "enabled": true, "photos": false, "enabledAt": 1762128000, "lastSyncAt": 1762128500,
  "docs": { "profile": 1, "memory": 1, "template": 3, "workout": 42, "photo": 0 }, "bytes": 183422, "inboxPending": 1,
  "connections": [ { "id": "g_…", "clientName": "Claude", "createdAt": 1762128100, "lastUsedAt": 1762128400 } ] }
```
Nicht eingeschaltet: `{ "enabled": false, … }`.

### `POST /v1/chat/disable` → `{ "enabled": false, "deleted": true }`

### `POST /v1/chat/sync`
```json
{ "reset": false,
  "upserts": [ { "kind": "workout", "id": "<uuid>", "updatedAt": 1762128000123, "data": { … } } ],
  "deletes": [ { "kind": "template", "id": "<uuid>" } ] }
```
→ `{ "applied": 1, "skipped": 0, "deleted": 1, "lastSyncAt": 1762128500 }`. `updatedAt` in **Millisekunden**; ein Upsert mit
älterem `updatedAt` als gespeichert wird übersprungen (`skipped`). `reset: true` löscht vorher alle Dokumente (nicht die
Fotos). Höchstens 500 Einträge je Anfrage, Dokument ≤ 256 KB, je Konto 5000 Dokumente / 25 MB (`413 sync_quota_exceeded`).
IDs: `[A-Za-z0-9_.:-]{1,80}`. Unbekannte Felder in `data` sind erlaubt und werden an den Chat durchgereicht.

**Dokumentarten** (`kind` / `id` / `data`). Gewichte immer in **kg**, Zeitpunkte in **ms**:

| kind | id | data |
|---|---|---|
| `profile` | `me` | `{ "name", "birthYear", "unit": "kg"\|"lb", "language": "de"\|"en", "timeZone": "Europe/Berlin", "heightCm"?, "goals"?, "experience"?, "limitations"?, "notes"?, "bodyweight": [ { "date": "2026-09-01", "kg": 80.2 } ] }` |
| `memory` | `coach` | `{ "text": "<Coach-Gedächtnis>" }` |
| `template` | Vorlagen-UUID | `{ "name", "note"?, "exercises": [ { "exerciseId", "name", "trackingType", "restSec"?, "note"?, "sets": [ { "kg"?, "reps"?, "sec"? } ] } ] }` |
| `workout` | Session-UUID | `{ "name", "templateId"?, "startedAt", "endedAt": <ms> \| null (läuft), "note"?, "feeling"? (1–5), "photoIds"?: [], "review"?: { "rating", "summary", "positives", "improvements", "tips" }, "exercises": [ { "exerciseId", "name", "trackingType", "origin"?: "planned"\|"added"\|"replaced", "replacedExerciseId"?, "note"?, "restSec"?, "sets": [ { "kg"?, "reps"?, "sec"?, "rpe"?, "completed": true, "completedAt"?, "planned"?: { "kg"?, "reps"?, "sec"? } } ] } ] }` |

`exerciseId` = stabile Übungs-ID (`wg:…`, `custom:<uuid>` eigener Übungen; ehemalige `fedb:…`-Übungen, die Nutzer verwendet haben, behalten ihre ID als eigene Übung), `name` = Anzeigename (für den Chat lesbar).
Kennzahlen (Volumen, Top-Satz, e1RM nach Epley, längster Halt) berechnet der Server beim Upload selbst. Fotos,
Notizen und Gefühl gehen nur in diese Verbindung, nie in Teilen-Links.

### `PUT /v1/chat/photos/<photoId>?takenAt=<ms>&workoutId=<id>` / `DELETE /v1/chat/photos/<photoId>`
Body = Bildbytes (JPEG oder PNG, ≤ 3 MB; App verkleinert auf ~1500 px lange Kante), `Content-Type: image/jpeg`. Signatur wie
immer über den rohen Body; `takenAt` und `workoutId` stehen im signierten Query-String. Nur mit Foto-Freigabe
(`403 photos_not_enabled`), höchstens 300 Fotos. → `{ "id", "takenAt", "workoutId", "mimeType", "size", "sha256" }`.

### `POST /v1/chat/pairing` → `{ "code": "K7QM-3XWP", "expiresAt": 1762128600, "ttlSec": 600 }`
8 Zeichen (ohne 0/O/1/I/L), einmalig, 10 min gültig, ein neuer Code ersetzt den alten. Eingabe auf der Anmeldeseite ist
unabhängig von Groß-/Kleinschreibung, Bindestrich und Leerzeichen. Limit: 10/h je Gerät.

### `GET /v1/chat/inbox`
```json
{ "items": [
  { "id": "in_…", "kind": "template", "createdAt": 1762128400, "source": { "clientName": "Claude" },
    "template": { "v": 1, "kind": "template", "name": "Push B", "exercises": [ … ], "custom": [ … ] },
    "link": "https://bigfire3.github.io/overfit/t#<payload>", "replacesTemplateId": "<uuid>" | null, "message": "Begründung" | null },
  { "id": "in_…", "kind": "note", "createdAt": 1762128450, "source": { "clientName": "ChatGPT" },
    "note": { "title": "Auswertung" | null, "text": "Markdown", "workoutId": "<uuid>" | null, "rating": 8 | null } } ] }
```
`template` ist exakt das Teilen-Format v1 (`docs/share-format.md`) → dieselbe Import-Vorschau wie beim Link. Mit
`replacesTemplateId` bietet die Vorschau „Vorlage aktualisieren“ (Diff zur bestehenden) neben „Als neue Vorlage“ an. Notizen
mit `workoutId` erscheinen am Training im Verlauf, sonst in einer Liste „Notizen aus dem Chat“. Höchstens 50 offene Einträge.

### `POST /v1/chat/inbox/ack` → Body `{ "ids": ["in_…"] }` → `{ "removed": 1 }`
Auch für verworfene Einträge aufrufen.

### `POST /v1/chat/connections/revoke`
Body `{ "grantId": "g_…" }` oder `{ "all": true }` → `{ "revoked": 1 }`. Der Chat verliert sofort den Zugriff (Tokens gelöscht).

### Chat-Seite (öffentlich, nicht für die App)
- `POST /mcp` – MCP Streamable HTTP (JSON-RPC, Antwort `application/json`, zustandslos; `GET` → 405). Ohne/mit ungültigem
  Token `401` + `WWW-Authenticate: Bearer resource_metadata="…/.well-known/oauth-protected-resource"`.
- `GET /.well-known/oauth-protected-resource` (RFC 9728), `GET /.well-known/oauth-authorization-server` (RFC 8414)
- `POST /oauth/register` (Dynamic Client Registration, RFC 7591; `none`, `client_secret_post`, `client_secret_basic`;
  Redirects nur `https` oder `http://localhost`). Alternativ Client ID Metadata Document (`client_id` = https-URL).
- `GET/POST /oauth/authorize` – Anmeldeseite (DE/EN) mit Kopplungscode, PKCE `S256` Pflicht, `resource` (RFC 8707) optional.
- `POST /oauth/token` – `authorization_code` (Code 5 min, einmalig) und `refresh_token` (rotierend). Access-Token 1 h,
  Refresh-Token 90 Tage. `POST /oauth/revoke` (RFC 7009).

**Tools:** `get_profile`, `list_templates`, `get_template`, `list_workouts`, `get_workout`, `get_current_workout`,
`list_exercises`, `get_exercise_progress`, `search_exercise_catalog`, `list_photos`, `get_photo` (Bildinhalt),
`create_template` (→ Eingang + Import-Link), `add_note` (→ Eingang). Dazu drei Prompts (`evaluate_last_workout`,
`assess_progress`, `plan_next_workout`). Limit 120 MCP-Aufrufe/min je Verbindung.

## Kurz-Codes für Workouts und Duo (geplant, Session Q, noch nicht deployt)

Ziel: der QR-Code trägt nur eine ID (`OFS:<ID>`, 8 Zeichen Base36-Großbuchstaben+Ziffern → Version 1, 21×21 Module) statt des
ganzen Workouts (Push/Pull/Legs: 49–57 Module mit `OF1:`). Dasselbe Prinzip gilt später für die Duo-Session (ID einer Session)
und wie bei Spotify Codes: das Muster enthält nur eine ID, der Inhalt liegt auf dem Server.

App-Seite ist vorbereitet (`share/QrPayload.kt`): Schnittstelle `ShortCodeService { upload(training): String?; fetch(id): SharedTraining? }`,
Standard `NoShortCodes`. `qrTextFor(training, service)` nutzt `OFS:<ID>`, wenn `upload` eine ID liefert, sonst den vollständigen
`OF1:`-Inhalt (offline und ohne Backend funktioniert alles wie bisher). Der Scanner/Import erkennt beide Formate;
`OFS:` ohne erreichbares Backend ergibt die Meldung „kein Overfit-Training“.

### `POST /v1/share/short` (signiert)
Body: das Teilen-Format v1 (`docs/share-format.md`, wie im Link, JSON). Antwort `{ "id": "7K3QX9M2", "expiresAt": 1765000000 }`.
- ID: 8 Zeichen aus `0-9A-Z`, zufällig, nicht ratebar (kein Zähler). Nur Inhalt, keine Gerätekennung gespeichert.
- Aufbewahrung 30 Tage (kurzlebig, Duo-Sessions 24 h), danach `404`. Maximal 200 KB (wie `MAX_JSON_BYTES`).
- Rate-Limit: 30 pro Stunde und Gerät.

### `GET /v1/share/short/<id>` (öffentlich, ohne Signatur)
Antwort: das Teilen-Format v1 (JSON), `404` bei unbekannter/abgelaufener ID. Kein Konto nötig, damit auch ein neues Handy
direkt importieren kann. Antwort mit `Cache-Control: no-store`.

### Datenschutz
Der Inhalt eines Kurz-Codes liegt (anders als der Link, dessen Fragment nie einen Server erreicht) kurz auf dem Server.
Deshalb ist der Kurz-Code opt-in pro Teilen-Vorgang vorgesehen, sobald das Backend läuft; ohne Zustimmung bleibt es beim
vollständigen `OF1:`-Code. Fotos, Notizen und Gefühl gehören wie beim Link nie dazu.
