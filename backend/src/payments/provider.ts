/**
 * Schmale Schnittstelle zum Zahlungsanbieter für den Web-Kauf von Guthaben. Heute: Stripe Checkout (stripe.ts).
 * Ein Wechsel heißt: diese Schnittstelle neu implementieren und in `getPaymentProvider` (web.ts) auswählen;
 * Bestellungen, Buchung, Idempotenz und Codes (web.ts) bleiben gleich.
 */

/** `sandbox` = Testmodus des Anbieters (Testkarten, keine echte Zahlung), `live` = echte Zahlungen. */
export type PaymentEnvironment = 'sandbox' | 'live';

export interface CheckoutRequest {
  /** Unsere Bestell-ID; kommt im Webhook über die Metadaten des Anbieters zurück. */
  orderId: string;
  kind: 'credit' | 'code';
  packageId: string;
  /** Endpreis in µ€ (Kleinunternehmer: ohne USt). */
  priceMicro: number;
  creditMicro: number;
  locale: 'de' | 'en';
  /** Rücksprung nach erfolgreicher Zahlung bzw. nach Abbruch (Kaufseite). */
  successUrl: string;
  cancelUrl: string;
}

export interface CheckoutSession {
  /** ID des Checkouts beim Anbieter (Stripe: Checkout Session `cs_…`). */
  providerTxnId: string;
  /** Gehostete Checkout-Seite des Anbieters; die Kaufseite leitet dorthin weiter. */
  checkoutUrl: string | null;
}

/** Was die Kaufseite über den Anbieter wissen darf (öffentliche Werte). */
export interface ClientSettings {
  provider: string;
  environment: PaymentEnvironment;
}

export type PaymentEvent =
  | {
      type: 'paid';
      /** Checkout-ID, unter der die Bestellung angelegt wurde. */
      providerTxnId: string;
      /** Zahlungs-ID, auf die sich Erstattung und Chargeback beziehen (Stripe: PaymentIntent `pi_…`). */
      paymentId: string | null;
      orderId: string | null;
      currency: string;
      /** Bezahlter Gesamtbetrag in Cent. */
      totalMinor: number;
      /** Rechnungsland des Käufers (ISO alpha-2), falls bekannt. */
      country: string | null;
    }
  | {
      type: 'reversal';
      /** refund = Rückerstattung, chargeback = Rückbuchung durch die Bank. */
      action: 'refund' | 'chargeback';
      /** Eindeutiger Schlüssel des Vorgangs (Idempotenz). */
      adjustmentId: string;
      paymentId: string;
      /** Unsere Bestell-ID aus den Metadaten, falls vorhanden (Erstattung vor der Zahlungsbestätigung). */
      orderId: string | null;
      currency: string;
      /** Betrag in Cent. Bei `cumulative` die Summe aller bisherigen Erstattungen dieser Zahlung. */
      amountMinor: number;
      cumulative: boolean;
    }
  | { type: 'ignored'; reason: string };

export interface PaymentProvider {
  readonly name: string;
  readonly environment: PaymentEnvironment;
  readonly client: ClientSettings;
  createCheckout(req: CheckoutRequest): Promise<CheckoutSession>;
  /** Prüft die Signatur (wirft `ApiError(401)`) und übersetzt das Ereignis. `rawBody` exakt wie empfangen. */
  parseWebhook(headers: Headers, rawBody: string): Promise<PaymentEvent>;
}
