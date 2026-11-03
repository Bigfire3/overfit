/**
 * Schmale Schnittstelle zum Zahlungsanbieter (Merchant of Record) für den Web-Kauf von Guthaben.
 * Heute gibt es nur Paddle (paddle.ts). Ein Wechsel (z. B. Lemon Squeezy, Stripe) heißt: diese Schnittstelle neu
 * implementieren und in `getPaymentProvider` auswählen; Bestellungen, Buchung, Idempotenz und Codes (web.ts) bleiben gleich.
 */

export type PaymentEnvironment = 'sandbox' | 'live';

export interface CheckoutRequest {
  /** Unsere Bestell-ID; kommt im Webhook über die Metadaten des Anbieters zurück. */
  orderId: string;
  kind: 'credit' | 'code';
  packageId: string;
  /** Endpreis inkl. USt in µ€. */
  priceMicro: number;
  creditMicro: number;
  locale: 'de' | 'en';
}

export interface CheckoutSession {
  /** Transaktions-ID beim Anbieter, öffnet den Checkout auf der Kaufseite. */
  providerTxnId: string;
  /** Vom Anbieter gelieferte Checkout-URL (falls vorhanden). */
  checkoutUrl: string | null;
}

/** Was die Kaufseite braucht, um den Checkout des Anbieters zu öffnen (öffentliche Werte). */
export interface ClientSettings {
  provider: string;
  environment: PaymentEnvironment;
  /** Client-seitiges Token des Anbieters (Paddle.js), öffentlich. */
  clientToken: string;
}

export type PaymentEvent =
  | {
      type: 'paid';
      providerTxnId: string;
      orderId: string | null;
      currency: string;
      /** Bezahlter Gesamtbetrag in Cent (inkl. USt). */
      totalMinor: number;
    }
  | {
      type: 'reversal';
      /** refund = Rückerstattung, chargeback = Rückbuchung durch die Bank. */
      action: 'refund' | 'chargeback';
      adjustmentId: string;
      providerTxnId: string;
      currency: string;
      /** Erstatteter Betrag in Cent (inkl. USt). */
      amountMinor: number;
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
