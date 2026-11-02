export type Language = 'de' | 'en';
export type WeightUnit = 'kg' | 'lb';
export type ThemeMode = 'system' | 'dark' | 'light';

export interface Profile {
  /** How the user wants to be called. */
  name: string;
  /** Stored instead of an age so it stays correct over time. */
  birthYear: number | null;
  unit: WeightUnit;
}

/** A single weight measurement. Always stored in kg. */
export interface WeightEntry {
  /** ISO date, e.g. "2026-11-02". */
  date: string;
  kg: number;
}

export const AGE_MIN = 13;
export const AGE_MAX = 110;
export const WEIGHT_KG_MIN = 25;
export const WEIGHT_KG_MAX = 350;

const LB_PER_KG = 2.20462262;

export function kgToLb(kg: number): number {
  return kg * LB_PER_KG;
}

export function lbToKg(lb: number): number {
  return lb / LB_PER_KG;
}

export function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

/** Parses user input like "82,4" or "82.4". Returns null for anything else. */
export function parseDecimal(input: string): number | null {
  const normalized = input.trim().replace(',', '.');
  if (!/^\d+(\.\d+)?$/.test(normalized)) return null;
  return Number(normalized);
}

export function parseAge(input: string): number | null {
  const trimmed = input.trim();
  if (!/^\d{1,3}$/.test(trimmed)) return null;
  const age = Number(trimmed);
  return age >= AGE_MIN && age <= AGE_MAX ? age : null;
}

/** Converts a weight input in the given unit to kg, or null if invalid/out of range. */
export function parseWeightToKg(input: string, unit: WeightUnit): number | null {
  const value = parseDecimal(input);
  if (value === null) return null;
  const kg = unit === 'kg' ? value : lbToKg(value);
  if (kg < WEIGHT_KG_MIN || kg > WEIGHT_KG_MAX) return null;
  return round1(kg);
}

export function birthYearFromAge(age: number, today: Date): number {
  return today.getFullYear() - age;
}

/** Approximate age from the birth year (may be off by one before the birthday). */
export function ageFromBirthYear(birthYear: number, today: Date): number {
  return today.getFullYear() - birthYear;
}

export function displayWeight(kg: number, unit: WeightUnit): number {
  return round1(unit === 'kg' ? kg : kgToLb(kg));
}

export function formatWeight(kg: number, unit: WeightUnit, language: Language): string {
  const value = displayWeight(kg, unit);
  const formatted = new Intl.NumberFormat(language === 'de' ? 'de-DE' : 'en-US', {
    maximumFractionDigits: 1,
  }).format(value);
  return `${formatted} ${unit}`;
}

export function isoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function latestWeight(entries: WeightEntry[]): WeightEntry | null {
  if (entries.length === 0) return null;
  return entries.reduce((latest, entry) => (entry.date >= latest.date ? entry : latest));
}

/** Adds a weight entry; a second entry on the same day replaces the first. */
export function upsertWeight(entries: WeightEntry[], entry: WeightEntry): WeightEntry[] {
  return [...entries.filter((e) => e.date !== entry.date), entry].sort((a, b) =>
    a.date.localeCompare(b.date),
  );
}
