#!/usr/bin/env node
// Erzeugt src/routing.gen.json aus llm-routing.jsonc (Kommentare entfernt). Der Worker bündelt nur JSON.
// Läuft automatisch vor `npm test`, `npm run typecheck`, `npm run dev` und `npm run deploy` (package.json) und im Deploy-Workflow.
// Die erzeugte Datei ist nicht eingecheckt (.gitignore): llm-routing.jsonc ist die einzige Quelle.
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/** llm-routing.jsonc hat nur ganze Kommentarzeilen (wie wrangler.jsonc). */
export function readRouting(path = new URL('../llm-routing.jsonc', import.meta.url)) {
  const text = readFileSync(path, 'utf8')
    .split('\n')
    .filter((l) => !/^\s*\/\//.test(l))
    .join('\n');
  return JSON.parse(text);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const r = readRouting();
  // Grobe Prüfung schon hier (die vollständige macht src/routing.ts `parseRouting`, Tests und Worker-Start).
  for (const k of ['usdToEur', 'default', 'models', 'presets']) if (!(k in r)) throw new Error(`llm-routing.jsonc: ${k} fehlt`);
  writeFileSync(new URL('../src/routing.gen.json', import.meta.url), JSON.stringify(r, null, 1) + '\n');
  console.log(`routing.gen.json: ${Object.keys(r.models).length} Modelle, Voreinstellungen ${Object.keys(r.presets).join(', ')}`);
}
