import type { Config, Env } from './types';

export interface PurchaseVerifier {
  /** Prüft den Kauf beim Store. `null` = ungültig. `orderId` muss pro Kauf eindeutig sein (Idempotenz). */
  verify(input: { productId: string; token: string }): Promise<{ orderId: string; productId: string } | null>;
}

export type AttestationResult = { ok: true; deviceKey: string } | { ok: false; reason: string };

export interface AttestationVerifier {
  /**
   * Prüft Play Integrity bzw. App Attest. `challenge` ist die vom Server ausgestellte Herausforderung
   * (Nonce bzw. clientDataHash). `deviceKey` ist eine geräte-stabile Kennung (Play Integrity Device Recall /
   * App-Attest-Schlüssel-Hash), die eine Neuinstallation übersteht.
   */
  verify(input: { token: string; challenge: string }): Promise<AttestationResult>;
}

export type Platform = 'google' | 'apple';

/** Nicht konfigurierte Prüfer: Funktion bleibt in Produktion abgeschaltet (503). */
export function unconfigured(): null {
  return null;
}

/**
 * TODO(Fabian/Folgearbeit): Echte Prüfer sind noch nicht implementiert, weil sie Zugangsdaten brauchen
 * (Google Play Developer API + Play Integrity, Apple App Store Server API + App Attest). Die Schnittstellen
 * oben sind fertig; hier die Implementierungen einhängen, sobald Secrets vorliegen (siehe backend/README.md).
 */
export function purchaseVerifier(_env: Env, _platform: Platform): PurchaseVerifier | null {
  return unconfigured();
}

export function attestationVerifier(_env: Env, _platform: Platform): AttestationVerifier | null {
  return unconfigured();
}

/** Dev-/Testmodus: Token der Form `mock:<orderId>` bzw. `mock:<deviceKey>`. Nur mit DEV_MODE=true. */
export const mockPurchaseVerifier: PurchaseVerifier = {
  async verify({ productId, token }) {
    const m = /^mock:([A-Za-z0-9_.-]{1,64})$/.exec(token);
    return m ? { orderId: m[1], productId } : null;
  },
};

export const mockAttestationVerifier: AttestationVerifier = {
  async verify({ token }) {
    const m = /^mock:([A-Za-z0-9_.-]{1,64})$/.exec(token);
    return m ? { ok: true, deviceKey: m[1] } : { ok: false, reason: 'mock_token_invalid' };
  },
};

/**
 * Testbetrieb (`DEPLOY_MODE=test`): keine Geräteprüfung. Token `test:<geräte-id>`; pro Gerät höchstens ein Spot
 * (Schlüssel `test-<id>`). Nur für die Test-Instanz, die Limits dort sind niedrig. Siehe docs/DECISIONS.md.
 */
export const testAttestationVerifier: AttestationVerifier = {
  async verify({ token }) {
    const m = /^test:([A-Za-z0-9_-]{16,64})$/.exec(token);
    return m ? { ok: true, deviceKey: `test-${m[1]}` } : { ok: false, reason: 'test_token_invalid' };
  },
};

export function getPurchaseVerifier(env: Env, cfg: Config, platform: Platform): PurchaseVerifier | null {
  return cfg.purchaseMode === 'mock' ? mockPurchaseVerifier : purchaseVerifier(env, platform);
}

export function getAttestationVerifier(env: Env, cfg: Config, platform: Platform): AttestationVerifier | null {
  if (cfg.testMode) return testAttestationVerifier;
  return cfg.attestationMode === 'mock' ? mockAttestationVerifier : attestationVerifier(env, platform);
}
