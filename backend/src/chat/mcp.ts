import type { Config, Env } from '../types';
import { rateLimit } from '../ratelimit';
import { bearer, unauthorized } from './oauth';
import { getSync } from './sync';
import { callTool, toolList } from './tools';

// Remote-MCP-Server (Streamable HTTP, zustandslos): POST /mcp mit JSON-RPC, Antwort als application/json.
// Kein SSE-Stream nötig (GET /mcp → 405), keine Server-Sitzung. Auth: OAuth-Bearer-Token (siehe oauth.ts).

export const PROTOCOL_VERSIONS = ['2025-11-25', '2025-06-18', '2025-03-26'];
const SERVER_INFO = { name: 'overfit', title: 'Overfit', version: '1.0.0' };

const INSTRUCTIONS = `Overfit is the user's strength-training and calisthenics log (phone app). These tools give you their profile, coach memory, templates, full workout history with every set, per-exercise progress and – if shared – body photos.
Typical flow: get_profile first (answer in the user's app language, respect injuries/limitations) → list_workouts / get_workout / get_exercise_progress to analyse → create_template to propose a plan (it lands in the app inbox; the user accepts it there and then simply trains it) → add_note to store an evaluation in the app.
Rules: weights are kg; convert for lb users. Suggest realistic progressions (e.g. +2.5 kg or +1 rep), detect plateaus, consider calisthenics progressions. Photos: factual, kind, focus on visible change. Data reflects the last sync from the phone.`;

type Rpc = { jsonrpc?: string; id?: string | number | null; method?: string; params?: Record<string, unknown> };

const PROMPTS = [
  {
    name: 'evaluate_last_workout',
    title: 'Letztes Training auswerten',
    description: 'Bewertet das letzte Training im Vergleich zu den vorherigen Einheiten derselben Vorlage.',
    text: 'Werte mein letztes Overfit-Training aus: Lies zuerst mein Profil, dann das letzte Training und die letzten 3–5 Einheiten derselben Vorlage sowie den Fortschritt der Hauptübungen. Gib eine Bewertung 1–10, was gut lief, was besser geht, und konkrete Zielwerte fürs nächste Mal. Biete an, die Vorlage anzupassen (create_template) und die Auswertung in der App zu speichern (add_note).',
  },
  {
    name: 'assess_progress',
    title: 'Fortschritt bewerten',
    description: 'Langfristige Einschätzung: Trends je Hauptübung, Plateaus, Körpergewicht, optional Fotos.',
    text: 'Bewerte meinen Trainingsfortschritt der letzten Monate mit Overfit: Profil und Coach-Gedächtnis lesen, die häufigsten Übungen mit get_exercise_progress analysieren, Körpergewichtsverlauf einbeziehen und – falls freigegeben – ein altes und ein aktuelles Foto vergleichen. Nenne Trends, Plateaus und die drei wichtigsten Stellschrauben.',
  },
  {
    name: 'plan_next_workout',
    title: 'Nächstes Training planen',
    description: 'Erstellt eine Vorlage für die nächste Einheit und legt sie in den Overfit-Eingang.',
    text: 'Plane mein nächstes Training: Lies Profil, Vorlagen und die letzten Trainings, wähle sinnvolle Übungen (IDs über search_exercise_catalog oder list_exercises) mit Sätzen, Zielgewichten und Pausen und lege die Vorlage mit create_template in meinen Overfit-Eingang. Begründe die Änderungen kurz.',
  },
];

function result(id: Rpc['id'], r: unknown) {
  return { jsonrpc: '2.0', id: id ?? null, result: r };
}
function rpcError(id: Rpc['id'], code: number, message: string) {
  return { jsonrpc: '2.0', id: id ?? null, error: { code, message } };
}

export async function handleMcp(req: Request, env: Env, cfg: Config): Promise<Response> {
  if (req.method === 'GET' || req.method === 'DELETE') {
    return new Response(null, { status: 405, headers: { allow: 'POST' } });
  }
  if (req.method !== 'POST') return new Response(null, { status: 405, headers: { allow: 'POST' } });
  const who = await bearer(req, env);
  if (!who || !(await getSync(env, who.accountId))) return unauthorized(req, cfg);
  await rateLimit(env, `mcp:${who.grantId}`, cfg.rlMcpPerMin, 60);

  let msg: unknown;
  try {
    const text = await req.text();
    if (text.length > 1024 * 1024) return Response.json(rpcError(null, -32600, 'Anfrage zu groß'), { status: 413 });
    msg = JSON.parse(text);
  } catch {
    return Response.json(rpcError(null, -32700, 'Parse error'), { status: 400 });
  }
  const batch = Array.isArray(msg);
  const items = (batch ? msg : [msg]) as Rpc[];
  if (!items.length) return Response.json(rpcError(null, -32600, 'Leerer Batch'), { status: 400 });
  const out: unknown[] = [];
  for (const m of items) {
    const r = await dispatch(m, env, cfg, who);
    if (r) out.push(r);
  }
  if (!out.length) return new Response(null, { status: 202 });
  const headers: Record<string, string> = { 'cache-control': 'no-store' };
  const pv = req.headers.get('mcp-protocol-version');
  if (pv) headers['mcp-protocol-version'] = pv;
  return Response.json(batch ? out : out[0], { headers });
}

async function dispatch(m: Rpc, env: Env, cfg: Config, who: Awaited<ReturnType<typeof bearer>> & object) {
  if (!m || typeof m !== 'object' || typeof m.method !== 'string') {
    // Antworten des Clients (z. B. auf Server-Anfragen) gibt es hier nicht – ignorieren.
    return 'id' in (m ?? {}) && !('result' in m || 'error' in m) ? rpcError(m.id, -32600, 'Ungültige Anfrage') : null;
  }
  const isNotification = m.id === undefined;
  if (isNotification) return null; // notifications/initialized, cancelled …
  const p = m.params ?? {};
  switch (m.method) {
    case 'initialize': {
      const requested = String(p.protocolVersion ?? '');
      return result(m.id, {
        protocolVersion: PROTOCOL_VERSIONS.includes(requested) ? requested : PROTOCOL_VERSIONS[0],
        capabilities: { tools: { listChanged: false }, prompts: { listChanged: false }, resources: {} },
        serverInfo: SERVER_INFO,
        instructions: INSTRUCTIONS,
      });
    }
    case 'ping':
      return result(m.id, {});
    case 'tools/list':
      return result(m.id, { tools: toolList() });
    case 'tools/call': {
      const name = String(p.name ?? '');
      const args = (p.arguments && typeof p.arguments === 'object' ? p.arguments : {}) as Record<string, unknown>;
      return result(m.id, await callTool(name, args, env, cfg, who));
    }
    case 'prompts/list':
      return result(m.id, { prompts: PROMPTS.map(({ text: _t, ...rest }) => rest) });
    case 'prompts/get': {
      const pr = PROMPTS.find((x) => x.name === p.name);
      if (!pr) return rpcError(m.id, -32602, 'Unbekannter Prompt');
      return result(m.id, { description: pr.description, messages: [{ role: 'user', content: { type: 'text', text: pr.text } }] });
    }
    case 'resources/list':
      return result(m.id, { resources: [] });
    case 'resources/templates/list':
      return result(m.id, { resourceTemplates: [] });
    default:
      return rpcError(m.id, -32601, `Methode nicht unterstützt: ${m.method}`);
  }
}
