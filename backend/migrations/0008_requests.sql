-- Anfrage-Protokoll (7.11.2026): jede KI-Anfrage über Guthaben, auch fehlgeschlagene, für die Ansicht „Abrechnung“ in der App.
-- Nur Metadaten (Zeit, Zweck, Modell, Token, Betrag, Status, Ausweichen), nie Prompt, Antwort oder Foto.
CREATE TABLE requests (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  account_id       TEXT NOT NULL,
  ts               INTEGER NOT NULL,              -- Unix-Sekunden (Eingang)
  duration_ms      INTEGER,
  purpose          TEXT,                          -- review | chat | autofill | plan | other (von der App)
  tier             TEXT NOT NULL,                 -- angefragte und abgerechnete Stufe
  status           TEXT NOT NULL,                 -- ok | failed
  error_code       TEXT,                          -- bei failed: API-Fehlercode (insufficient_funds, upstream_unavailable …)
  requested_model  TEXT NOT NULL,                 -- Modell der angefragten Stufe (zuerst versucht)
  model            TEXT,                          -- Modell, das geantwortet hat (bei failed: null)
  fallback_from_status INTEGER,                   -- HTTP-Status, mit dem das zuerst versuchte Modell ausfiel (nur bei Ausweichen)
  fallback_reason  TEXT,                          -- Kurztext des Ausfalls (höchstens 200 Zeichen, nie Inhalte)
  input_tokens     INTEGER NOT NULL DEFAULT 0,
  cache_read_tokens  INTEGER NOT NULL DEFAULT 0,
  cache_write_tokens INTEGER NOT NULL DEFAULT 0,
  output_tokens    INTEGER NOT NULL DEFAULT 0,
  charged_micro    INTEGER NOT NULL DEFAULT 0,    -- abgebuchtes Guthaben (0 bei geschenkter Auswertung und Fehlern)
  gift             INTEGER NOT NULL DEFAULT 0,    -- 1 = als geschenkte Auswertung abgerechnet
  photo            INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX requests_account ON requests(account_id, id);
CREATE INDEX requests_ts ON requests(ts);
