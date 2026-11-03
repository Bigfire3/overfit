import type { Config, Env } from './types';

/**
 * Werkzeug, keine Steuerberatung: Web-Umsatz je Kalenderjahr, je Rechnungsland und gesamt, mit Warnung ab 80 % der
 * Schwellen, die für einen Kleinunternehmer (§ 19 UStG) beim Direktverkauf relevant sind. Die Schwellen und ihre Regeln
 * erklärt docs/monetization.md „Kleinunternehmer und Schwellen“; prüfen muss das der Steuerberater.
 */
export const THRESHOLDS = {
  /** EU-weite Lieferschwelle für grenzüberschreitende B2C-Dienstleistungen (elektronisch) an andere EU-Länder, Vorjahr oder laufendes Jahr. */
  euCrossBorderMicro: 10_000_000_000,
  /** Kleinunternehmergrenze (ab 2025): Gesamtumsatz im Vorjahr. */
  smallBusinessPrevYearMicro: 25_000_000_000,
  /** Kleinunternehmergrenze (ab 2025): Gesamtumsatz im laufenden Jahr (Überschreiten wirkt sofort). */
  smallBusinessCurrentYearMicro: 100_000_000_000,
  warnShare: 0.8,
} as const;

/** Sitzland des Verkäufers; Umsätze dorthin sind nicht grenzüberschreitend. */
export const HOME_COUNTRY = 'DE';
export const EU_COUNTRIES = new Set([
  'AT', 'BE', 'BG', 'HR', 'CY', 'CZ', 'DK', 'EE', 'FI', 'FR', 'DE', 'GR', 'HU', 'IE', 'IT', 'LV', 'LT', 'LU', 'MT', 'NL',
  'PL', 'PT', 'RO', 'SK', 'SI', 'ES', 'SE',
]);

export interface YearRevenue {
  year: number;
  orders: number;
  /** Bezahlt minus erstattet/zurückgebucht (µ€). */
  totalMicro: number;
  /** Davon an andere EU-Länder (zählt zur Lieferschwelle). */
  euCrossBorderMicro: number;
  /** Ohne bekanntes Rechnungsland (zur Sicherheit wie grenzüberschreitend behandeln). */
  unknownCountryMicro: number;
  byCountry: { country: string; orders: number; totalMicro: number }[];
}

export interface RevenueWarning {
  code: 'eu_cross_border' | 'small_business_prev_year' | 'small_business_current_year';
  year: number;
  valueMicro: number;
  thresholdMicro: number;
  share: number;
  message: string;
}

const eur = (micro: number) => `${(micro / 1e6).toLocaleString('de-DE', { maximumFractionDigits: 0 })} €`;

/** Reine Auswertung (testbar ohne DB): Zeilen je Jahr und Land → Jahre und Warnungen. */
export function summarizeRevenue(
  rows: { year: number; country: string | null; orders: number; totalMicro: number }[],
  currentYear: number,
): { years: YearRevenue[]; warnings: RevenueWarning[] } {
  const byYear = new Map<number, YearRevenue>();
  for (const r of rows) {
    const y = byYear.get(r.year) ?? { year: r.year, orders: 0, totalMicro: 0, euCrossBorderMicro: 0, unknownCountryMicro: 0, byCountry: [] };
    const c = r.country ?? '??';
    y.orders += r.orders;
    y.totalMicro += r.totalMicro;
    if (!r.country) y.unknownCountryMicro += r.totalMicro;
    else if (EU_COUNTRIES.has(c) && c !== HOME_COUNTRY) y.euCrossBorderMicro += r.totalMicro;
    y.byCountry.push({ country: c, orders: r.orders, totalMicro: r.totalMicro });
    byYear.set(r.year, y);
  }
  const years = [...byYear.values()].sort((a, b) => b.year - a.year);
  for (const y of years) y.byCountry.sort((a, b) => b.totalMicro - a.totalMicro);

  const warnings: RevenueWarning[] = [];
  const warn = (code: RevenueWarning['code'], year: number, value: number, threshold: number, what: string) => {
    const share = value / threshold;
    if (share >= THRESHOLDS.warnShare) {
      warnings.push({
        code, year, valueMicro: value, thresholdMicro: threshold, share: Math.round(share * 1000) / 1000,
        message: `${what} ${year}: ${eur(value)} von ${eur(threshold)} (${Math.round(share * 100)} %). Mit dem Steuerberater prüfen, bevor die Schwelle überschritten wird.`,
      });
    }
  };
  for (const y of years.filter((y) => y.year === currentYear || y.year === currentYear - 1)) {
    // Unbekanntes Land zählt hier vorsichtshalber mit.
    warn('eu_cross_border', y.year, y.euCrossBorderMicro + y.unknownCountryMicro, THRESHOLDS.euCrossBorderMicro, 'Web-Umsatz an andere EU-Länder');
  }
  const cur = byYear.get(currentYear);
  const prev = byYear.get(currentYear - 1);
  if (prev) warn('small_business_prev_year', prev.year, prev.totalMicro, THRESHOLDS.smallBusinessPrevYearMicro, 'Web-Umsatz gesamt (Kleinunternehmergrenze Vorjahr)');
  if (cur) {
    // Für das Folgejahr zählt der Umsatz dieses Jahres gegen die 25.000-€-Grenze; ab 80 % davon warnen.
    warn('small_business_prev_year', cur.year, cur.totalMicro, THRESHOLDS.smallBusinessPrevYearMicro, 'Web-Umsatz gesamt (Kleinunternehmergrenze, zählt fürs Folgejahr)');
    warn('small_business_current_year', cur.year, cur.totalMicro, THRESHOLDS.smallBusinessCurrentYearMicro, 'Web-Umsatz gesamt (Kleinunternehmergrenze laufendes Jahr)');
  }
  return { years, warnings };
}

/** Web-Umsatz der Umgebung dieses Workers (Testbetrieb: Stripe-Testmodus, Produktion: live). */
export async function webRevenueReport(env: Env, cfg: Config, nowSec = Math.floor(Date.now() / 1000)) {
  const environment = cfg.testMode || cfg.devMode ? 'sandbox' : 'live';
  const rows = await env.DB.prepare(
    `SELECT CAST(strftime('%Y', paid_at, 'unixepoch') AS INTEGER) AS year, country,
            COUNT(*) AS orders, SUM(COALESCE(paid_micro, price_micro) - reversed_micro) AS totalMicro
     FROM web_orders WHERE paid_at IS NOT NULL AND environment = ?
     GROUP BY year, country`,
  )
    .bind(environment)
    .all<{ year: number; country: string | null; orders: number; totalMicro: number }>();
  const currentYear = new Date(nowSec * 1000).getUTCFullYear();
  return {
    environment,
    note:
      'Nur Web-Verkäufe (Stripe). Store-Erlöse (Auszahlungen von Apple/Google) zählen ggf. zum Gesamtumsatz dazu. ' +
      'Werkzeug, keine Steuerberatung: Schwellen und Folgen mit dem Steuerberater prüfen (docs/monetization.md).',
    thresholds: THRESHOLDS,
    ...summarizeRevenue(rows.results, currentYear),
  };
}
