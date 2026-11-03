import type { Price } from './types';

export interface Usage {
  input_tokens?: number;
  cache_read_input_tokens?: number;
  cache_creation_input_tokens?: number;
  output_tokens?: number;
}

const BP = 10_000n;
const MTOK = 1_000_000n;

function ceilDiv(a: bigint, b: bigint): bigint {
  return (a + b - 1n) / b;
}

/** Kosten in Mikro-Euro, aufgerundet; nur Integer-Rechnung (BigInt). */
export function costMicro(usage: Usage, price: Price, markupBp: number): number {
  const sum =
    BigInt(usage.input_tokens ?? 0) * BigInt(price.in) +
    BigInt(usage.cache_read_input_tokens ?? 0) * BigInt(price.cacheRead) +
    BigInt(usage.cache_creation_input_tokens ?? 0) * BigInt(price.cacheWrite) +
    BigInt(usage.output_tokens ?? 0) * BigInt(price.out);
  return Number(ceilDiv(sum * (BP + BigInt(markupBp)), MTOK * BP));
}

/**
 * Gutschrift eines Pakets. Web und Codes: 1:1 zum Preis. Store: das im Paket festgelegte Guthaben; der Store-Preis
 * liegt offen darüber (USt und Store-Gebühr), docs/monetization.md „Rechnung“.
 */
export function packageCreditMicro(pkg: { priceMicro: number; creditMicro?: number }): number {
  return pkg.creditMicro ?? pkg.priceMicro;
}
