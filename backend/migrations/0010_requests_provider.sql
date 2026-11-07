-- Anbieter umschaltbar (llm-routing.jsonc, 7.11.2026): Protokoll speichert auch den Anbieter des zuerst versuchten und des
-- antwortenden Modells (anthropic | openai | gemini | workers-ai | openrouter). Alte Zeilen: null (= anthropic bzw. alte Stufen).
ALTER TABLE requests ADD COLUMN requested_provider TEXT;
ALTER TABLE requests ADD COLUMN provider TEXT;
