import { useCallback } from 'react';

import type { Language } from '@/domain/profile';
import { useAppStore } from '@/store/AppStore';

import { de, type Messages } from './de';
import { en } from './en';

const MESSAGES: Record<Language, Messages> = { de, en };

export function messagesFor(language: Language): Messages {
  return MESSAGES[language];
}

/** Replaces `{name}` placeholders. */
export function format(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) => values[key] ?? match);
}

export function useI18n() {
  const language = useAppStore((s) => s.settings.language);
  const t = MESSAGES[language];
  const formatDate = useCallback(
    (iso: string) =>
      new Intl.DateTimeFormat(language === 'de' ? 'de-DE' : 'en-US', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      }).format(new Date(`${iso}T12:00:00`)),
    [language],
  );
  return { t, language, format, formatDate };
}
