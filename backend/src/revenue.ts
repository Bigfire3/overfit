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

/**
 * Gebühren-Annahmen für den Finanzbericht (Schätzung, Rechengrundlage wie backend/test/pricing.test.ts):
 * Stripe konservative Mischung 2,5 % + 0,25 € je Zahlung; Stores 15 % (Small-Business-Programm) auf den Preis ohne
 * USt, USt 19 % (DE) angenommen – Apple/Google führen die USt ab, sie ist keine Einnahme.
 */
export const FEE_ASSUMPTIONS = {
  stripePct: 0.025,
  stripeFixedMicro: 250_000,
  storeFeePct: 0.15,
  storeVatPct: 0.19,
} as const;

export interface ChannelRevenue {
  channel: 'web' | 'google' | 'apple';
  label: string;
  orders: number;
  /** Vom Käufer bezahlt (abzüglich Erstattungen), echte Euro. */
  grossMicro: number;
  /** Geschätzte Gebühren (Stripe) bzw. Gebühr + USt (Store). */
  feesMicro: number;
  /** Was bei Fabian ankommt (geschätzt). */
  netMicro: number;
  /** Dafür ausgegebenes Guthaben. */
  creditMicro: number;
}

/**
 * Einnahmen in echten Euro je Kanal im Zeitraum, offenes Guthaben (gekauft/geschenkt getrennt), KI-Kosten und Rohgewinn.
 * Geschenktes (Startguthaben, Admin-Gutschrift, Einladungsbonus) ist nie Einnahme: Einnahmen stammen nur aus
 * bezahlten Bestellungen (web_orders, purchases).
 */
export async function financeReport(env: Env, cfg: Config, from: number, to: number) {
  const f = FEE_ASSUMPTIONS;
  const environment = cfg.testMode || cfg.devMode ? 'sandbox' : 'live';
  const web = await env.DB.prepare(
    `SELECT COUNT(*) AS orders, COALESCE(SUM(COALESCE(paid_micro, price_micro)), 0) AS paid,
            COALESCE(SUM(reversed_micro), 0) AS reversed, COALESCE(SUM(credit_micro), 0) AS credit
     FROM web_orders WHERE paid_at IS NOT NULL AND paid_at >= ? AND paid_at < ? AND environment = ?`,
  )
    .bind(from, to, environment)
    .first<{ orders: number; paid: number; reversed: number; credit: number }>();
  const store = await env.DB.prepare(
    `SELECT platform, COUNT(*) AS orders, COALESCE(SUM(price_micro), 0) AS paid, COALESCE(SUM(credited_micro), 0) AS credit
     FROM purchases WHERE created_at >= ? AND created_at < ? GROUP BY platform`,
  )
    .bind(from, to)
    .all<{ platform: string; orders: number; paid: number; credit: number }>();

  const channels: ChannelRevenue[] = [];
  const wOrders = web?.orders ?? 0;
  const wGross = (web?.paid ?? 0) - (web?.reversed ?? 0);
  // Stripe behält die Gebühr auch bei Erstattungen; geschätzt auf den ursprünglich bezahlten Betrag.
  const wFees = Math.round((web?.paid ?? 0) * f.stripePct) + wOrders * f.stripeFixedMicro;
  channels.push({ channel: 'web', label: 'Web (Stripe)', orders: wOrders, grossMicro: wGross, feesMicro: wFees, netMicro: wGross - wFees, creditMicro: web?.credit ?? 0 });
  for (const [channel, label] of [['google', 'Google Play'], ['apple', 'App Store']] as const) {
    const row = store.results.find((r) => r.platform === channel);
    const gross = row?.paid ?? 0;
    const net = Math.round((gross / (1 + f.storeVatPct)) * (1 - f.storeFeePct));
    channels.push({ channel, label, orders: row?.orders ?? 0, grossMicro: gross, feesMicro: gross - net, netMicro: net, creditMicro: row?.credit ?? 0 });
  }

  const t = Math.floor(Date.now() / 1000);
  const open = await env.DB.prepare(
    `SELECT
       (SELECT COALESCE(SUM(remaining_micro), 0) FROM lots WHERE kind IN ('test_credit', 'invite_bonus', 'invite_reward') AND (expires_at IS NULL OR expires_at > ?1)) AS gifted,
       (SELECT COALESCE(SUM(remaining_micro), 0) FROM lots WHERE kind NOT IN ('test_credit', 'invite_bonus', 'invite_reward') AND (expires_at IS NULL OR expires_at > ?1)) AS purchased,
       (SELECT COALESCE(SUM(remaining), 0) FROM review_gifts WHERE expires_at > ?1) AS reviews`,
  )
    .bind(t)
    .first<{ gifted: number; purchased: number; reviews: number }>();
  const ai = await env.DB.prepare(
    `SELECT COALESCE(SUM(CASE WHEN reviews = -1 THEN 0 ELSE cost_micro END), 0) AS paid,
            COALESCE(SUM(CASE WHEN reviews = -1 THEN cost_micro ELSE 0 END), 0) AS gifted,
            SUM(CASE WHEN reviews = -1 THEN 1 ELSE 0 END) AS giftedRequests,
            SUM(CASE WHEN cost_micro IS NULL THEN 1 ELSE 0 END) AS unknown
     FROM ledger WHERE type = 'usage' AND ts >= ? AND ts < ?`,
  )
    .bind(from, to)
    .first<{ paid: number; gifted: number; giftedRequests: number | null; unknown: number | null }>();

  const sum = (k: 'grossMicro' | 'feesMicro' | 'netMicro') => channels.reduce((a, c) => a + c[k], 0);
  const aiCost = (ai?.paid ?? 0) + (ai?.gifted ?? 0);
  return {
    environment,
    channels,
    revenue: { grossMicro: sum('grossMicro'), feesMicro: sum('feesMicro'), netMicro: sum('netMicro') },
    openCredit: {
      purchasedMicro: open?.purchased ?? 0,
      giftedMicro: open?.gifted ?? 0,
      giftReviews: open?.reviews ?? 0,
    },
    aiCost: {
      totalMicro: aiCost,
      /** Aus Guthaben bezahlte Anfragen (gekauft oder geschenkte Euro). */
      creditMicro: ai?.paid ?? 0,
      /** Geschenkte Auswertungen (Einladungsbonus). */
      giftReviewsMicro: ai?.gifted ?? 0,
      giftReviewRequests: ai?.giftedRequests ?? 0,
      /** Ältere Buchungen ohne bekannte Selbstkosten. */
      unknownCostRequests: ai?.unknown ?? 0,
    },
    /** Netto-Einnahmen − KI-Kosten im Zeitraum (ohne Fixkosten, Steuer). */
    grossProfitMicro: sum('netMicro') - aiCost,
    assumptions: FEE_ASSUMPTIONS,
    note: 'Echte Euro. Geschenktes (Startguthaben, Admin-Gutschrift, Einladungsbonus) zählt nie als Einnahme. Gebühren geschätzt (assumptions).',
  };
}
