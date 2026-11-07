import { describe, expect, it } from 'vitest';
// @ts-expect-error – Skript ohne Typen (reines ESM für den Deploy-Workflow)
import { plan } from '../scripts/deploy-secrets.mjs';

const plain = (env: Record<string, string>, existing: string[] = []) =>
  plan('production', env, existing) as { secrets: Record<string, string>; notes: string[]; errors: string[]; generated: string[] };

describe('scripts/deploy-secrets.mjs: KI-Schlüssel', () => {
  it('ohne ANTHROPIC_API_KEY → Fehler AI_KEY', () => {
    expect(plain({}).errors).toContain('AI_KEY');
  });

  it('nur GEMINI_API_KEY reicht nicht mehr (Fehler + Hinweis, Gemini wird nicht gesetzt)', () => {
    const p = plain({ GEMINI_API_KEY: 'AIza-x' });
    expect(p.errors).toContain('AI_KEY');
    expect(p.secrets.GEMINI_API_KEY).toBeUndefined();
    expect(p.notes.some((n) => n.includes('GEMINI_API_KEY'))).toBe(true);
  });

  it('ANTHROPIC_API_KEY aus GitHub wird gesetzt (getrimmt)', () => {
    const p = plain({ ANTHROPIC_API_KEY: '  sk-ant-x \n' });
    expect(p.errors).not.toContain('AI_KEY');
    expect(p.secrets.ANTHROPIC_API_KEY).toBe('sk-ant-x');
  });

  it('vorhandener Worker-Schlüssel reicht und bleibt unangetastet', () => {
    const p = plain({}, ['ANTHROPIC_API_KEY']);
    expect(p.errors).not.toContain('AI_KEY');
    expect(p.secrets.ANTHROPIC_API_KEY).toBeUndefined();
    expect(p.notes.some((n) => n.includes('ANTHROPIC_API_KEY') && n.includes('Worker'))).toBe(true);
  });

  it('leerer Wert zählt als fehlend', () => {
    expect(plain({ ANTHROPIC_API_KEY: '   ' }).errors).toContain('AI_KEY');
  });
});
