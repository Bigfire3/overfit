import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet } from 'react-native';

import { BigInput, PrimaryButton, Screen, Text } from '@/components/ui';
import { ageFromBirthYear, birthYearFromAge, parseAge } from '@/domain/profile';
import { OnboardingHeader } from '@/features/onboarding/OnboardingHeader';
import { useI18n } from '@/i18n';
import { actions, useAppStore } from '@/store/AppStore';
import { space } from '@/theme';

export default function AgeStep() {
  const { t } = useI18n();
  const birthYear = useAppStore((s) => s.profile.birthYear);
  const [input, setInput] = useState(
    birthYear !== null ? String(ageFromBirthYear(birthYear, new Date())) : '',
  );
  const age = parseAge(input);
  const showError = input.length >= 2 && age === null;

  const next = () => {
    if (age === null) return;
    actions.setBirthYear(birthYearFromAge(age, new Date()));
    router.push('/onboarding/weight');
  };

  return (
    <Screen
      header={<OnboardingHeader step={2} />}
      footer={<PrimaryButton label={t.common.next} onPress={next} disabled={age === null} />}
    >
      <Text variant="title" style={styles.title}>
        {t.onboarding.ageTitle}
      </Text>
      <BigInput
        value={input}
        onChangeText={(v) => setInput(v.replace(/\D/g, ''))}
        placeholder="30"
        suffix={t.onboarding.ageUnit}
        keyboardType="number-pad"
        autoFocus
        maxLength={3}
        returnKeyType="next"
        onSubmitEditing={next}
      />
      {showError ? (
        <Text variant="caption" tone="muted" style={styles.hint}>
          {t.onboarding.ageError}
        </Text>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { marginBottom: space.xl },
  hint: { marginTop: space.m },
});
