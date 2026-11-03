import { describe, expect, it } from 'vitest';
import { Client, baseEnv } from './helpers';

const verify = (c: Client, o: object) => c.call('POST', '/v1/purchases/verify', { platform: 'google', productId: 'overfit.fuel.10', purchaseToken: 'mock:order1', ...o });

describe('Käufe', () => {
  it('schreibt Preis × (1 − Gebühr) / (1 + Aufschlag) gut', async () => {
    const c = await Client.create();
    const r = await verify(c, {});
    expect(r.status).toBe(200);
    expect(r.body.creditedMicro).toBe(7_870_370);
    expect(r.body.alreadyCredited).toBe(false);
    expect(r.body.balanceMicro).toBe(7_870_370);
  });

  it('ist idempotent pro Kauf, auch über Konten hinweg', async () => {
    const a = await Client.create();
    const b = await Client.create();
    await verify(a, {});
    const again = await verify(a, {});
    expect(again.body.alreadyCredited).toBe(true);
    expect(again.body.balanceMicro).toBe(7_870_370);
    const other = await verify(b, {});
    expect(other.body.alreadyCredited).toBe(true);
    expect(other.body.balanceMicro).toBe(0);
    const parallel = await Promise.all([verify(a, { purchaseToken: 'mock:p2' }), verify(a, { purchaseToken: 'mock:p2' })]);
    expect(parallel.filter((r) => !r.body.alreadyCredited)).toHaveLength(1);
    expect((await a.call('GET', '/v1/account')).body.balanceMicro).toBe(2 * 7_870_370);
  });

  it('Konfiguration ändert die Gutschrift', async () => {
    const c = await Client.create(baseEnv({ MARKUP_BP: '0', STORE_FEE_BP: '0' }));
    expect((await verify(c, {})).body.creditedMicro).toBe(10_000_000);
  });

  it('unbekanntes Paket, ungültiger Token, falsche Plattform', async () => {
    const c = await Client.create();
    expect((await verify(c, { productId: 'nope' })).body.error.code).toBe('unknown_product');
    expect((await verify(c, { purchaseToken: 'garbage' })).body.error.code).toBe('purchase_invalid');
    expect((await verify(c, { platform: 'x' })).status).toBe(400);
  });

  it('ohne Konfiguration (Produktion) abgeschaltet, auch wenn Mock verlangt wird', async () => {
    const c = await Client.create(baseEnv({ PURCHASE_MODE: 'required' }));
    expect((await verify(c, {})).body.error.code).toBe('purchases_unavailable');
    const prod = await Client.create(baseEnv({ DEV_MODE: 'false', PURCHASE_MODE: 'mock' }));
    expect((await verify(prod, {})).status).toBe(503);
  });
});
