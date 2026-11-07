import type { Config, Env } from './types';

export interface PurchaseVerifier {
  /** Prüft den Kauf beim Store. `null` = ungültig. `orderId` muss pro Kauf eindeutig sein (Idempotenz). */
  verify(input: { productId: string; token: string }): Promise<{ orderId: string; productId: string } | null>;
}

export type Platform = 'google' | 'apple';

/** Nicht konfigurierte Prüfer: Funktion bleibt in Produktion abgeschaltet (503). */
export function unconfigured(): null {
  return null;
}

/**
 * TODO(Fabian/Folgearbeit): Echte Prüfer sind noch nicht implementiert, weil sie Zugangsdaten brauchen
 * (Google Play Developer API, Apple App Store Server API). Die Schnittstellen
 * oben sind fertig; hier die Implementierungen einhängen, sobald Secrets vorliegen (siehe backend/README.md).
 */
export function purchaseVerifier(_env: Env, _platform: Platform): PurchaseVerifier | null {
  return unconfigured();
}

/** Dev-/Testmodus: Token der Form `mock:<orderId>`. Nur mit DEV_MODE=true. */
export const mockPurchaseVerifier: PurchaseVerifier = {
  async verify({ productId, token }) {
    const m = /^mock:([A-Za-z0-9_.-]{1,64})$/.exec(token);
    return m ? { orderId: m[1], productId } : null;
  },
};

export function getPurchaseVerifier(env: Env, cfg: Config, platform: Platform): PurchaseVerifier | null {
  return cfg.purchaseMode === 'mock' ? mockPurchaseVerifier : purchaseVerifier(env, platform);
}
