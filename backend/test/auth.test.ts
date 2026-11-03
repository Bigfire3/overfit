import { describe, expect, it } from 'vitest';
import { Client, baseEnv, rawFetch } from './helpers';
import { b64uEncode } from '../src/crypto';

describe('Geräte-Identität und Signatur', () => {
  it('registriert ein Gerät und liefert ein leeres Konto', async () => {
    const c = await Client.create();
    const r = await c.call('GET', '/v1/account');
    expect(r.status).toBe(200);
    expect(r.body.balanceMicro).toBe(0);
    expect(r.body.accountId).toBeTruthy();
  });

  it('Registrierung ist idempotent', async () => {
    const c = await Client.create();
    const a = (await c.call('GET', '/v1/account')).body.accountId;
    const r = await c.call('POST', '/v1/devices', { publicKey: b64uEncode(c.publicRaw) });
    expect(r.status).toBe(200);
    expect(r.body.accountId).toBe(a);
  });

  it('akzeptiert DER-Signaturen (Android Keystore)', async () => {
    const c = await Client.create(baseEnv(), { der: true });
    expect((await c.call('GET', '/v1/account')).status).toBe(200);
  });

  it('weist Replay mit gleicher Nonce ab', async () => {
    const c = await Client.create();
    const nonce = b64uEncode(crypto.getRandomValues(new Uint8Array(16)));
    expect((await c.call('GET', '/v1/account', undefined, { nonce })).status).toBe(200);
    const r = await c.call('GET', '/v1/account', undefined, { nonce });
    expect(r.status).toBe(401);
    expect(r.body.error.code).toBe('replay');
  });

  it('weist alte und zukünftige Zeitstempel ab', async () => {
    const c = await Client.create();
    const now = Math.floor(Date.now() / 1000);
    for (const ts of [now - 3600, now + 3600]) {
      const r = await c.call('GET', '/v1/account', undefined, { ts });
      expect(r.status).toBe(401);
      expect(r.body.error.code).toBe('timestamp_out_of_range');
    }
  });

  it('weist manipulierten Body, Pfad und fremde Signatur ab', async () => {
    const c = await Client.create();
    const headers = await c.sign('PUT', '/v1/account/profile', JSON.stringify({ displayName: 'A' }));
    const res = await rawFetch('/v1/account/profile', baseEnv(), { method: 'PUT', headers, body: JSON.stringify({ displayName: 'B' }) });
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('bad_signature');
    const other = await Client.create();
    const h2 = await other.sign('GET', '/v1/account', '');
    const res2 = await rawFetch('/v1/account', baseEnv(), { headers: { ...h2, 'x-overfit-device': c.deviceId } });
    expect(res2.body.error.code).toBe('bad_signature');
  });

  it('unbekanntes Gerät und fehlende Header', async () => {
    const c = await Client.create(baseEnv(), { register: false });
    expect((await c.call('GET', '/v1/account')).body.error.code).toBe('unknown_device');
    expect((await rawFetch('/v1/account', baseEnv())).body.error.code).toBe('unauthorized');
  });

  it('Registrierung verlangt Besitz des privaten Schlüssels', async () => {
    const a = await Client.create(baseEnv(), { register: false });
    const b = await Client.create(baseEnv(), { register: false });
    // b meldet den Schlüssel von a an, signiert aber mit b
    b.deviceId = a.deviceId;
    const r = await b.call('POST', '/v1/devices', { publicKey: b64uEncode(a.publicRaw) });
    expect(r.status).toBe(401);
  });

  it('Rate-Limit greift', async () => {
    const e = baseEnv({ RL_DEVICE_PER_MIN: '3' });
    const c = await Client.create(e, { register: false });
    await c.call('POST', '/v1/devices', { publicKey: b64uEncode(c.publicRaw) });
    const codes: number[] = [];
    for (let i = 0; i < 5; i++) codes.push((await c.call('GET', '/v1/account')).status);
    expect(codes).toContain(429);
  });

  it('Profil: Anzeigename setzen und löschen', async () => {
    const c = await Client.create();
    expect((await c.call('PUT', '/v1/account/profile', { displayName: '  Fabian  ' })).body.displayName).toBe('Fabian');
    expect((await c.call('GET', '/v1/account')).body.displayName).toBe('Fabian');
    expect((await c.call('PUT', '/v1/account/profile', { displayName: 'x'.repeat(25) })).status).toBe(400);
    expect((await c.call('PUT', '/v1/account/profile', { displayName: null })).body.displayName).toBeNull();
  });

  it('Health und Config sind öffentlich', async () => {
    expect((await rawFetch('/v1/health', baseEnv())).body.ok).toBe(true);
    const cfg = await rawFetch('/v1/config', baseEnv());
    expect(cfg.body.packages[0].creditMicro).toBeGreaterThan(0);
  });
});
