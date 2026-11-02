import * as SecureStore from 'expo-secure-store';

/** API key storage: iOS Keychain / Android Keystore. */
const KEY = 'overfit.apiKey';

export function loadApiKey(): Promise<string | null> {
  return SecureStore.getItemAsync(KEY);
}

export function saveApiKey(value: string): Promise<void> {
  return SecureStore.setItemAsync(KEY, value);
}

export function deleteApiKey(): Promise<void> {
  return SecureStore.deleteItemAsync(KEY);
}
