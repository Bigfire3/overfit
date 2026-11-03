// Rauchtest der Fallback-Seite: kodiert ein Beispiel wie die App (Raw Deflate + Base64url), lädt die Seite
// im Headless-Browser und prüft die Darstellung. Aufruf: node test/smoke.mjs
import { createRequire } from 'node:module';
import { deflateRawSync, deflateSync } from 'node:zlib';
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const server = http.createServer(async (req, res) => {
  let p = new URL(req.url, 'http://x').pathname;
  if (p.endsWith('/')) p += 'index.html';
  if (p === '/t') p = '/t/index.html';
  try {
    const body = await readFile(path.join(root, p));
    res.writeHead(200, { 'content-type': p.endsWith('.html') ? 'text/html' : p.endsWith('.css') ? 'text/css' : 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(404).end();
  }
}).listen(0);
const base = `http://localhost:${server.address().port}`;

const b64u = (buf) => Buffer.from(buf).toString('base64url');
const sample = {
  v: 1, kind: 'template', name: 'Push <b>A</b>', note: 'Heavy day',
  exercises: [
    { id: 'wg:bench-press', restSec: 150, sets: [{ kg: 60, reps: 8 }, { kg: 62.5, reps: 6 }] },
    { name: 'Klimmzug', sets: [{ kg: -20, reps: 8 }] },
    { ref: 'c1', sets: [{ sec: 45 }] },
  ],
  custom: [{ ref: 'c1', name: 'Handstand', trackingType: 'TIME' }],
};
const json = JSON.stringify(sample);

let failed = 0;
const check = (ok, msg) => { if (!ok) { failed++; console.error('FAIL', msg); } else console.log('ok  ', msg); };

const browser = await chromium.launch();
for (const [locale, heading] of [['en-US', 'Open in Overfit'], ['de-DE', 'In Overfit öffnen']]) {
  const ctx = await browser.newContext({ locale });
  const page = await ctx.newPage();
  await page.goto(`${base}/t/#${b64u(deflateRawSync(json))}`);
  await page.waitForSelector('.ex');
  const text = await page.innerText('main');
  check(text.includes('Push <b>A</b>'), `${locale}: Name wird als Text (nicht HTML) angezeigt`);
  check(text.includes('Bench Press') && text.includes('Klimmzug') && text.includes('Handstand'), `${locale}: Übungsnamen (id, name, ref)`);
  check(text.includes('60 kg') && text.includes('62') && text.includes('45 s'), `${locale}: Sätze`);
  check(text.includes('2:30'), `${locale}: Pause`);
  check(await page.locator('#open').innerText() === heading, `${locale}: Beschriftung "${heading}"`);
  const href = await page.getAttribute('#open', 'href');
  check(href.startsWith('overfit://t#') && href.endsWith(b64u(deflateRawSync(json))), `${locale}: overfit://-Link`);
  check((await page.getAttribute('#dl', 'href')).includes('/releases/latest/download/'), `${locale}: Download-Link`);
  await ctx.close();
}

const ctx = await browser.newContext({ locale: 'en-US' });
const page = await ctx.newPage();
await page.goto(`${base}/t/#${b64u(deflateSync(json))}`); // zlib-Header wird toleriert
await page.waitForSelector('.ex');
check(true, 'zlib-Header toleriert');
for (const [hash, needle] of [['', 'No workout found'], ['#%%%', 'damaged'], ['#' + b64u('nope'), 'damaged'], ['#' + b64u(deflateRawSync('{"v":2,"kind":"template","name":"x"}')), 'newer']]) {
  await page.goto(`${base}/t/${hash}`);
  await page.reload();
  await page.waitForSelector('h1');
  check((await page.innerText('main')).includes(needle), `Fehlerfall ${hash.slice(0, 12) || '(leer)'} → "${needle}"`);
  check(await page.locator('#open').isHidden(), `Fehlerfall ${hash.slice(0, 12) || '(leer)'}: kein „Öffnen“-Button`);
}
await page.goto(`${base}/`);
check((await page.innerText('h1')).includes('Train'), 'Startseite');
await browser.close();
server.close();
process.exit(failed ? 1 : 0);
