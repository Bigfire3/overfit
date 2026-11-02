import Anthropic from '@anthropic-ai/sdk';

import type { KeyValidationResult, LlmProvider, ProviderId } from './types';

/** Validates a bearer-token key against a cheap GET endpoint (no tokens spent). */
async function validateBearer(url: string, apiKey: string): Promise<KeyValidationResult> {
  try {
    const res = await fetch(url, { headers: { Authorization: `Bearer ${apiKey}` } });
    if (res.ok) return 'ok';
    return res.status === 401 || res.status === 403 ? 'invalid' : 'network';
  } catch {
    return 'network';
  }
}

const openrouter: LlmProvider = {
  id: 'openrouter',
  label: 'OpenRouter',
  keyUrl: 'https://openrouter.ai/settings/keys',
  keyPlaceholder: 'sk-or-…',
  validateKey: (apiKey) => validateBearer('https://openrouter.ai/api/v1/key', apiKey),
};

const anthropic: LlmProvider = {
  id: 'anthropic',
  label: 'Anthropic',
  keyUrl: 'https://platform.claude.com/settings/keys',
  keyPlaceholder: 'sk-ant-…',
  async validateKey(apiKey) {
    // The key belongs to the user and never leaves their device except to Anthropic,
    // so calling the API from the client is intended here.
    const client = new Anthropic({ apiKey, dangerouslyAllowBrowser: true, maxRetries: 0 });
    try {
      await client.models.list({ limit: 1 });
      return 'ok';
    } catch (error) {
      if (error instanceof Anthropic.AuthenticationError || error instanceof Anthropic.PermissionDeniedError) {
        return 'invalid';
      }
      return 'network';
    }
  },
};

const openai: LlmProvider = {
  id: 'openai',
  label: 'OpenAI',
  keyUrl: 'https://platform.openai.com/api-keys',
  keyPlaceholder: 'sk-…',
  validateKey: (apiKey) => validateBearer('https://api.openai.com/v1/models', apiKey),
};

export const PROVIDERS: LlmProvider[] = [openrouter, anthropic, openai];

export function getProvider(id: ProviderId): LlmProvider {
  const provider = PROVIDERS.find((p) => p.id === id);
  if (!provider) throw new Error(`Unknown provider: ${id}`);
  return provider;
}
