// Misst Frame 1 und Frame 3 jeder Workout-Guide-Übung (Quelle: unveränderte SVGs im Import-Cache) in Chromium:
// Bounding-Box der Figur (SVG-Einheiten, 512 x 512) und mittlere Linienstärke t = 2 * Fläche / Umfang.
// Ergebnis: tools/catalog/frame_metrics.json (eingecheckt; import_catalog.py liest es).
// Aufruf (Repo-Root, nach einem Import-Lauf, damit der Cache gefüllt ist):  node tools/catalog/measure_frames.js
// Braucht Playwright mit Chromium (in der Claude-Umgebung vorinstalliert).
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const here = __dirname;
const src = fs.readFileSync(path.join(here, 'import_catalog.py'), 'utf8');
const sha = /WG_SHA = "([0-9a-f]+)"/.exec(src)[1];
const cache = path.join(here, '.cache', `wg-${sha}`);
const N = 512;
(async () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(cache, 'manifest.json'), 'utf8'));
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent(`<canvas id=c width=${N} height=${N}></canvas>`);
  const out = {};
  for (const e of manifest.sort((a, b) => a.slug.localeCompare(b.slug))) {
    out[e.slug] = {};
    for (const idx of [1, 3]) {
      const frame = e.frames.find(f => f.index === idx);
      const svg = fs.readFileSync(path.join(cache, frame.path), 'utf8');
      out[e.slug][idx] = await page.evaluate(async ([svg, N]) => {
        const img = new Image();
        img.src = 'data:image/svg+xml;base64,' + btoa(svg);
        await img.decode();
        const x = document.getElementById('c').getContext('2d');
        x.clearRect(0, 0, N, N);
        x.drawImage(img, 0, 0, N, N);
        const d = x.getImageData(0, 0, N, N).data;
        const on = (i, y) => i >= 0 && y >= 0 && i < N && y < N && d[(y * N + i) * 4 + 3] > 127;
        let a = 0, per = 0, x0 = N, y0 = N, x1 = -1, y1 = -1;
        for (let y = 0; y < N; y++) for (let i = 0; i < N; i++) {
          if (!on(i, y)) continue;
          a++;
          if (!on(i - 1, y) || !on(i + 1, y) || !on(i, y - 1) || !on(i, y + 1)) per++;
          x0 = Math.min(x0, i); x1 = Math.max(x1, i); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
        }
        return { bbox: [x0, y0, x1 + 1, y1 + 1], t: +(2 * a / per).toFixed(2) };
      }, [svg, N]);
    }
  }
  await browser.close();
  fs.writeFileSync(path.join(here, 'frame_metrics.json'), '{\n' + Object.entries(out).map(([k, v]) => JSON.stringify(k) + ':' + JSON.stringify(v)).join(',\n') + '\n}\n');
  console.log(`Gemessen: ${Object.keys(out).length} Übungen`);
})();
