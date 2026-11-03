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
});
