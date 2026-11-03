-- Session AD: Einladungen heißen intern „invite“ (vorher „spot“), Einladungsbonus als geschenkte Coach-Auswertungen.
-- Rein umbenennend bzw. additiv: alle Testdaten bleiben erhalten. Bestehende Euro-Einladungsboni bleiben als geschenktes
-- Guthaben (lots.kind invite_bonus / invite_reward) gültig, bis sie verbraucht sind oder verfallen.

ALTER TABLE spot_tokens_used RENAME TO invite_tokens_used;
ALTER TABLE spot_redemptions RENAME TO invite_redemptions;
DROP INDEX IF EXISTS spot_inviter_month;
DROP INDEX IF EXISTS spot_month;
CREATE INDEX invite_inviter_month ON invite_redemptions(inviter_account_id, month);
CREATE INDEX invite_month ON invite_redemptions(month);

UPDATE lots SET kind = 'invite_bonus' WHERE kind = 'spot_bonus';
UPDATE lots SET kind = 'invite_reward' WHERE kind = 'spot_reward';
UPDATE ledger SET type = 'invite_bonus' WHERE type = 'spot_bonus';
UPDATE ledger SET type = 'invite_reward' WHERE type = 'spot_reward';

-- Geschenkte Coach-Auswertungen (Einladungsbonus): ein eigener Zähler, kein Euro-Betrag. Gilt nur für Coach-Auswertungen
-- mit dem günstigsten Bildmodell (Stufe standard). Verfällt (expires_at), wird vor dem Guthaben verbraucht.
CREATE TABLE review_gifts (
  id         TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  kind       TEXT NOT NULL,                    -- invite_bonus (Eingeladener) | invite_reward (Einladender)
  count      INTEGER NOT NULL CHECK (count > 0),
  remaining  INTEGER NOT NULL CHECK (remaining >= 0),
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  ref        TEXT
);
CREATE INDEX review_gifts_account ON review_gifts(account_id);

-- Auswertungen je Buchung: +n Gutschrift, −1 Verbrauch (dann amount_micro = 0), −n Verfall. NULL = reine Euro-Buchung.
ALTER TABLE ledger ADD COLUMN reviews INTEGER;
