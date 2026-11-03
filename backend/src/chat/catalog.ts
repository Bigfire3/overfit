import catalogJson from '../catalog.gen.json';

// Schlanker Übungskatalog der App (erzeugt mit scripts/build-catalog.mjs), damit der Chat gültige Übungs-IDs findet.

type Row = [id: string, name: string, tracking: string, muscles: string, equipment: string];
const rows = (catalogJson as { version: string; exercises: Row[] }).exercises;
const byId = new Map<string, Row>(rows.map((r) => [r[0], r]));

export interface CatalogEntry {
  id: string;
  name: string;
  trackingType: string;
  muscles: string[];
  equipment: string;
}

const toEntry = (r: Row): CatalogEntry => ({ id: r[0], name: r[1], trackingType: r[2], muscles: r[3] ? r[3].split(',') : [], equipment: r[4] });
const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9äöüß]+/g, ' ').trim();

export function catalogEntry(id: string): CatalogEntry | null {
  const r = byId.get(id);
  return r ? toEntry(r) : null;
}

export const catalogVersion = (catalogJson as { version: string }).version;

/** Einfache Suche: alle Wörter müssen als Wortanfang in Name, Muskel oder Equipment vorkommen. */
export function searchCatalog(query: string, limit = 10): CatalogEntry[] {
  const q = norm(query);
  if (!q) return [];
  const words = q.split(' ');
  const compact = q.replace(/ /g, '');
  const scored: { r: Row; s: number }[] = [];
  for (const r of rows) {
    const name = norm(r[1]);
    const hay = `${name} ${norm(r[3])} ${norm(r[4])}`.split(' ');
    const nameCompact = name.replace(/ /g, '');
    const all = words.every((w) => hay.some((h) => h.startsWith(w))) || nameCompact.includes(compact);
    if (!all) continue;
    let s = 0;
    if (name === q) s += 100;
    if (name.startsWith(q)) s += 20;
    if (r[0].startsWith('wg:')) s += 5; // Workout-Guide-Einträge haben Bilder in der App
    s -= name.length / 100;
    scored.push({ r, s });
  }
  return scored
    .sort((a, b) => b.s - a.s)
    .slice(0, limit)
    .map((x) => toEntry(x.r));
}
