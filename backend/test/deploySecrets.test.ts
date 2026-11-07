import { describe, expect, it } from 'vitest';
// @ts-expect-error – Skript ohne Typen (reines ESM für den Deploy-Workflow)
import { plan } from '../scripts/deploy-secrets.mjs';


type Plan = { secrets: Record<string, string>; notes: string[]; errors: string[]; warnings: string[]; generated: string[]; llm: { name: string | null; requested: string | null; source: string } };
const full = (mode: string, env: Record<string, string>, existing: string[] = []) => plan(mode, env, existing) as Plan;

describe('scripts/deploy-secrets.mjs: KI-Schlüssel und Voreinstellung', () => {
  it('ohne Auswahl: Voreinstellung claude mit ANTHROPIC_API_KEY (getrimmt), im Worker gespeichert', () => {
    const p = full('production', { ANTHROPIC_API_KEY: '  sk-ant-x \n' });
    expect(p.errors).toEqual([]);
    expect(p.warnings).toEqual([]);
    expect(p.secrets.ANTHROPIC_API_KEY).toBe('sk-ant-x');
    expect(p.secrets.LLM_PRESET).toBe('claude');
    expect(p.llm.name).toBe('claude');
  });

  it('Auswahl openai mit OPENAI_API_KEY: Schlüssel und Wahl werden übertragen', () => {
    const p = full('test', { LLM_PRESET_INPUT: 'openai', OPENAI_API_KEY: 'sk-proj-x' });
    expect(p.errors).toEqual([]);
    expect(p.secrets).toMatchObject({ OPENAI_API_KEY: 'sk-proj-x', LLM_PRESET: 'openai' });
    expect(p.llm).toMatchObject({ name: 'openai', source: 'Auswahl beim Start' });
  });

  it('fehlt der Schlüssel der Auswahl: Warnung und Rückfall auf die nächste vollständige Voreinstellung', () => {
    const p = full('test', { LLM_PRESET_INPUT: 'openai' }, ['ANTHROPIC_API_KEY']);
    expect(p.errors).toEqual([]);
    expect(p.llm).toMatchObject({ name: 'claude', requested: 'openai' });
    expect(p.warnings.join()).toContain('OPENAI_API_KEY');
    expect(p.warnings.join()).toContain('claude');
  });

  it('ohne jeden Schlüssel bleibt nur Workers AI (gratis), mit Warnung', () => {
    const p = full('production', {});
    expect(p.errors).toEqual([]);
    expect(p.llm.name).toBe('gratis');
    expect(p.warnings.join()).toContain('ANTHROPIC_API_KEY');
  });

  it('Repo-Variable gilt, wenn nichts ausgewählt ist; Auswahl schlägt Variable', () => {
    expect(full('test', { LLM_PRESET_VAR: 'gemini', GEMINI_API_KEY: 'g' }).secrets.LLM_PRESET).toBe('gemini');
    expect(full('test', { LLM_PRESET_VAR: 'gemini', LLM_PRESET_INPUT: 'claude', GEMINI_API_KEY: 'g', ANTHROPIC_API_KEY: 'a' }).secrets.LLM_PRESET).toBe('claude');
  });

  it('„wie-bisher“ ohne Variable: gespeicherte Wahl im Worker bleibt unangetastet', () => {
    const p = full('test', { LLM_PRESET_INPUT: 'wie-bisher' }, ['LLM_PRESET', 'ANTHROPIC_API_KEY']);
    expect(p.errors).toEqual([]);
    expect(p.secrets.LLM_PRESET).toBeUndefined();
  });

  it('unbekannte Voreinstellung → Fehler', () => {
    expect(full('test', { LLM_PRESET_VAR: 'quatsch', ANTHROPIC_API_KEY: 'a' }).errors).toContain('LLM_PRESET');
  });

  it('vorhandener Worker-Schlüssel reicht und bleibt unangetastet; leerer Wert zählt als fehlend', () => {
    const p = full('production', { ANTHROPIC_API_KEY: '   ' }, ['ANTHROPIC_API_KEY']);
    expect(p.secrets.ANTHROPIC_API_KEY).toBeUndefined();
    expect(p.llm.name).toBe('claude');
    expect(full('production', { ANTHROPIC_API_KEY: '   ' }).llm.name).toBe('gratis');
  });

  it('Rückfall-Regel stimmt mit dem Worker überein (src/routing.ts)', async () => {
    const { choosePreset: worker } = await import('../src/routing');
    const { choosePreset: deploy } = await import('../scripts/deploy-secrets.mjs' as string);
    const keys = ['ANTHROPIC_API_KEY', 'OPENAI_API_KEY', 'GEMINI_API_KEY', 'OPENROUTER_API_KEY'];
    for (let mask = 0; mask < 16; mask++) {
      const ready = keys.filter((_, i) => mask & (1 << i));
      for (const wanted of ['claude', 'openai', 'gemini', 'gratis', 'gemischt', 'openrouter']) {
        const env = Object.fromEntries([...ready.map((k) => [k, 'x']), ['LLM_PRESET', wanted], ['AI', { run: async () => ({}), gateway: () => ({}) }]]);
        expect(deploy(wanted, new Set(ready)).name, `${wanted} mit ${ready}`).toBe(worker(env as never).name);
      }
    }
  });
});
