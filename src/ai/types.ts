export type ProviderId = 'openrouter' | 'anthropic' | 'openai';

export type KeyValidationResult = 'ok' | 'invalid' | 'network';

/**
 * Everything the app needs from an LLM backend. Implementations:
 * - direct providers using the user's own key (BYOK), see ./providers
 * - later: a credits proxy via our own backend (issue #13)
 */
export interface LlmProvider {
  id: ProviderId;
  label: string;
  /** Where users create a key. */
  keyUrl: string;
  /** Example prefix shown as input placeholder. */
  keyPlaceholder: string;
  /** Checks the key without spending tokens. */
  validateKey(apiKey: string): Promise<KeyValidationResult>;
}
