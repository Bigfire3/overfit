-- Fuel-Stufen (standard | pro | lite): jede Verbrauchsbuchung bekommt die angefragte und abgerechnete Stufe.
-- `model` bleibt das tatsächlich genutzte Modell (bei Ausweichen das der Ausweich-Stufe).
ALTER TABLE ledger ADD COLUMN tier TEXT;
