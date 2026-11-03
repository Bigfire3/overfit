import { describe, expect, it } from 'vitest';
import { costMicro, packageCreditMicro } from '../src/money';

const sonnet = { in: 1_840_000, cacheRead: 184_000, cacheWrite: 2_300_000, out: 9_200_000 };

describe('Geld', () => {
  it('rechnet Kosten mit Aufschlag, aufgerundet, als Integer', () => {
    // 1000 in + 500 out = 1840 + 4600 = 6440 µ€ → +8 % = 6955,2 → 6956
    expect(costMicro({ input_tokens: 1000, output_tokens: 500 }, sonnet, 800)).toBe(6956);
    expect(costMicro({}, sonnet, 800)).toBe(0);
    expect(costMicro({ input_tokens: 1 }, sonnet, 0)).toBe(2); // 1,84 → 2
  });
  it('berücksichtigt Cache-Read und Cache-Write', () => {
    const c = costMicro({ cache_read_input_tokens: 1_000_000, cache_creation_input_tokens: 1_000_000 }, sonnet, 0);
    expect(c).toBe(184_000 + 2_300_000);
  });
  it('überläuft bei großen Mengen nicht', () => {
    expect(costMicro({ input_tokens: 5_000_000_000, output_tokens: 5_000_000_000 }, sonnet, 800)).toBeGreaterThan(0);
  });
  it('Paket-Gutschrift: Web 1:1, Store mit festgelegtem Guthaben unter dem Store-Preis', () => {
    expect(packageCreditMicro({ priceMicro: 10_000_000 })).toBe(10_000_000);
    expect(packageCreditMicro({ priceMicro: 6_990_000, creditMicro: 5_000_000 })).toBe(5_000_000);
  });
});
