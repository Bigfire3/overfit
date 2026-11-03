-- Guthaben im Web kaufen (Merchant of Record, zunächst Testmodus/Sandbox) und Guthaben-Codes.
-- Beträge in Mikro-Euro. Keine Namen, keine E-Mail-Adressen: die Zahlungsdaten bleiben beim Anbieter.

-- Quelle einer Buchung: NULL = wie bisher (Store/Spot/Verbrauch), 'web' = Web-Kauf, 'code' = eingelöster Guthaben-Code.
ALTER TABLE ledger ADD COLUMN source TEXT;

-- Kauflinks aus der App: kurzlebig (15 min), nur der Hash des Tokens liegt hier.
CREATE TABLE web_links (
  token_hash  TEXT PRIMARY KEY,
  account_id  TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  expires_at  INTEGER NOT NULL,
  checkouts   INTEGER NOT NULL DEFAULT 0, -- begonnene Checkouts (Obergrenze gegen Missbrauch)
  used_at     INTEGER                     -- gesetzt, sobald ein Kauf über den Link bezahlt ist (danach ungültig)
);
CREATE INDEX web_links_expires ON web_links(expires_at);

-- Bestellungen auf der Kaufseite. id ist geheim (steht nur in der Erfolgs-URL des Käufers).
CREATE TABLE web_orders (
  id              TEXT PRIMARY KEY,
  provider        TEXT NOT NULL,          -- paddle
  environment     TEXT NOT NULL,          -- sandbox | live
  provider_txn_id TEXT,                   -- Transaktions-ID beim Anbieter (txn_…)
  kind            TEXT NOT NULL,          -- credit (aufs Konto) | code (Guthaben-Code)
  account_id      TEXT,                   -- nur bei kind = credit
  link_hash       TEXT,
  package_id      TEXT NOT NULL,
  price_micro     INTEGER NOT NULL,       -- Endpreis inkl. USt
  credit_micro    INTEGER NOT NULL,       -- Guthaben (= Preis, 1:1)
  status          TEXT NOT NULL,          -- open | paid | refunded | chargeback
  created_at      INTEGER NOT NULL,
  paid_at         INTEGER,
  reversed_micro  INTEGER NOT NULL DEFAULT 0, -- zurückerstattet bzw. per Chargeback zurückgeholt (Preis)
  incident        TEXT                    -- Vorfall: refund | chargeback | amount_mismatch | …
);
CREATE UNIQUE INDEX web_orders_txn ON web_orders(provider, provider_txn_id);
CREATE INDEX web_orders_account ON web_orders(account_id);

-- Idempotenz der Webhooks: jedes verarbeitete Ereignis (Zahlung, Erstattung) genau einmal.
CREATE TABLE web_events (
  provider   TEXT NOT NULL,
  event_key  TEXT NOT NULL,               -- paid:<txn> | adj:<adjustment>
  created_at INTEGER NOT NULL,
  PRIMARY KEY (provider, event_key)
) WITHOUT ROWID;

-- Guthaben-Codes (Kauf ohne App, Geschenk). Einmalig einlösbar.
CREATE TABLE credit_codes (
  code         TEXT PRIMARY KEY,          -- normalisiert ohne Bindestriche
  order_id     TEXT NOT NULL UNIQUE,
  amount_micro INTEGER NOT NULL CHECK (amount_micro >= 0),
  status       TEXT NOT NULL,             -- active | redeemed | revoked
  created_at   INTEGER NOT NULL,
  redeemed_by  TEXT,
  redeemed_at  INTEGER
);
CREATE INDEX credit_codes_redeemer ON credit_codes(redeemed_by);
