import { useColorScheme } from 'react-native';

import { useAppStore } from '@/store/AppStore';

/**
 * Black & white design system. No accent colour: hierarchy comes from
 * typography, whitespace and a few greys only.
 */
const dark = {
  background: '#000000',
  foreground: '#FFFFFF',
  muted: '#8E8E93',
  faint: '#48484A',
  hairline: '#262628',
  /** Inverted surfaces, e.g. the primary button. */
  inverseBackground: '#FFFFFF',
  inverseForeground: '#000000',
};

const light: typeof dark = {
  background: '#FFFFFF',
  foreground: '#000000',
  muted: '#6E6E73',
  faint: '#C7C7CC',
  hairline: '#E5E5EA',
  inverseBackground: '#000000',
  inverseForeground: '#FFFFFF',
};

export type Colors = typeof dark;
export type Scheme = 'dark' | 'light';

export const fonts = {
  regular: 'Inter_400Regular',
  medium: 'Inter_500Medium',
  semibold: 'Inter_600SemiBold',
} as const;

/** Only a handful of sizes on purpose. */
export const type = {
  hero: { fontFamily: fonts.semibold, fontSize: 56, lineHeight: 62, letterSpacing: -1.5 },
  title: { fontFamily: fonts.semibold, fontSize: 36, lineHeight: 42, letterSpacing: -0.8 },
  body: { fontFamily: fonts.regular, fontSize: 17, lineHeight: 24 },
  label: { fontFamily: fonts.medium, fontSize: 17, lineHeight: 24 },
  caption: { fontFamily: fonts.medium, fontSize: 13, lineHeight: 18 },
} as const;

export const space = {
  xs: 4,
  s: 8,
  m: 16,
  l: 24,
  xl: 40,
  xxl: 64,
} as const;

export const radius = {
  pill: 999,
} as const;

export function useScheme(): Scheme {
  const mode = useAppStore((s) => s.settings.theme);
  const system = useColorScheme();
  if (mode === 'system') return system === 'light' ? 'light' : 'dark';
  return mode;
}

export function useColors(): Colors {
  return useScheme() === 'light' ? light : dark;
}
