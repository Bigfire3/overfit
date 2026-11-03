import type { Config, Env } from '../types';
import { b64uEncode, randomId } from '../crypto';
import { now } from '../ledger';
import { ChatData, type WorkoutSummary } from './data';
import { catalogEntry, searchCatalog } from './catalog';
import { encodePayload, SHARE_LIMITS, type ShareExercise, type ShareTemplate } from './share';
import { getSync } from './sync';
import { sealJson } from './vault';
import type { ChatPrincipal } from './oauth';

// MCP-Tools der KI-Chat-Verbindung. Beschreibungen sind bewusst ausführlich: Claude, ChatGPT und Gemini wählen
// Tools nur anhand dieser Texte aus. Alle Gewichte in kg (die App rechnet für die Anzeige in lb um).

type J = Record<string, unknown>;

export class ToolError extends Error {}

export interface ToolContent {
  content: ({ type: 'text'; text: string } | { type: 'image'; data: string; mimeType: string })[];
  structuredContent?: J;
  isError?: boolean;
}

interface Ctx {
  env: Env;
  cfg: Config;
  who: ChatPrincipal;
  data: ChatData;
}

interface ToolDef {
  name: string;
  title: string;
  description: string;
  inputSchema: J;
  annotations: J;
  run: (args: J, ctx: Ctx) => Promise<J | ToolContent>;
}

const READ = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };
const WRITE = { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false };
const obj = (properties: J, required: string[] = []): J => ({ type: 'object', properties, required, additionalProperties: false });
const DATE_DESC = 'Datum als YYYY-MM-DD oder ISO-8601 (date or ISO-8601 timestamp)';

function tzOf(profile: J | null): string {
  const tz = typeof profile?.timeZone === 'string' ? profile.timeZone : 'Europe/Berlin';
  try {
    new Intl.DateTimeFormat('en', { timeZone: tz });
    return tz;
  } catch {
    return 'Europe/Berlin';
  }
}

function fmt(ms: number | null | undefined, tz: string): string | null {
  if (!ms) return null;
  const p = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .formatToParts(new Date(ms))
      .map((x) => [x.type, x.value]),
  );
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`;
}

function parseDate(v: unknown, endOfDay = false): number | undefined {
  if (typeof v !== 'string' || !v) return undefined;
  const s = /^\d{4}-\d{2}-\d{2}$/.test(v) ? `${v}T${endOfDay ? '23:59:59.999' : '00:00:00'}Z` : v;
  const t = Date.parse(s);
  if (Number.isNaN(t)) throw new ToolError(`Ungültiges Datum: ${v}`);
  return t;
}

function int(v: unknown, def: number, min: number, max: number): number {
  const n = typeof v === 'number' ? Math.trunc(v) : def;
  return Math.min(Math.max(Number.isFinite(n) ? n : def, min), max);
}

const str = (v: unknown, max: number): string | undefined => (typeof v === 'string' && v.trim() ? v.trim().slice(0, max) : undefined);

async function profileAndTz(ctx: Ctx): Promise<{ profile: J | null; tz: string }> {
  const profile = await ctx.data.doc<J>('profile', 'me');
  return { profile, tz: tzOf(profile) };
}

function summaryOut(s: WorkoutSummary, tz: string, detail = false) {
  return {
    workoutId: s.id,
    name: s.name,
    templateId: s.templateId,
    start: fmt(s.startedAt, tz),
    durationMin: s.durationMin,
    inProgress: s.endedAt === null,
    setsDone: s.setsDone,
    setsPlanned: s.setsPlanned,
    volumeKg: s.volumeKg,
    feeling: s.feeling,
    coachRating: s.rating,
    hasPhoto: s.hasPhoto,
    exercises: detail
      ? s.exercises
      : s.exercises.map((e) => `${e.name}${e.topKg !== null ? ` · top ${e.topKg} kg × ${e.topKgReps ?? '?'}` : e.maxReps !== null ? ` · max ${e.maxReps} reps` : e.maxSec !== null ? ` · max ${e.maxSec} s` : ''}`),
  };
}

/** Datumsfelder (ms) eines Trainings lesbar machen. */
function humanizeWorkout(d: J, tz: string): J {
  const sets = (v: unknown) =>
    Array.isArray(v) ? v.map((s: J) => (s && typeof s.completedAt === 'number' ? { ...s, completedAt: fmt(s.completedAt, tz) } : s)) : v;
  return {
    ...d,
    startedAt: fmt(d.startedAt as number, tz),
    endedAt: fmt(d.endedAt as number, tz),
    exercises: Array.isArray(d.exercises) ? d.exercises.map((e: J) => ({ ...e, sets: sets(e.sets) })) : d.exercises,
  };
}

// ---------- Vorlage bauen ----------

interface KnownExercise {
  name: string;
  trackingType: string;
}

async function knownExercises(ctx: Ctx): Promise<Map<string, KnownExercise>> {
  const m = new Map<string, KnownExercise>();
  for (const t of await ctx.data.docs<J>('template', { limit: 500 })) {
    for (const e of (Array.isArray(t.data.exercises) ? t.data.exercises : []) as J[]) {
      if (typeof e.exerciseId === 'string') m.set(e.exerciseId, { name: String(e.name ?? e.exerciseId), trackingType: String(e.trackingType ?? 'WEIGHT_REPS') });
    }
  }
  for (const s of await ctx.data.summaries({ limit: 300 })) {
    for (const e of s.exercises) if (!m.has(e.exerciseId)) m.set(e.exerciseId, { name: e.name, trackingType: e.trackingType });
  }
  return m;
}

const TRACKING = ['WEIGHT_REPS', 'REPS', 'TIME', 'WEIGHT_TIME'];

async function buildTemplate(args: J, ctx: Ctx): Promise<{ template: ShareTemplate; warnings: string[] }> {
  const name = str(args.name, SHARE_LIMITS.name);
  if (!name) throw new ToolError('name fehlt');
  const list = Array.isArray(args.exercises) ? (args.exercises as J[]) : [];
  if (!list.length) throw new ToolError('exercises darf nicht leer sein');
  if (list.length > SHARE_LIMITS.exercises) throw new ToolError(`Höchstens ${SHARE_LIMITS.exercises} Übungen`);
  const known = await knownExercises(ctx);
  const custom: { ref: string; name: string; trackingType: string }[] = [];
  const warnings: string[] = [];
  const exercises: ShareExercise[] = [];
  for (const [i, e] of list.entries()) {
    if (!e || typeof e !== 'object') throw new ToolError(`exercises[${i}] ungültig`);
    const sets = (Array.isArray(e.sets) ? (e.sets as J[]) : []).slice(0, SHARE_LIMITS.sets + 1);
    if (sets.length > SHARE_LIMITS.sets) throw new ToolError(`exercises[${i}]: höchstens ${SHARE_LIMITS.sets} Sätze`);
    const outSets = sets.map((s) => {
      const o: { kg?: number; reps?: number; sec?: number } = {};
      for (const k of ['kg', 'reps', 'sec'] as const) {
        const v = s?.[k];
        if (typeof v === 'number' && Number.isFinite(v)) o[k] = k === 'kg' ? Math.round(v * 100) / 100 : Math.max(0, Math.round(v));
      }
      return o;
    });
    const ex: ShareExercise = { sets: outSets };
    const rest = e.rest_sec;
    if (typeof rest === 'number' && Number.isFinite(rest)) ex.restSec = Math.min(Math.max(Math.round(rest), 0), 3600);
    const note = str(e.note, 300);
    if (note) ex.note = note;
    const id = str(e.exercise_id, 120);
    const freeName = str(e.name, 80);
    if (id && catalogEntry(id)) {
      ex.id = id;
    } else if (id && known.has(id)) {
      // Eigene Übung des Nutzers: per Name zuordnen (Import nutzt die vorhandene eigene Übung gleichen Namens).
      const k = known.get(id)!;
      const ref = `c${custom.length + 1}`;
      custom.push({ ref, name: k.name.slice(0, 80), trackingType: TRACKING.includes(k.trackingType) ? k.trackingType : 'WEIGHT_REPS' });
      ex.ref = ref;
    } else if (freeName) {
      if (id) warnings.push(`Unbekannte ID ${id} – wird über den Namen „${freeName}“ zugeordnet.`);
      const tt = str(e.tracking_type, 20);
      if (tt && TRACKING.includes(tt)) {
        const ref = `c${custom.length + 1}`;
        custom.push({ ref, name: freeName, trackingType: tt });
        ex.ref = ref;
      } else {
        ex.name = freeName;
      }
    } else {
      throw new ToolError(`exercises[${i}]: unbekannte exercise_id ${id ?? '(leer)'} – erst search_exercise_catalog nutzen oder name angeben`);
    }
    exercises.push(ex);
  }
  const template: ShareTemplate = { v: 1, kind: 'template', name, exercises };
  const tnote = str(args.note, 500);
  if (tnote) template.note = tnote;
  if (custom.length) template.custom = custom;
  if (new TextEncoder().encode(JSON.stringify(template)).length > SHARE_LIMITS.bytes) throw new ToolError('Vorlage zu groß');
  return { template, warnings };
}

async function putInbox(ctx: Ctx, kind: 'template' | 'note', payload: J): Promise<string> {
  const c = await ctx.env.DB.prepare('SELECT COUNT(*) AS c FROM chat_inbox WHERE account_id = ?').bind(ctx.who.accountId).first<{ c: number }>();
  if ((c?.c ?? 0) >= ctx.cfg.chatInboxMax) {
    throw new ToolError(`Der Overfit-Eingang ist voll (${ctx.cfg.chatInboxMax} Einträge). Bitte zuerst die App öffnen, damit sie den Eingang abholt.`);
  }
  const id = `in_${randomId(12)}`;
  await ctx.env.DB.prepare('INSERT INTO chat_inbox (id, account_id, kind, created_at, grant_id, client_name, data) VALUES (?, ?, ?, ?, ?, ?, ?)')
    .bind(id, ctx.who.accountId, kind, now(), ctx.who.grantId, ctx.who.clientName, await sealJson(ctx.env, ctx.cfg, ctx.who.accountId, `inbox/${id}`, payload))
    .run();
  return id;
}

// ---------- Tools ----------

const setSchema = obj({
  kg: { type: 'number', description: 'Zielgewicht in kg (negativ = Unterstützung, z. B. Band). Bei lb-Nutzern vorher umrechnen (1 lb = 0,4536 kg).' },
  reps: { type: 'integer', minimum: 0, description: 'Ziel-Wiederholungen' },
  sec: { type: 'integer', minimum: 0, description: 'Zieldauer in Sekunden (für Halteübungen)' },
});

export const TOOLS: ToolDef[] = [
  {
    name: 'get_profile',
    title: 'Profil und Ziele lesen',
    description:
      'Reads the Overfit user profile: name, age/birth year, body weight history (kg), preferred unit (kg/lb), app language, goals, experience, limitations/injuries, free-text notes, and the long-term coach memory (what worked, plateaus, preferences). Call this FIRST in every conversation about training, and answer in the user\'s app language. Also returns when the data was last synced from the phone.',
    inputSchema: obj({}),
    annotations: READ,
    async run(_a, ctx) {
      const { profile, tz } = await profileAndTz(ctx);
      const memory = await ctx.data.doc<J>('memory', 'coach');
      const sync = await getSync(ctx.env, ctx.who.accountId);
      const weights = Array.isArray(profile?.bodyweight) ? (profile!.bodyweight as J[]).slice(-30) : [];
      const counts = await ctx.env.DB.prepare('SELECT kind, COUNT(*) AS c FROM chat_docs WHERE account_id = ? GROUP BY kind')
        .bind(ctx.who.accountId)
        .all<{ kind: string; c: number }>();
      return {
        profile: profile ? { ...profile, bodyweight: weights } : null,
        coachMemory: memory?.text ?? null,
        units: 'Alle Gewichte in kg. Bei unit = "lb" für den Nutzer in lb umrechnen.',
        timeZone: tz,
        today: fmt(Date.now(), tz)?.slice(0, 10),
        lastSyncedAt: sync?.lastSyncAt ? fmt(sync.lastSyncAt * 1000, tz) : null,
        photosShared: sync?.photosEnabled ?? false,
        counts: Object.fromEntries(counts.results.map((r) => [r.kind, r.c])),
      };
    },
  },
  {
    name: 'list_templates',
    title: 'Vorlagen auflisten',
    description:
      'Lists the user\'s workout templates (routines such as "Push", "Pull", "Legs") with id, name, number of exercises and exercise names. Use get_template for the full plan with sets, target weights and rest times.',
    inputSchema: obj({}),
    annotations: READ,
    async run(_a, ctx) {
      const list = await ctx.data.docs<J>('template', { limit: 500 });
      return {
        templates: list.map((t) => {
          const ex = Array.isArray(t.data.exercises) ? (t.data.exercises as J[]) : [];
          return { templateId: t.id, name: t.data.name ?? '', exercises: ex.map((e) => e.name ?? e.exerciseId), exerciseCount: ex.length };
        }),
      };
    },
  },
  {
    name: 'get_template',
    title: 'Vorlage lesen',
    description: 'Returns one workout template in full: exercises in order (exerciseId, name, trackingType), planned sets with target kg/reps/seconds, rest seconds and notes.',
    inputSchema: obj({ template_id: { type: 'string', description: 'templateId aus list_templates' } }, ['template_id']),
    annotations: READ,
    async run(a, ctx) {
      const t = await ctx.data.doc<J>('template', String(a.template_id ?? ''));
      if (!t) throw new ToolError('Vorlage nicht gefunden – list_templates aufrufen');
      return { templateId: a.template_id, ...t };
    },
  },
  {
    name: 'list_workouts',
    title: 'Vergangene Trainings auflisten',
    description:
      'Lists past (and a currently running) workouts, newest first, with date, duration, completed/planned sets, total volume (kg × reps), feeling (1–5), coach rating and per-exercise top sets. Filter by date range or template. Use get_workout for every set of one session, get_exercise_progress for long-term trends of one exercise.',
    inputSchema: obj({
      limit: { type: 'integer', minimum: 1, maximum: 100, default: 20 },
      from: { type: 'string', description: DATE_DESC },
      to: { type: 'string', description: DATE_DESC },
      template_id: { type: 'string', description: 'Nur Trainings dieser Vorlage' },
    }),
    annotations: READ,
    async run(a, ctx) {
      const { tz } = await profileAndTz(ctx);
      const list = await ctx.data.summaries({
        limit: int(a.limit, 20, 1, 100),
        from: parseDate(a.from),
        to: parseDate(a.to, true),
        templateId: str(a.template_id, 80),
      });
      return { workouts: list.map((s) => summaryOut(s, tz)), count: list.length };
    },
  },
  {
    name: 'get_workout',
    title: 'Training im Detail',
    description:
      'Returns one workout completely: every exercise and set (planned vs. actual kg/reps/seconds, completed or not, RPE), substitutions, notes, feeling, duration, photo ids and the in-app coach review if present, plus computed stats (volume, top set, estimated 1RM per exercise).',
    inputSchema: obj({ workout_id: { type: 'string', description: 'workoutId aus list_workouts' } }, ['workout_id']),
    annotations: READ,
    async run(a, ctx) {
      const id = String(a.workout_id ?? '');
      const { tz } = await profileAndTz(ctx);
      const d = await ctx.data.doc<J>('workout', id);
      if (!d) throw new ToolError('Training nicht gefunden – list_workouts aufrufen');
      const s = await ctx.data.summary(id);
      return { workoutId: id, workout: humanizeWorkout(d, tz), stats: s ? summaryOut(s, tz, true) : null };
    },
  },
  {
    name: 'get_current_workout',
    title: 'Laufendes Training',
    description:
      'Returns the workout that is currently in progress on the phone (sets done so far, what is next), or null if none is running. Data is as fresh as the last sync from the app.',
    inputSchema: obj({}),
    annotations: READ,
    async run(_a, ctx) {
      const { tz } = await profileAndTz(ctx);
      const running = (await ctx.data.summaries({ limit: 10 })).find((s) => s.endedAt === null);
      if (!running) return { current: null, message: 'Gerade läuft kein Training (Stand der letzten Synchronisation).' };
      const d = await ctx.data.doc<J>('workout', running.id);
      return { current: d ? humanizeWorkout(d, tz) : null, stats: summaryOut(running, tz, true), workoutId: running.id };
    },
  },
  {
    name: 'list_exercises',
    title: 'Trainierte Übungen',
    description:
      'Lists every exercise the user has trained (from history and templates) with exerciseId, name, number of sessions, last date and best estimated 1RM. Use it to find exercise ids for get_exercise_progress or to reuse the user\'s own (custom) exercises in create_template.',
    inputSchema: obj({ query: { type: 'string', description: 'Optional: Teil des Namens' } }),
    annotations: READ,
    async run(a, ctx) {
      const { tz } = await profileAndTz(ctx);
      const q = str(a.query, 80)?.toLowerCase();
      const m = new Map<string, { exerciseId: string; name: string; trackingType: string; sessions: number; last: number; bestE1rmKg: number | null; custom: boolean }>();
      for (const s of await ctx.data.summaries()) {
        for (const e of s.exercises) {
          if (e.sets === 0) continue;
          const x = m.get(e.exerciseId) ?? { exerciseId: e.exerciseId, name: e.name, trackingType: e.trackingType, sessions: 0, last: 0, bestE1rmKg: null, custom: !catalogEntry(e.exerciseId) };
          x.sessions++;
          x.last = Math.max(x.last, s.startedAt);
          if (e.bestE1rmKg !== null) x.bestE1rmKg = Math.max(x.bestE1rmKg ?? 0, e.bestE1rmKg);
          m.set(e.exerciseId, x);
        }
      }
      for (const [id, k] of await knownExercises(ctx)) {
        if (!m.has(id)) m.set(id, { exerciseId: id, name: k.name, trackingType: k.trackingType, sessions: 0, last: 0, bestE1rmKg: null, custom: !catalogEntry(id) });
      }
      const list = [...m.values()]
        .filter((x) => !q || x.name.toLowerCase().includes(q) || x.exerciseId.toLowerCase().includes(q))
        .sort((x, y) => y.sessions - x.sessions || y.last - x.last)
        .map((x) => ({ ...x, last: x.last ? fmt(x.last, tz)?.slice(0, 10) : null }));
      return { exercises: list };
    },
  },
  {
    name: 'get_exercise_progress',
    title: 'Fortschritt einer Übung',
    description:
      'Progress of one exercise over time: per session the top set, estimated 1RM (Epley), volume, total reps and longest hold, plus all-time bests and the change between the first and last session in the range. Use this to judge progression, plateaus and whether to increase weight. Identify the exercise by exercise_id (preferred, from list_exercises) or by name.',
    inputSchema: obj({
      exercise_id: { type: 'string' },
      name: { type: 'string', description: 'Alternativ: Name oder Namensteil' },
      limit: { type: 'integer', minimum: 1, maximum: 200, default: 50, description: 'Anzahl Einheiten (neueste)' },
      from: { type: 'string', description: DATE_DESC },
    }),
    annotations: READ,
    async run(a, ctx) {
      const { tz } = await profileAndTz(ctx);
      const id = str(a.exercise_id, 120);
      const q = str(a.name, 80)?.toLowerCase();
      if (!id && !q) throw new ToolError('exercise_id oder name angeben');
      const limit = int(a.limit, 50, 1, 200);
      const sessions: J[] = [];
      let matchedName: string | null = null;
      for (const s of await ctx.data.summaries({ from: parseDate(a.from) })) {
        const e = s.exercises.find((x) => (id ? x.exerciseId === id : x.name.toLowerCase().includes(q!)));
        if (!e || e.sets === 0) continue;
        matchedName ??= e.name;
        sessions.push({ date: fmt(s.startedAt, tz), workoutId: s.id, sets: e.sets, topKg: e.topKg, topKgReps: e.topKgReps, e1rmKg: e.bestE1rmKg, volumeKg: e.volumeKg, totalReps: e.totalReps, maxReps: e.maxReps, maxSec: e.maxSec, _t: s.startedAt });
        if (sessions.length >= limit) break;
      }
      if (!sessions.length) return { exercise: id ?? a.name, sessions: [], message: 'Keine erledigten Sätze zu dieser Übung gefunden.' };
      const best = (k: string) => {
        let b: J | null = null;
        for (const s of sessions) if (typeof s[k] === 'number' && (!b || (s[k] as number) > (b[k] as number))) b = s;
        return b ? { value: b[k], date: b.date } : null;
      };
      const first = sessions[sessions.length - 1];
      const last = sessions[0];
      const delta = (k: string) => (typeof first[k] === 'number' && typeof last[k] === 'number' ? Math.round(((last[k] as number) - (first[k] as number)) * 10) / 10 : null);
      const days = Math.round(((last._t as number) - (first._t as number)) / 86400000);
      return {
        exercise: { exerciseId: id ?? null, name: matchedName },
        sessionsCount: sessions.length,
        bests: { e1rmKg: best('e1rmKg'), topKg: best('topKg'), volumeKg: best('volumeKg'), maxReps: best('maxReps'), maxSec: best('maxSec') },
        change: { days, e1rmKg: delta('e1rmKg'), topKg: delta('topKg'), volumeKg: delta('volumeKg'), maxReps: delta('maxReps'), maxSec: delta('maxSec') },
        sessions: sessions.map(({ _t, ...s }) => s),
      };
    },
  },
  {
    name: 'search_exercise_catalog',
    title: 'Übungskatalog durchsuchen',
    description:
      'Searches the Overfit exercise catalog (≈1100 exercises, English names) and returns valid exercise ids with tracking type, muscles and equipment. Use this before create_template to get exact ids; search with English terms (e.g. "bench press", "pull up", "romanian deadlift").',
    inputSchema: obj({ query: { type: 'string' }, limit: { type: 'integer', minimum: 1, maximum: 30, default: 10 } }, ['query']),
    annotations: READ,
    async run(a) {
      return { results: searchCatalog(String(a.query ?? ''), int(a.limit, 10, 1, 30)) };
    },
  },
  {
    name: 'list_photos',
    title: 'Körperfotos auflisten',
    description:
      'Lists the body progress photos the user explicitly shared with the chat (id, date, linked workout). Only available if photo sharing is enabled in the app. Use get_photo to look at one; compare an old and a recent photo for visible changes, factually and kindly.',
    inputSchema: obj({ limit: { type: 'integer', minimum: 1, maximum: 100, default: 30 } }),
    annotations: READ,
    async run(a, ctx) {
      const sync = await getSync(ctx.env, ctx.who.accountId);
      if (!sync?.photosEnabled) return { photos: [], message: 'Fotos sind nicht freigegeben (in der App unter KI-Chat verbinden → Fotos teilen).' };
      const { tz } = await profileAndTz(ctx);
      const list = await ctx.data.docs<J>('photo', { limit: int(a.limit, 30, 1, 100) });
      return { photos: list.map((p) => ({ photoId: p.id, date: fmt(p.data.takenAt as number, tz), workoutId: p.data.workoutId ?? null })) };
    },
  },
  {
    name: 'get_photo',
    title: 'Körperfoto ansehen',
    description: 'Returns one shared body photo as an image (JPEG/PNG) together with its date. Only works if photo sharing is enabled in the app.',
    inputSchema: obj({ photo_id: { type: 'string' } }, ['photo_id']),
    annotations: READ,
    async run(a, ctx) {
      const sync = await getSync(ctx.env, ctx.who.accountId);
      if (!sync?.photosEnabled) throw new ToolError('Fotos sind nicht freigegeben.');
      const id = String(a.photo_id ?? '');
      const meta = await ctx.data.doc<J>('photo', id);
      const bytes = meta ? await ctx.data.photoBytes(id) : null;
      if (!meta || !bytes) throw new ToolError('Foto nicht gefunden – list_photos aufrufen');
      const { tz } = await profileAndTz(ctx);
      return {
        content: [
          { type: 'text', text: `Foto ${id} vom ${fmt(meta.takenAt as number, tz)}` },
          { type: 'image', data: b64uToB64(b64uEncode(bytes)), mimeType: String(meta.mimeType ?? 'image/jpeg') },
        ],
      };
    },
  },
  {
    name: 'create_template',
    title: 'Vorlage anlegen oder ändern',
    description:
      'Creates a new workout template (or a revised version of an existing one) and puts it into the user\'s Overfit inbox. The app shows it with a preview the next time it is opened, and the user taps "Übernehmen" to save it – nothing changes without the user\'s confirmation. Use exercise ids from search_exercise_catalog, list_exercises or get_template; for exercises not in the catalog pass a clear name (+ tracking_type). Weights always in kg. Returns an import link the user can also tap directly. Tell the user to open Overfit to accept it.',
    inputSchema: obj(
      {
        name: { type: 'string', maxLength: 80, description: 'Name der Vorlage, z. B. "Push A"' },
        note: { type: 'string', maxLength: 500 },
        replaces_template_id: { type: 'string', description: 'Optional: templateId der Vorlage, die ersetzt/aktualisiert werden soll' },
        message: { type: 'string', maxLength: 500, description: 'Kurze Begründung der Änderungen für den Nutzer (in seiner Sprache)' },
        exercises: {
          type: 'array',
          minItems: 1,
          maxItems: 50,
          items: obj({
            exercise_id: { type: 'string', description: 'Katalog-ID (z. B. "wg:bench-press") oder ID einer eigenen Übung aus list_exercises' },
            name: { type: 'string', description: 'Nur wenn keine ID bekannt ist' },
            tracking_type: { type: 'string', enum: TRACKING, description: 'Für neue eigene Übungen ohne ID' },
            rest_sec: { type: 'integer', minimum: 0, maximum: 3600 },
            note: { type: 'string', maxLength: 300 },
            sets: { type: 'array', maxItems: 30, items: setSchema },
          }),
        },
      },
      ['name', 'exercises'],
    ),
    annotations: WRITE,
    async run(a, ctx) {
      const { template, warnings } = await buildTemplate(a, ctx);
      const replaces = str(a.replaces_template_id, 80);
      if (replaces && !(await ctx.data.doc('template', replaces))) throw new ToolError('replaces_template_id unbekannt – list_templates aufrufen');
      const payload = await encodePayload(template);
      const link = `${ctx.cfg.shareBaseUrl}#${payload}`;
      const inboxId = await putInbox(ctx, 'template', {
        template,
        link,
        replacesTemplateId: replaces ?? null,
        message: str(a.message, 500) ?? null,
      });
      return {
        inboxId,
        status: 'Im Overfit-Eingang. Die App zeigt die Vorlage beim nächsten Öffnen mit Vorschau zum Übernehmen.',
        importLink: link,
        appLink: `overfit://t#${payload}`,
        warnings,
        template,
      };
    },
  },
  {
    name: 'add_note',
    title: 'Notiz oder Auswertung hinterlegen',
    description:
      'Saves a note, evaluation or progress assessment from this chat into the Overfit app (shown in the inbox and, with workout_id, at that workout in the history). Use it after analysing a workout or the overall progress, when the user wants to keep the result. Keep it concise and in the user\'s language; markdown is allowed.',
    inputSchema: obj(
      {
        text: { type: 'string', maxLength: 4000 },
        title: { type: 'string', maxLength: 80 },
        workout_id: { type: 'string', description: 'Optional: zugehöriges Training' },
        rating: { type: 'integer', minimum: 1, maximum: 10, description: 'Optionale Bewertung 1–10' },
      },
      ['text'],
    ),
    annotations: WRITE,
    async run(a, ctx) {
      const text = str(a.text, 4000);
      if (!text) throw new ToolError('text fehlt');
      const workoutId = str(a.workout_id, 80);
      if (workoutId && !(await ctx.data.doc('workout', workoutId))) throw new ToolError('workout_id unbekannt');
      const rating = typeof a.rating === 'number' ? int(a.rating, 0, 1, 10) : null;
      const inboxId = await putInbox(ctx, 'note', { note: { title: str(a.title, 80) ?? null, text, workoutId: workoutId ?? null, rating } });
      return { inboxId, status: 'Notiz liegt im Overfit-Eingang und erscheint beim nächsten Öffnen der App.' };
    },
  },
];

function b64uToB64(s: string): string {
  const b = s.replace(/-/g, '+').replace(/_/g, '/');
  return b + '='.repeat((4 - (b.length % 4)) % 4);
}

export async function callTool(name: string, args: J, env: Env, cfg: Config, who: ChatPrincipal): Promise<ToolContent> {
  const tool = TOOLS.find((t) => t.name === name);
  if (!tool) return { content: [{ type: 'text', text: `Unbekanntes Tool: ${name}` }], isError: true };
  const ctx: Ctx = { env, cfg, who, data: new ChatData(env, cfg, who.accountId) };
  try {
    const r = await tool.run(args ?? {}, ctx);
    if (Array.isArray((r as ToolContent).content)) return r as ToolContent;
    return { content: [{ type: 'text', text: JSON.stringify(r) }], structuredContent: r as J };
  } catch (e) {
    if (e instanceof ToolError) return { content: [{ type: 'text', text: e.message }], isError: true };
    throw e;
  }
}

export function toolList() {
  return TOOLS.map(({ name, title, description, inputSchema, annotations }) => ({ name, title, description, inputSchema, annotations: { title, ...annotations } }));
}
