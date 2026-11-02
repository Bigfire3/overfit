import { getLocales } from 'expo-localization';

import type { Language, WeightUnit } from '@/domain/profile';

/** Regions that use pounds for body weight. */
const LB_REGIONS = new Set(['US', 'LR', 'MM']);

function primaryLocale() {
  try {
    return getLocales()[0] ?? null;
  } catch {
    return null;
  }
}

export function deviceLanguage(): Language {
  return primaryLocale()?.languageCode === 'de' ? 'de' : 'en';
}

export function deviceWeightUnit(): WeightUnit {
  const locale = primaryLocale();
  if (locale?.measurementSystem) return locale.measurementSystem === 'us' ? 'lb' : 'kg';
  // measurementSystem is null on web; fall back to the region.
  return locale?.regionCode && LB_REGIONS.has(locale.regionCode) ? 'lb' : 'kg';
}
