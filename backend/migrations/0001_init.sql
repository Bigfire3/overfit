-- Overfit-Backend: nur Konto-ID, Saldo und Buchungen. Keine Trainingsinhalte, keine Fotos.
-- Beträge sind Integer in Mikro-Euro (1 € = 1_000_000), nie Float.

CREATE TABLE accounts (
  id            TEXT PRIMARY KEY,
  display_name  TEXT,
  recovery_hash TEXT UNIQUE,
  created_at    INTEGER NOT NULL
);

CREATE TABLE devices (
  id          TEXT PRIMARY KEY,           -- base64url(SHA-256(roher öffentlicher Schlüssel))
  account_id  TEXT NOT NULL REFERENCES accounts(id),
  public_key  TEXT NOT NULL,              -- base64url, 65 Byte unkomprimierter P-256-Punkt
  created_at  INTEGER NOT NULL
);
CREATE INDEX devices_account ON devices(account_id);

-- Guthaben-Posten: Käufe (verfallen nie) und Boni (verfallen). remaining wird bei der Abrechnung verbraucht.
CREATE TABLE lots (
  id              TEXT PRIMARY KEY,
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  kind            TEXT NOT NULL,          -- purchase | spot_bonus | spot_reward
  amount_micro    INTEGER NOT NULL CHECK (amount_micro > 0),
  remaining_micro INTEGER NOT NULL CHECK (remaining_micro >= 0),
  expires_at      INTEGER,                -- Unix-Sekunden, NULL = verfällt nie
  created_at      INTEGER NOT NULL,
  ref             TEXT
);
CREATE INDEX lots_account ON lots(account_id);

-- Reservierungen laufender KI-Anfragen
CREATE TABLE holds (
  id           TEXT PRIMARY KEY,
  account_id   TEXT NOT NULL REFERENCES accounts(id),
  amount_micro INTEGER NOT NULL CHECK (amount_micro > 0),
  created_at   INTEGER NOT NULL
);
CREATE INDEX holds_account ON holds(account_id);

CREATE TABLE ledger (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  account_id         TEXT NOT NULL,
  ts                 INTEGER NOT NULL,
  type               TEXT NOT NULL,       -- usage | purchase | spot_bonus | spot_reward
  amount_micro       INTEGER NOT NULL,    -- vorzeichenbehaftet: Gutschrift +, Verbrauch -
  model              TEXT,
  input_tokens       INTEGER,
  cache_read_tokens  INTEGER,
  cache_write_tokens INTEGER,
  output_tokens      INTEGER,
  ref                TEXT
);
CREATE INDEX ledger_account ON ledger(account_id, id);

CREATE TABLE nonces (
  device_id TEXT NOT NULL,
  nonce     TEXT NOT NULL,
  ts        INTEGER NOT NULL,
  PRIMARY KEY (device_id, nonce)
) WITHOUT ROWID;
CREATE INDEX nonces_ts ON nonces(ts);

CREATE TABLE rate_limits (
  key    TEXT NOT NULL,
  window INTEGER NOT NULL,
  count  INTEGER NOT NULL,
  PRIMARY KEY (key, window)
) WITHOUT ROWID;

CREATE TABLE purchases (
  platform       TEXT NOT NULL,
  order_id       TEXT NOT NULL,
  account_id     TEXT NOT NULL,
  product_id     TEXT NOT NULL,
  price_micro    INTEGER NOT NULL,
  credited_micro INTEGER NOT NULL,
  created_at     INTEGER NOT NULL,
  PRIMARY KEY (platform, order_id)
) WITHOUT ROWID;

CREATE TABLE spot_tokens_used (
  token_id           TEXT PRIMARY KEY,
  inviter_account_id TEXT NOT NULL,
  used_at            INTEGER NOT NULL
);

CREATE TABLE spot_redemptions (
  id                  TEXT PRIMARY KEY,
  inviter_account_id  TEXT NOT NULL,
  redeemer_account_id TEXT NOT NULL UNIQUE,
  redeemer_device_id  TEXT NOT NULL UNIQUE,
  attestation_key     TEXT NOT NULL UNIQUE,  -- Gerätekennung aus der Attestierung (einmal pro Gerät)
  created_at          INTEGER NOT NULL,
  month               TEXT NOT NULL,         -- 'YYYY-MM' (UTC)
  rewarded_at         INTEGER
);
CREATE INDEX spot_inviter_month ON spot_redemptions(inviter_account_id, month);
CREATE INDEX spot_month ON spot_redemptions(month);
