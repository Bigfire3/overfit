-- Inhaltsfreie Auswertung (GET /v1/admin/usage): Selbstkosten, Ausweichung und Foto-Anfrage je Verbrauchsbuchung.
-- Nur Zahlen und Markierungen, nie Prompt, Antwort oder Foto. Alte Buchungen bleiben NULL (Selbstkosten unbekannt).
ALTER TABLE ledger ADD COLUMN cost_micro INTEGER;            -- Anbieterkosten zum Listenpreis des tatsächlich genutzten Modells, ohne Aufschlag
ALTER TABLE ledger ADD COLUMN fallback INTEGER NOT NULL DEFAULT 0; -- 1 = ein anderes Modell als das gewählte hat geantwortet
ALTER TABLE ledger ADD COLUMN photo INTEGER NOT NULL DEFAULT 0;    -- 1 = Anfrage enthielt Körperfotos
CREATE INDEX IF NOT EXISTS ledger_ts ON ledger (ts);
