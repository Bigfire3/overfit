-- KI-Chat-Verbindungen (Remote-MCP für Claude, ChatGPT, Gemini). Ausdrückliches Opt-in je Konto.
-- Ausnahme von „keine Trainingsinhalte“: Nur nach „KI-Chat verbinden“ in der App. Alle Inhalte liegen
-- AES-256-GCM-verschlüsselt (Schlüssel aus SYNC_ENCRYPTION_KEY je Konto abgeleitet). Abschalten löscht alles.
-- Nur additive Änderungen, bestehende Tabellen bleiben unverändert.

CREATE TABLE chat_sync (
  account_id     TEXT PRIMARY KEY REFERENCES accounts(id),
  photos_enabled INTEGER NOT NULL DEFAULT 0,
  enabled_at     INTEGER NOT NULL,
  last_sync_at   INTEGER
);

-- Synchronisierte Dokumente (profile, memory, template, workout, photo). data = verschlüsseltes JSON.
CREATE TABLE chat_docs (
  account_id  TEXT NOT NULL,
  kind        TEXT NOT NULL,
  id          TEXT NOT NULL,
  updated_at  INTEGER NOT NULL,         -- vom Gerät (Unix-Millisekunden), für Sortierung und Konflikte
  sort_key    INTEGER NOT NULL DEFAULT 0, -- z. B. Start des Trainings (ms) für die Verlaufsliste
  ref         TEXT,                     -- z. B. templateId eines Trainings
  size        INTEGER NOT NULL,
  data        BLOB NOT NULL,
  summary     BLOB,                     -- verschlüsselte Kurzfassung (Trainings: Kennzahlen je Übung) für Listen/Fortschritt
  PRIMARY KEY (account_id, kind, id)
) WITHOUT ROWID;
CREATE INDEX chat_docs_sort ON chat_docs(account_id, kind, sort_key);

-- Eingang: vom Chat angelegte Vorlagen und Notizen, die die App abholt.
CREATE TABLE chat_inbox (
  id          TEXT PRIMARY KEY,
  account_id  TEXT NOT NULL,
  kind        TEXT NOT NULL,            -- template | note
  created_at  INTEGER NOT NULL,
  grant_id    TEXT,
  client_name TEXT,
  data        BLOB NOT NULL
);
CREATE INDEX chat_inbox_account ON chat_inbox(account_id, created_at);

-- Kopplungscodes (App zeigt sie an, Eingabe auf der Anmeldeseite). Nur Hash gespeichert.
CREATE TABLE chat_pairing (
  code_hash  TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX chat_pairing_account ON chat_pairing(account_id);

-- OAuth-Clients aus Dynamic Client Registration (RFC 7591).
CREATE TABLE oauth_clients (
  client_id          TEXT PRIMARY KEY,
  client_secret_hash TEXT,              -- NULL = öffentlicher Client (PKCE)
  client_name        TEXT,
  redirect_uris      TEXT NOT NULL,     -- JSON-Array
  created_at         INTEGER NOT NULL
);

-- Freigaben: eine pro verbundenem Chat; widerrufbar aus der App.
CREATE TABLE oauth_grants (
  id           TEXT PRIMARY KEY,
  account_id   TEXT NOT NULL,
  client_id    TEXT NOT NULL,
  client_name  TEXT,
  scope        TEXT NOT NULL,
  created_at   INTEGER NOT NULL,
  last_used_at INTEGER
);
CREATE INDEX oauth_grants_account ON oauth_grants(account_id);

-- Autorisierungscodes (einmalig, 5 min) und Tokens (nur Hash).
CREATE TABLE oauth_codes (
  code_hash      TEXT PRIMARY KEY,
  grant_id       TEXT NOT NULL,
  client_id      TEXT NOT NULL,
  redirect_uri   TEXT NOT NULL,
  code_challenge TEXT NOT NULL,
  resource       TEXT,
  expires_at     INTEGER NOT NULL
);

CREATE TABLE oauth_tokens (
  token_hash TEXT PRIMARY KEY,
  grant_id   TEXT NOT NULL,
  kind       TEXT NOT NULL,             -- access | refresh
  expires_at INTEGER NOT NULL
);
CREATE INDEX oauth_tokens_grant ON oauth_tokens(grant_id);
