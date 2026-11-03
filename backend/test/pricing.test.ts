import { describe, expect, it } from 'vitest';
import { DEFAULT_MARKUP_BP, getConfig } from '../src/config';
import { costMicro } from '../src/money';
import { currentPrices, USAGE_PROFILES } from '../src/providers';
import { baseEnv } from './helpers';

/**
 * Die Rechnung aus docs/monetization.md „Rechnung“ als Test: Bei vollständig verbrauchtem Guthaben bleibt in jedem
 * angebotenen Kanal und Paket nach USt (nur Store), Gebühr, Anbieterkosten und Spot-Rücklage mindestens 5 % vom Netto.
 */
// Web (Stripe, Kleinunternehmer nach § 19 UStG: keine USt). Gebühren laut stripe.com/de/pricing (geprüft 3.11.2026):
// EWR-Standardkarten 1,5 % + 0,25 €, EWR-Premiumkarten 2,8 % + 0,25 €, UK 2,5 % + 0,25 €, Nicht-EWR 3,15 % + 0,25 €,
// Währungsumrechnung + 2 %, Klarna ab 2,99 % + 0,35 €.
const STRIPE_MIX = { pct: 0.025, fixed: 0.25 }; // konservative Mischung (Rechengrundlage)
const STRIPE_WORST = [
  { name: 'Nicht-EWR-Karte mit Umrechnung', pct: 0.0315 + 0.02, fixed: 0.25 },
  { name: 'Klarna', pct: 0.0299, fixed: 0.35 },
];
const STORE_FEE = 0.15; // Small-Business-Programm, auf den Preis ohne USt
const STORE_VAT = [0.19, 0.27]; // DE und höchster EU-Satz (HU)
const SPOT_SHARE = 0.1; // Spot-/Test-Gutschriften als Anteil am Netto
const MIN_PROFIT_SHARE = 0.05; // Untergrenze (Ziel: 7 %)
const TARGET_PROFIT_SHARE = 0.07;

const webNet = (p: number, fee = STRIPE_MIX) => p - (p * fee.pct + fee.fixed);
const storeNet = (p: number, vat: number) => (p / (1 + vat)) * (1 - STORE_FEE);
/** Gewinn als Anteil am Netto: Guthaben wird zum Listenpreis × (1 + Aufschlag) verbraucht. */
const profitShare = (credit: number, net: number, markupBp: number) => (net - credit / (1 + markupBp / 10_000) - SPOT_SHARE * net) / net;
/** Nötiger Aufschlag für einen Zielgewinn: credit ÷ (1 + m) = net × (1 − Spot − Ziel). */
const neededMarkup = (credit: number, net: number, target: number) => credit / (net * (1 - SPOT_SHARE - target)) - 1;

describe('Aufschlag deckt jeden Kanal und jedes Paket', () => {
  const cfg = getConfig(baseEnv());
  const eur = (micro: number) => micro / 1_000_000;

  it('Web-Pakete (Stripe): Guthaben = Preis, Gewinn ≥ 5 % vom Netto, ab 10 € ≥ 7 %', () => {
    for (const p of Object.values(cfg.webPackages)) {
      const price = eur(p.priceMicro);
      const share = profitShare(price, webNet(price), cfg.markupBp);
      expect(share).toBeGreaterThanOrEqual(MIN_PROFIT_SHARE);
      if (price >= 10) expect(share).toBeGreaterThanOrEqual(TARGET_PROFIT_SHARE);
    }
  });

  it('Web: auch die teuerste einzelne Zahlart macht keinen Verlust', () => {
    for (const p of Object.values(cfg.webPackages)) {
      for (const fee of STRIPE_WORST) expect(profitShare(eur(p.priceMicro), webNet(eur(p.priceMicro), fee), cfg.markupBp), fee.name).toBeGreaterThan(0);
    }
  });

  it('Store-Pakete: Aufpreis offen, Gewinn ≥ 5 % vom Netto auch bei 27 % USt', () => {
    for (const [id, p] of Object.entries(cfg.packages)) {
      expect(p.priceMicro, id).toBeGreaterThan(p.creditMicro);
      for (const vat of STORE_VAT) {
        expect(profitShare(eur(p.creditMicro), storeNet(eur(p.priceMicro), vat), cfg.markupBp), `${id} bei ${vat * 100} % USt`).toBeGreaterThanOrEqual(MIN_PROFIT_SHARE);
      }
    }
  });

  it('Store-Preise sind gängige Preisstufen (x,99 €) und jeweils die niedrigste, die reicht', () => {
    for (const [id, p] of Object.entries(cfg.packages)) {
      expect(p.priceMicro % 1_000_000, id).toBe(990_000);
      const lower = eur(p.priceMicro) - 0.5; // nächst niedrigere Stufe (x,49 €)
      expect(profitShare(eur(p.creditMicro), storeNet(lower, 0.27), cfg.markupBp), `${id} eine Stufe tiefer`).toBeLessThan(MIN_PROFIT_SHARE);
    }
  });

  it('Aufschlag 28 % ist der kleinste ganze Prozentwert, der alle Pakete trägt', () => {
    const need = Math.max(...Object.values(cfg.webPackages).map((p) => neededMarkup(eur(p.priceMicro), webNet(eur(p.priceMicro)), MIN_PROFIT_SHARE)));
    expect(cfg.markupBp).toBe(Math.ceil(need * 100) * 100);
    // Ohne das 5-€-Paket reichten 27 % (Zielgewinn 7 % beim 10-€-Paket): 5 € kostet alle einen Punkt (DECISIONS.md).
    expect(neededMarkup(10, webNet(10), TARGET_PROFIT_SHARE)).toBeLessThan(0.27);
  });

  it('Standard-Aufschlag ist der Wert aus wrangler.jsonc', () => {
    expect(cfg.markupBp).toBe(DEFAULT_MARKUP_BP);
  });
});

describe('Kostenschätzung rechnet den Aufschlag ein', () => {
  it('Coach-Auswertung mit Gemini 3.6 Flash ≈ Listenpreis × 1,28', () => {
    const cfg = getConfig(baseEnv());
    const price = currentPrices()['gemini-3.6-flash'];
    const u = USAGE_PROFILES.coach;
    const usage = { input_tokens: u.inputTokens, output_tokens: u.outputTokens + 1000 };
    const list = costMicro(usage, price, 0);
    expect(costMicro(usage, price, cfg.markupBp)).toBe(Math.ceil((list * 12_800) / 10_000));
    expect(cfg.prices['gemini-3.6-flash']).toEqual(price);
  });
});
