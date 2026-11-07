-- Fabian (7.11.2026): Guthaben läuft nie ab, kein Startguthaben, keine Einladungen.
-- Nicht destruktiv: Alle Guthaben-Posten verlieren ihr Ablaufdatum (auch schon abgelaufene Restbeträge gelten wieder).
-- Die Tabellen der Einladungen (invite_tokens_used, invite_redemptions, review_gifts) bleiben bestehen, werden aber nicht mehr genutzt.
UPDATE lots SET expires_at = NULL WHERE expires_at IS NOT NULL;
