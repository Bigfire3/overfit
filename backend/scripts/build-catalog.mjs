// Erzeugt src/catalog.gen.json (schlanker Übungskatalog für den MCP-Server) aus dem App-Katalog.
// Aufruf: node scripts/build-catalog.mjs  (nach jedem Katalog-Update in shared/ erneut ausführen)
import { readFileSync, writeFileSync } from 'node:fs';
const src = new URL('../../shared/src/commonMain/composeResources/files/catalog/exercises.json', import.meta.url);
const cat = JSON.parse(readFileSync(src, 'utf8'));
const out = {
  version: cat.version,
  // [id, name, trackingType, primäre Muskeln (kommagetrennt), Equipment]
  exercises: cat.exercises.map((e) => [e.id, e.name, e.tracking, (e.primary ?? []).join(','), e.equipment ?? '']),
};
writeFileSync(new URL('../src/catalog.gen.json', import.meta.url), JSON.stringify(out));
console.log(`catalog.gen.json: ${out.exercises.length} Übungen`);
