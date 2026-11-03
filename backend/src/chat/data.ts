import type { Config, Env } from '../types';
import { open, openJson } from './vault';

// Lesen der synchronisierten Daten für die MCP-Tools und Kennzahlen je Training.
// Schema der Dokumente: docs/backend-api.md („KI-Chat-Verbindung“). Eingaben vom Gerät werden defensiv gelesen.

type J = Record<string, unknown>;

export interface SetData {
  kg?: number;
  reps?: number;
  sec?: number;
  rpe?: number;
  completed?: boolean;
}

export interface ExerciseStat {
  exerciseId: string;
  name: string;
  trackingType: string;
  sets: number;
  /** Summe kg × Wdh. der erledigten Sätze (nur Sätze mit kg > 0) */
  volumeKg: number;
  totalReps: number;
  topKg: number | null;
  topKgReps: number | null;
  maxReps: number | null;
  maxSec: number | null;
  /** Geschätztes 1RM (Epley, nur Sätze mit 1–12 Wdh. und kg > 0) */
  bestE1rmKg: number | null;
}

export interface WorkoutSummary {
  id: string;
  name: string;
  templateId: string | null;
  startedAt: number;
  endedAt: number | null;
  durationMin: number | null;
  feeling: number | null;
  setsDone: number;
  setsPlanned: number;
  volumeKg: number;
  rating: number | null;
  hasPhoto: boolean;
  exercises: ExerciseStat[];
}

const num = (v: unknown): number | undefined => (typeof v === 'number' && Number.isFinite(v) ? v : undefined);
const str = (v: unknown, max = 200): string | undefined => (typeof v === 'string' ? v.slice(0, max) : undefined);
const arr = (v: unknown): J[] => (Array.isArray(v) ? (v.filter((x) => x && typeof x === 'object') as J[]) : []);
const round1 = (n: number) => Math.round(n * 10) / 10;

export function summarizeWorkout(id: string, d: J): WorkoutSummary {
  const exercises: ExerciseStat[] = [];
  let setsDone = 0;
  let setsPlanned = 0;
  let volume = 0;
  for (const e of arr(d.exercises)) {
    const sets = arr(e.sets);
    setsPlanned += sets.length;
    const done = sets.filter((s) => s.completed === true);
    setsDone += done.length;
    const st: ExerciseStat = {
      exerciseId: str(e.exerciseId, 120) ?? 'unknown',
      name: str(e.name, 120) ?? str(e.exerciseId, 120) ?? '?',
      trackingType: str(e.trackingType, 20) ?? 'WEIGHT_REPS',
      sets: done.length,
      volumeKg: 0,
      totalReps: 0,
      topKg: null,
      topKgReps: null,
      maxReps: null,
      maxSec: null,
      bestE1rmKg: null,
    };
    for (const s of done) {
      const kg = num(s.kg);
      const reps = num(s.reps);
      const sec = num(s.sec);
      if (reps !== undefined) {
        st.totalReps += reps;
        st.maxReps = Math.max(st.maxReps ?? 0, reps);
      }
      if (sec !== undefined) st.maxSec = Math.max(st.maxSec ?? 0, sec);
      if (kg !== undefined) {
        if (st.topKg === null || kg > st.topKg || (kg === st.topKg && (reps ?? 0) > (st.topKgReps ?? 0))) {
          st.topKg = kg;
          st.topKgReps = reps ?? null;
        }
        if (kg > 0 && reps !== undefined) {
          st.volumeKg += kg * reps;
          if (reps >= 1 && reps <= 12) st.bestE1rmKg = Math.max(st.bestE1rmKg ?? 0, round1(kg * (1 + reps / 30)));
        }
      }
    }
    st.volumeKg = round1(st.volumeKg);
    volume += st.volumeKg;
    exercises.push(st);
  }
  const startedAt = num(d.startedAt) ?? 0;
  const endedAt = num(d.endedAt) ?? null;
  const review = d.review && typeof d.review === 'object' ? (d.review as J) : null;
  return {
    id,
    name: str(d.name, 80) ?? '',
    templateId: str(d.templateId, 80) ?? null,
    startedAt,
    endedAt,
    durationMin: endedAt ? Math.round((endedAt - startedAt) / 60000) : null,
    feeling: num(d.feeling) ?? null,
    setsDone,
    setsPlanned,
    volumeKg: round1(volume),
    rating: review ? (num(review.rating) ?? null) : null,
    hasPhoto: Array.isArray(d.photoIds) && d.photoIds.length > 0,
    exercises,
  };
}

export class ChatData {
  constructor(
    private env: Env,
    private cfg: Config,
    public accountId: string,
  ) {}

  async doc<T = J>(kind: string, id: string): Promise<T | null> {
    const r = await this.env.DB.prepare('SELECT data FROM chat_docs WHERE account_id = ? AND kind = ? AND id = ?')
      .bind(this.accountId, kind, id)
      .first<{ data: ArrayBuffer }>();
    if (!r) return null;
    const aad = kind === 'photo' ? `photo/${id}` : `${kind}/${id}`;
    return openJson<T>(this.env, this.cfg, this.accountId, aad, r.data);
  }

  async docs<T = J>(kind: string, opts: { limit?: number; before?: number; ref?: string; from?: number; to?: number } = {}) {
    const where = ['account_id = ?', 'kind = ?'];
    const binds: unknown[] = [this.accountId, kind];
    if (opts.before !== undefined) (where.push('sort_key < ?'), binds.push(opts.before));
    if (opts.from !== undefined) (where.push('sort_key >= ?'), binds.push(opts.from));
    if (opts.to !== undefined) (where.push('sort_key <= ?'), binds.push(opts.to));
    if (opts.ref !== undefined) (where.push('ref = ?'), binds.push(opts.ref));
    const rows = await this.env.DB.prepare(
      `SELECT id, updated_at, sort_key, data FROM chat_docs WHERE ${where.join(' AND ')} ORDER BY sort_key DESC, id LIMIT ?`,
    )
      .bind(...binds, opts.limit ?? 1000)
      .all<{ id: string; updated_at: number; sort_key: number; data: ArrayBuffer }>();
    const out: { id: string; updatedAt: number; data: T }[] = [];
    for (const r of rows.results) {
      const aad = kind === 'photo' ? `photo/${r.id}` : `${kind}/${r.id}`;
      try {
        out.push({ id: r.id, updatedAt: r.updated_at, data: await openJson<T>(this.env, this.cfg, this.accountId, aad, r.data) });
      } catch {
        /* unlesbar → überspringen */
      }
    }
    return out;
  }

  /** Kurzfassungen der Trainings, neueste zuerst (aus der vorberechneten Spalte summary). */
  async summaries(opts: { limit?: number; before?: number; templateId?: string; from?: number; to?: number } = {}): Promise<WorkoutSummary[]> {
    const where = ['account_id = ?', "kind = 'workout'"];
    const binds: unknown[] = [this.accountId];
    if (opts.before !== undefined) (where.push('sort_key < ?'), binds.push(opts.before));
    if (opts.from !== undefined) (where.push('sort_key >= ?'), binds.push(opts.from));
    if (opts.to !== undefined) (where.push('sort_key <= ?'), binds.push(opts.to));
    if (opts.templateId !== undefined) (where.push('ref = ?'), binds.push(opts.templateId));
    const rows = await this.env.DB.prepare(
      `SELECT id, summary FROM chat_docs WHERE ${where.join(' AND ')} ORDER BY sort_key DESC, id LIMIT ?`,
    )
      .bind(...binds, opts.limit ?? 5000)
      .all<{ id: string; summary: ArrayBuffer | null }>();
    const out: WorkoutSummary[] = [];
    for (const r of rows.results) {
      if (!r.summary) continue;
      try {
        out.push(await openJson<WorkoutSummary>(this.env, this.cfg, this.accountId, `summary/${r.id}`, r.summary));
      } catch {
        /* überspringen */
      }
    }
    return out;
  }

  async summary(workoutId: string): Promise<WorkoutSummary | null> {
    const r = await this.env.DB.prepare("SELECT summary FROM chat_docs WHERE account_id = ? AND kind = 'workout' AND id = ?")
      .bind(this.accountId, workoutId)
      .first<{ summary: ArrayBuffer | null }>();
    if (!r?.summary) return null;
    return openJson<WorkoutSummary>(this.env, this.cfg, this.accountId, `summary/${workoutId}`, r.summary);
  }

  async photoBytes(photoId: string): Promise<Uint8Array | null> {
    const obj = await this.env.PHOTOS.get(`${this.accountId}/${photoId}`);
    if (!obj) return null;
    return open(this.env, this.cfg, this.accountId, `photo-bytes/${photoId}`, new Uint8Array(await obj.arrayBuffer()));
  }
}
