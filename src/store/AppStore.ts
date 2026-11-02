import AsyncStorage from '@react-native-async-storage/async-storage';
import { useSyncExternalStore } from 'react';

import type { ProviderId } from '@/ai/types';
import {
  isoDate,
  upsertWeight,
  type Language,
  type Profile,
  type ThemeMode,
  type WeightUnit,
} from '@/domain/profile';
import { deviceLanguage, deviceWeightUnit } from '@/i18n/device';

import { deleteApiKey, loadApiKey, saveApiKey } from './secureKey';

/** Everything except the API key, which lives in secure storage. */
export interface PersistedState {
  version: 1;
  profile: Profile;
  weights: { date: string; kg: number }[];
  settings: { language: Language; theme: ThemeMode };
  ai: { providerId: ProviderId };
  onboardedAt: string | null;
}

export interface AppState extends PersistedState {
  hydrated: boolean;
  hasApiKey: boolean;
}

const STORAGE_KEY = 'overfit/state/v1';

function initialState(): AppState {
  return {
    version: 1,
    profile: { name: '', birthYear: null, unit: deviceWeightUnit() },
    weights: [],
    settings: { language: deviceLanguage(), theme: 'dark' },
    ai: { providerId: 'openrouter' },
    onboardedAt: null,
    hydrated: false,
    hasApiKey: false,
  };
}

let state: AppState = initialState();
const listeners = new Set<() => void>();

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function persist(next: AppState) {
  const { hydrated: _hydrated, hasApiKey: _hasApiKey, ...persisted } = next;
  AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(persisted)).catch(() => {
    // Storage failures are non-fatal; state stays in memory for this session.
  });
}

function setState(update: (prev: AppState) => AppState) {
  state = update(state);
  if (state.hydrated) persist(state);
  listeners.forEach((l) => l());
}

export function getState(): AppState {
  return state;
}

/** Selectors must return existing references (state slices or primitives). */
export function useAppStore<T>(selector: (s: AppState) => T): T {
  return useSyncExternalStore(
    subscribe,
    () => selector(state),
    () => selector(state),
  );
}

export async function hydrate(): Promise<void> {
  const defaults = initialState();
  let stored: Partial<PersistedState> = {};
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (raw) stored = JSON.parse(raw) as Partial<PersistedState>;
  } catch {
    stored = {};
  }
  let hasApiKey = false;
  try {
    hasApiKey = Boolean(await loadApiKey());
  } catch {
    hasApiKey = false;
  }
  setState(() => ({
    ...defaults,
    ...stored,
    profile: { ...defaults.profile, ...stored.profile },
    settings: { ...defaults.settings, ...stored.settings },
    ai: { ...defaults.ai, ...stored.ai },
    hydrated: true,
    hasApiKey,
  }));
}

export const actions = {
  setName(name: string) {
    setState((s) => ({ ...s, profile: { ...s.profile, name: name.trim() } }));
  },
  setBirthYear(birthYear: number) {
    setState((s) => ({ ...s, profile: { ...s.profile, birthYear } }));
  },
  setUnit(unit: WeightUnit) {
    setState((s) => ({ ...s, profile: { ...s.profile, unit } }));
  },
  logWeight(kg: number, date: Date = new Date()) {
    setState((s) => ({ ...s, weights: upsertWeight(s.weights, { date: isoDate(date), kg }) }));
  },
  setLanguage(language: Language) {
    setState((s) => ({ ...s, settings: { ...s.settings, language } }));
  },
  setTheme(theme: ThemeMode) {
    setState((s) => ({ ...s, settings: { ...s.settings, theme } }));
  },
  setProvider(providerId: ProviderId) {
    setState((s) => ({ ...s, ai: { providerId } }));
  },
  async connectAi(providerId: ProviderId, apiKey: string) {
    await saveApiKey(apiKey);
    setState((s) => ({ ...s, ai: { providerId }, hasApiKey: true }));
  },
  async disconnectAi() {
    await deleteApiKey();
    setState((s) => ({ ...s, hasApiKey: false }));
  },
  completeOnboarding() {
    setState((s) => ({ ...s, onboardedAt: s.onboardedAt ?? new Date().toISOString() }));
  },
  async resetAll() {
    await deleteApiKey().catch(() => undefined);
    await AsyncStorage.removeItem(STORAGE_KEY).catch(() => undefined);
    state = { ...initialState(), hydrated: true };
    listeners.forEach((l) => l());
  },
};
