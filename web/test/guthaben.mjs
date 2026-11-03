// Rauchtest der Kaufseite web/guthaben/: Backend und Paddle.js werden im Browser abgefangen (keine echten Aufrufe).
// Prüft Kauflink-Weg, Code-Weg, Testmodus-Hinweis, abgelaufene Links, Erfolgsseite mit Code und die Platzhalter.
// Aufruf: node test/guthaben.mjs
import { createRequire } from 'node:module';
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const API = 'https://api.test';

const types = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml' };
const server = http.createServer(async (req, res) => {
  let p = new URL(req.url, 'http://x').pathname;
  if (p.endsWith('/')) p += 'index.html';
  if (p === '/guthaben/config.js') {
    res.writeHead(200, { 'content-type': 'text/javascript' });
    return res.end(`window.OVERFIT_API = '${API}/';`);
  }
  try {
    const body = await readFile(path.join(root, p));
    res.writeHead(200, { 'content-type': types[path.extname(p)] ?? 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(404).end();
  }
}).listen(0);
const base = `http://localhost:${server.address().port}`;

let failed = 0;
const check = (ok, msg) => { if (!ok) { failed++; console.error('FAIL', msg); } else console.log('ok  ', msg); };

const PACKAGES = [5, 10, 20].map((e) => ({ id: `web.${e}`, priceMicro: e * 1e6, creditMicro: e * 1e6 }));
const FAKE_PADDLE = `window.Paddle = {
  Environment: { set: (e) => { window.__paddleEnv = e; } },
  Initialize: (o) => { window.__paddleInit = o; },
  Checkout: { open: (o) => { window.__paddleOpen = o; }, close: () => {} },
};`;

/** Neue Seite mit abgefangenem Backend. `state` steuert die Antworten. */
async function open(browser, locale, url, state) {
  const ctx = await browser.newContext({ locale });
  const page = await ctx.newPage();
  const calls = [];
  await page.route('https://cdn.paddle.com/**', (r) => r.fulfill({ contentType: 'text/javascript', body: FAKE_PADDLE }));
  await page.route(`${API}/**`, async (r) => {
    const u = new URL(r.request().url());
    const body = r.request().postData() ? JSON.parse(r.request().postData()) : null;
    calls.push({ path: u.pathname, body });
    const json = (status, b) => r.fulfill({ status, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify(b) });
    if (u.pathname === '/v1/web/session') {
      return json(200, { enabled: state.enabled ?? true, testMode: true, client: { provider: 'paddle', environment: 'sandbox', clientToken: 'test_tok' }, packages: PACKAGES, link: body?.token ? { status: state.link ?? 'valid' } : null });
    }
    if (u.pathname === '/v1/web/checkout') {
      if (state.checkoutError) return json(410, { error: { code: state.checkoutError } });
      return json(200, { orderId: 'ord_123456789012345678', transactionId: 'txn_1', checkoutUrl: null, client: { provider: 'paddle', environment: 'sandbox', clientToken: 'test_tok' }, testMode: true });
    }
    if (u.pathname.startsWith('/v1/web/orders/')) {
      state.polls = (state.polls ?? 0) + 1;
      const paid = state.polls >= 2;
      return json(200, { kind: state.kind ?? 'credit', status: paid ? 'paid' : 'open', priceMicro: 5e6, creditMicro: 5e6, testMode: true, code: paid && state.kind === 'code' ? 'ABCD-EFGH-JKMN' : null, codeStatus: paid && state.kind === 'code' ? 'active' : null });
    }
    return json(404, { error: { code: 'not_found' } });
  });
  await page.goto(url);
  return { ctx, page, calls };
}

const browser = await chromium.launch();

// 1. Aus der App (gültiger Link, Deutsch): Guthaben aufladen, Testmodus, 10 € vorgewählt, Paddle-Sandbox
{
  const { ctx, page, calls } = await open(browser, 'en-US', `${base}/guthaben/#t=tok_abcdefghijklmnopqrstuvwxyz012345&lang=de`, {});
  await page.waitForSelector('#shop:not([hidden])');
  const text = (await page.innerText('main')).replace(/\u00a0/g, ' ');
  check(text.includes('Guthaben aufladen'), 'App-Link: Titel „Guthaben aufladen“ (lang=de schlägt Browsersprache)');
  check(text.includes('Testmodus – keine echte Zahlung'), 'Testmodus-Hinweis sichtbar');
  check(text.includes('5 €') && text.includes('10 €') && text.includes('20 €'), 'Pakete 5/10/20 €');
  check(text.includes('10 € Guthaben'), 'Guthaben = Preis');
  check(text.includes('Impressum (folgt vor dem Start)') && text.includes('Datenschutz (folgt vor dem Start)') && text.includes('AGB'), 'Rechtstexte als Platzhalter markiert');
  check((await page.locator('.pack[aria-checked="true"]').innerText()).replace(/\u00a0/g, ' ').includes('10 €'), '10 € vorgewählt');
  check(calls[0].body.token === 'tok_abcdefghijklmnopqrstuvwxyz012345', 'Token geht an /v1/web/session');
  await page.locator('.pack', { hasText: '20 €' }).click();
  await page.click('#pay');
  await page.waitForFunction(() => window.__paddleOpen);
  const co = calls.find((c) => c.path === '/v1/web/checkout').body;
  check(co.kind === 'credit' && co.packageId === 'web.20' && co.token && co.locale === 'de', 'Checkout: credit, web.20, Token, de');
  check(await page.evaluate(() => window.__paddleEnv) === 'sandbox', 'Paddle.js in der Sandbox');
  check(await page.evaluate(() => window.__paddleInit.token) === 'test_tok', 'Paddle.js mit Client-Token');
  const opened = await page.evaluate(() => window.__paddleOpen);
  check(opened.transactionId === 'txn_1' && opened.settings.successUrl.includes('?order=ord_123456789012345678'), 'Checkout mit Transaktion und Erfolgs-URL');
  // Paddle meldet Abschluss → Erfolgsseite
  await page.evaluate(() => window.__paddleInit.eventCallback({ name: 'checkout.completed' }));
  await page.waitForSelector('#back:not([hidden])', { timeout: 10000 });
  const done = (await page.innerText('main')).replace(/\u00a0/g, ' ');
  check(done.includes('Danke!') && done.includes('5 € Guthaben sind auf deinem Konto'), 'Erfolgsseite nach Bestätigung');
  check(await page.getAttribute('#back', 'href') === 'overfit://guthaben', '„Zurück zur App“ öffnet overfit://guthaben');
  await ctx.close();
}

// 2. Ohne App (Englisch): Code kaufen → Erfolgsseite zeigt Code
{
  const { ctx, page, calls } = await open(browser, 'en-US', `${base}/guthaben/`, { kind: 'code' });
  await page.waitForSelector('#shop:not([hidden])');
  const text = (await page.innerText('main')).replace(/\u00a0/g, ' ');
  check(text.includes('Buy a credit code') && text.includes('Test mode – no real payment'), 'Ohne Token: Code kaufen (EN), Testmodus');
  check(await page.locator('#switch').isHidden(), 'Ohne Token kein Umschalter auf Konto');
  await page.click('#pay');
  await page.waitForFunction(() => window.__paddleOpen);
  const co = calls.find((c) => c.path === '/v1/web/checkout').body;
  check(co.kind === 'code' && !co.token && co.locale === 'en', 'Checkout: code ohne Token');
  await page.goto(`${base}/guthaben/?order=ord_123456789012345678&lang=en`);
  await page.waitForSelector('#codeBox:not([hidden])', { timeout: 10000 });
  check((await page.innerText('#codeValue')) === 'ABCD-EFGH-JKMN', 'Erfolgsseite zeigt den Code');
  check(((await page.innerText('main')).replace(/\u00a0/g, ' ')).includes('Redeem it in the app'), 'Hinweis zum Einlösen');
  await ctx.close();
}

// 3. Abgelaufener Link: Hinweis, Code-Weg angeboten
{
  const { ctx, page } = await open(browser, 'de-DE', `${base}/guthaben/#t=tok_abcdefghijklmnopqrstuvwxyz012345`, { link: 'expired' });
  await page.waitForSelector('#shop:not([hidden])');
  const text = (await page.innerText('main')).replace(/\u00a0/g, ' ');
  check(text.includes('abgelaufen') && text.includes('Guthaben-Code kaufen'), 'Abgelaufener Link → Hinweis und Code-Kauf');
  await ctx.close();
}

// 4. Link läuft während der Auswahl ab → Checkout meldet link_expired → Seite schaltet auf Code um
{
  const { ctx, page } = await open(browser, 'de-DE', `${base}/guthaben/#t=tok_abcdefghijklmnopqrstuvwxyz012345`, { checkoutError: 'link_expired' });
  await page.waitForSelector('#shop:not([hidden])');
  await page.click('#pay');
  await page.waitForSelector('#linkNote:not([hidden])');
  check((await page.innerText('#shopTitle')) === 'Guthaben-Code kaufen', 'link_expired beim Checkout → Code-Weg');
  await ctx.close();
}

// 5. Web-Kauf aus
{
  const { ctx, page } = await open(browser, 'de-DE', `${base}/guthaben/`, { enabled: false });
  await page.waitForSelector('#error:not([hidden])');
  check(((await page.innerText('main')).replace(/\u00a0/g, ' ')).includes('Gerade nicht verfügbar'), 'Web-Kauf aus → Hinweis');
  await ctx.close();
}

await browser.close();
server.close();
process.exit(failed ? 1 : 0);
