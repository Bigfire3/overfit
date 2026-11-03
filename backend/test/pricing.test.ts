import { describe, expect, it } from 'vitest';
import { DEFAULT_MARKUP_BP, getConfig } from '../src/config';
import { costMicro } from '../src/money';
import { currentPrices, USAGE_PROFILES } from '../src/providers';
import { baseEnv } from './helpers';

/**
 * Die Rechnung aus docs/monetization.md „Rechnung“, als Test: Bei vollständig verbrauchtem Guthaben bleibt in
 * jedem angebotenen Kanal und Paket nach USt, Gebühr, Anbieterkosten und Spot-Allowance mindestens der Zielgewinn.
 */
const VAT = 0.19; // DE; andere EU-Sätze (17–27 %) weichen leicht ab
const PADDLE_PCT = 0.05;
const PADDLE_FIXED_EUR = 0.5;
const STORE_FEE = 0.15; // Small-Business-Programm, auf den Preis ohne USt
const SPOT_SHARE = 0.1; // Spot-/Test-Gutschriften als Anteil am Netto
const MIN_PROFIT_SHARE = 0.05; // Untergrenze des Zielgewinns (Ziel: 7 %)

const webNet = (p: number) => p - (p * VAT) / (1 + VAT) - (p * PADDLE_PCT + PADDLE_FIXED_EUR);
const storeNet = (p: number) => (p / (1 + VAT)) * (1 - STORE_FEE);
const profitShare = (price: number, net: number, markupBp: number) => {
  const providerCost = price / (1 + markupBp / 10_000); // Guthaben = Preis, verbraucht zum Listenpreis × (1 + Aufschlag)
  return (net - providerCost - SPOT_SHARE * net) / net;
};

describe('Aufschlag deckt jeden Kanal und jedes Paket', () => {
  const cfg = getConfig(baseEnv());
  const eur = (micro: number) => micro / 1_000_000;

  it('Store-Pakete', () => {
    for (const p of Object.values(cfg.packages)) expect(profitShare(eur(p.priceMicro), storeNet(eur(p.priceMicro)), cfg.markupBp)).toBeGreaterThanOrEqual(MIN_PROFIT_SHARE);
  });
  it('Web-Pakete (Paddle)', () => {
    for (const p of Object.values(cfg.webPackages)) expect(profitShare(eur(p.priceMicro), webNet(eur(p.priceMicro)), cfg.markupBp)).toBeGreaterThanOrEqual(MIN_PROFIT_SHARE);
  });
  it('ein 5-€-Web-Paket würde den nötigen Aufschlag deutlich anheben (deshalb 10/20/50 €)', () => {
    expect(profitShare(5, webNet(5), cfg.markupBp)).toBeLessThan(MIN_PROFIT_SHARE);
  });
  it('Standard-Aufschlag ist der Wert aus wrangler.jsonc', () => {
    expect(cfg.markupBp).toBe(DEFAULT_MARKUP_BP);
  });
});

describe('Kostenschätzung rechnet den Aufschlag ein', () => {
  it('Coach-Auswertung mit Gemini 3.6 Flash ≈ Listenpreis × 1,69', () => {
    const cfg = getConfig(baseEnv());
    const price = currentPrices()['gemini-3.6-flash'];
    const u = USAGE_PROFILES.coach;
    const usage = { input_tokens: u.inputTokens, output_tokens: u.outputTokens + 1000 };
    const list = costMicro(usage, price, 0);
    expect(costMicro(usage, price, cfg.markupBp)).toBe(Math.ceil((list * 16_900) / 10_000));
    expect(cfg.prices['gemini-3.6-flash']).toEqual(price);
  });
});
