import { b64uEncode } from '../crypto';

// Teilen-Format v1 (docs/share-format.md): JSON → Raw Deflate → Base64url im URL-Fragment.

export interface ShareSet {
  kg?: number;
  reps?: number;
  sec?: number;
}

export interface ShareExercise {
  id?: string;
  name?: string;
  ref?: string;
  restSec?: number;
  note?: string;
  sets: ShareSet[];
}

export interface ShareTemplate {
  v: 1;
  kind: 'template';
  name: string;
  note?: string;
  exercises: ShareExercise[];
  custom?: { ref: string; name: string; trackingType: string }[];
}

export const SHARE_LIMITS = { exercises: 50, sets: 30, name: 80, bytes: 200 * 1024 };

export async function encodePayload(t: ShareTemplate): Promise<string> {
  const input = new TextEncoder().encode(JSON.stringify(t));
  const stream = new Blob([input]).stream().pipeThrough(new CompressionStream('deflate-raw'));
  return b64uEncode(new Uint8Array(await new Response(stream).arrayBuffer()));
}

export async function decodePayload(payload: string): Promise<unknown> {
  const pad = '='.repeat((4 - (payload.length % 4)) % 4);
  const bin = atob(payload.replace(/-/g, '+').replace(/_/g, '/') + pad);
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return JSON.parse(await new Response(stream).text());
}
