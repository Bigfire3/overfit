-- Web-Kauf über Stripe (Session AB). Rein additiv: alte Bestellungen (Paddle-Sandbox) bleiben lesbar.

-- Zahlungs-ID beim Anbieter, falls sie sich von der Checkout-ID unterscheidet (Stripe: Checkout Session cs_… beim
-- Anlegen, PaymentIntent pi_… bei Zahlung, Erstattung und Chargeback).
ALTER TABLE web_orders ADD COLUMN provider_payment_id TEXT;
-- Rechnungsland des Käufers (ISO 3166-1 alpha-2, vom Zahlungsanbieter), nur für die Auswertung der EU-Lieferschwelle
-- und der Kleinunternehmergrenze. Keine Namen, keine Adressen.
ALTER TABLE web_orders ADD COLUMN country TEXT;
-- Tatsächlich bezahlter Betrag in µ€ (aus dem Webhook).
ALTER TABLE web_orders ADD COLUMN paid_micro INTEGER;
CREATE INDEX web_orders_payment ON web_orders(provider, provider_payment_id);
CREATE INDEX web_orders_paid_at ON web_orders(paid_at);
