import { describe, expect, it } from 'vitest';
import { env as workerEnv } from 'cloudflare:workers';
import { summarizeRevenue, THRESHOLDS } from '../src/revenue';
import { baseEnv, rawFetch } from './helpers';
import type { Env } from '../src/types';

const M = 1_000_000;

describe('Web-Umsatz je Jahr und Land (Kleinunternehmer, EU-Lieferschwelle)', () => {
  it('trennt Inland, andere EU-Länder, Drittländer und unbekannt', () => {
    const { years, warnings } = summarizeRevenue(
      [
        { year: 2026, country: 'DE', orders: 3, totalMicro: 30 * M },
        { year: 2026, country: 'AT', orders: 1, totalMicro: 10 * M },
        { year: 2026, country: 'CH', orders: 1, totalMicro: 20 * M },
        { year: 2026, country: null, orders: 1, totalMicro: 5 * M },
      ],
      2026,
    );
    expect(years[0]).toMatchObject({ year: 2026, orders: 6, totalMicro: 65 * M, euCrossBorderMicro: 10 * M, unknownCountryMicro: 5 * M });
    expect(years[0].byCountry[0]).toEqual({ country: 'DE', orders: 3, totalMicro: 30 * M });
    expect(warnings).toEqual([]);
  });

  it('warnt ab 80 % der Schwellen, unbekanntes Land zählt zur Lieferschwelle', () => {
    const { warnings } = summarizeRevenue(
      [
        { year: 2026, country: 'FR', orders: 900, totalMicro: 7_000 * M },
        { year: 2026, country: null, orders: 100, totalMicro: 1_000 * M },
        { year: 2026, country: 'DE', orders: 2000, totalMicro: 12_000 * M },
        { year: 2025, country: 'DE', orders: 10, totalMicro: 100 * M },
      ],
      2026,
    );
    const codes = warnings.map((w) => w.code);
    expect(codes).toContain('eu_cross_border');
    expect(codes).toContain('small_business_prev_year'); // 20.000 € = 80 % von 25.000 € (zählt fürs Folgejahr)
    expect(codes).not.toContain('small_business_current_year');
    const eu = warnings.find((w) => w.code === 'eu_cross_border')!;
    expect(eu).toMatchObject({ year: 2026, valueMicro: 8_000 * M, thresholdMicro: THRESHOLDS.euCrossBorderMicro, share: 0.8 });
    expect(eu.message).toContain('Steuerberater');
  });

  it('erscheint im Admin-Usage-Report, nur mit Bestellungen der eigenen Umgebung', async () => {
    const DB = (workerEnv as unknown as Env).DB;
    const t = Math.floor(Date.now() / 1000);
    const ins = (id: string, environment: string, country: string | null, paid: number, reversed = 0) =>
      DB.prepare(
        `INSERT INTO web_orders (id, provider, environment, kind, package_id, price_micro, credit_micro, status, created_at, paid_at, reversed_micro, country, paid_micro)
         VALUES (?, 'stripe', ?, 'code', 'web.10', ?, ?, 'paid', ?, ?, ?, ?, ?)`,
      ).bind(id, environment, paid, paid, t, t, reversed, country, paid);
    await DB.batch([ins('o1', 'sandbox', 'DE', 10 * M), ins('o2', 'sandbox', 'AT', 20 * M, 5 * M), ins('o3', 'live', 'FR', 50 * M)]);
    const secret = 'admin-secret-0123456789';
    const r = await rawFetch('/v1/admin/usage', baseEnv({ ADMIN_SECRET: secret }), { headers: { authorization: `Bearer ${secret}` } });
    expect(r.status).toBe(200);
    const w = r.body.webRevenue;
    expect(w.environment).toBe('sandbox');
    expect(w.years[0]).toMatchObject({ totalMicro: 25 * M, euCrossBorderMicro: 15 * M, orders: 2 });
    expect(w.note).toContain('keine Steuerberatung');
  });

  it('Finanzbericht: echte Euro je Kanal, offenes Guthaben getrennt, KI-Kosten, Rohgewinn; Geschenktes ist keine Einnahme', async () => {
    const DB = (workerEnv as unknown as Env).DB;
    const t = Math.floor(Date.now() / 1000);
    await DB.batch([
      DB.prepare(`INSERT INTO accounts (id, created_at) VALUES ('acc', ?)`).bind(t),
      DB.prepare(
        `INSERT INTO web_orders (id, provider, environment, kind, package_id, price_micro, credit_micro, status, created_at, paid_at, reversed_micro, country, paid_micro)
         VALUES ('w1', 'stripe', 'sandbox', 'credit', 'web.10', ?, ?, 'paid', ?, ?, 0, 'DE', ?)`,
      ).bind(10 * M, 10 * M, t, t, 10 * M),
      DB.prepare(`INSERT INTO purchases VALUES ('google', 'g1', 'acc', 'overfit.fuel.5', ?, ?, ?)`).bind(6_990_000, 5 * M, t),
      DB.prepare(`INSERT INTO lots VALUES ('p', 'acc', 'purchase', ?, ?, NULL, ?, 'x')`).bind(5 * M, 4 * M, t),
      DB.prepare(`INSERT INTO lots VALUES ('g', 'acc', 'test_credit', ?, ?, ?, ?, 'x')`).bind(M, M, t + 1000, t),
      DB.prepare(`INSERT INTO review_gifts VALUES ('r', 'acc', 'invite_bonus', 5, 4, ?, ?, 'x')`).bind(t + 1000, t),
      DB.prepare(`INSERT INTO ledger (account_id, ts, type, amount_micro, ref) VALUES ('acc', ?, 'test_credit', ?, 'x')`).bind(t, M),
      DB.prepare(`INSERT INTO ledger (account_id, ts, type, amount_micro, cost_micro) VALUES ('acc', ?, 'usage', -1000, 800)`).bind(t),
      DB.prepare(`INSERT INTO ledger (account_id, ts, type, amount_micro, cost_micro, reviews) VALUES ('acc', ?, 'usage', 0, 300, -1)`).bind(t),
    ]);
    const secret = 'admin-secret-0123456789';
    const r = await rawFetch('/v1/admin/usage', baseEnv({ ADMIN_SECRET: secret }), { headers: { authorization: `Bearer ${secret}` } });
    const f = r.body.finance;
    const web = f.channels.find((c: any) => c.channel === 'web');
    expect(web).toMatchObject({ grossMicro: 10 * M, feesMicro: 500_000, netMicro: 9_500_000 });
    const play = f.channels.find((c: any) => c.channel === 'google');
    expect(play.grossMicro).toBe(6_990_000);
    expect(play.netMicro).toBe(Math.round((6_990_000 / 1.19) * 0.85));
    expect(f.channels.find((c: any) => c.channel === 'apple').grossMicro).toBe(0);
    expect(f.revenue.grossMicro).toBe(10 * M + 6_990_000); // die 1 € Test-Gutschrift zählt nicht
    expect(f.openCredit).toEqual({ purchasedMicro: 4 * M, giftedMicro: M, giftReviews: 4 });
    expect(f.aiCost).toMatchObject({ totalMicro: 1100, creditMicro: 800, giftReviewsMicro: 300, giftReviewRequests: 1 });
    expect(f.grossProfitMicro).toBe(f.revenue.netMicro - 1100);
  });
});
