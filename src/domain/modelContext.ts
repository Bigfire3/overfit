import {
  ageFromBirthYear,
  displayWeight,
  isoDate,
  latestWeight,
  type Language,
  type Profile,
  type WeightEntry,
} from './profile';

export interface ModelContextInput {
  profile: Profile;
  weights: WeightEntry[];
  language: Language;
  today: Date;
}

const LANGUAGE_NAMES: Record<Language, string> = {
  de: 'German',
  en: 'English',
};

/**
 * The small profile block sent to the model with every request.
 * Regenerated per request so age, latest weight and today's date are always current.
 */
export function buildProfileBlock({ profile, weights, language, today }: ModelContextInput): string {
  const parts: string[] = [];
  if (profile.name) parts.push(`User: ${profile.name}`);
  if (profile.birthYear !== null) parts.push(`Age: ${ageFromBirthYear(profile.birthYear, today)}`);
  const latest = latestWeight(weights);
  if (latest) {
    parts.push(`Weight: ${displayWeight(latest.kg, profile.unit)} ${profile.unit} (as of ${latest.date})`);
  }

  const units = profile.unit === 'kg' ? 'metric' : 'imperial';
  const meta = [`Reply language: ${LANGUAGE_NAMES[language]}`, `Units: ${units}`, `Today: ${isoDate(today)}`];

  return [parts.join(' · '), meta.join(' · ')].filter(Boolean).join('\n');
}
