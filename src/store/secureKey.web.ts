/**
 * Web fallback: browsers have no Keychain/Keystore, so the key lives in localStorage.
 * Fine for development/preview; the mobile apps use expo-secure-store (secureKey.ts).
 */
const KEY = 'overfit.apiKey';

function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

export async function loadApiKey(): Promise<string | null> {
  return storage()?.getItem(KEY) ?? null;
}

export async function saveApiKey(value: string): Promise<void> {
  storage()?.setItem(KEY, value);
}

export async function deleteApiKey(): Promise<void> {
  storage()?.removeItem(KEY);
}
