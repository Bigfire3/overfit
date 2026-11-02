import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { BigInput, PrimaryButton, Screen, Segmented, Text } from '@/components/ui';
import { displayWeight, latestWeight, parseWeightToKg, type WeightUnit } from '@/domain/profile';
import { OnboardingHeader } from '@/features/onboarding/OnboardingHeader';
import { useI18n } from '@/i18n';
import { actions, useAppStore } from '@/store/AppStore';
import { space } from '@/theme';

export default function WeightStep() {
  const { t, language } = useI18n();
  const unit = useAppStore((s) => s.profile.unit);
  const weights = useAppStore((s) => s.weights);
  const [input, setInput] = useState(() => {
    const latest = latestWeight(weights);
    if (!latest) return '';
    const value = String(displayWeight(latest.kg, unit));
    return language === 'de' ? value.replace('.', ',') : value;
  });
  const kg = parseWeightToKg(input, unit);
  const showError = input.length >= 2 && kg === null;

  const changeUnit = (next: WeightUnit) => {
    // Convert what was typed so switching units doesn't change the meaning.
    if (kg !== null) {
      const value = String(displayWeight(kg, next));
      setInput(language === 'de' ? value.replace('.', ',') : value);
    }
    actions.setUnit(next);
  };

  const next = () => {
    if (kg === null) return;
    actions.logWeight(kg);
    router.push('/onboarding/ai');
  };

  return (
    <Screen
      header={<OnboardingHeader step={3} />}
      footer={<PrimaryButton label={t.common.next} onPress={next} disabled={kg === null} />}
    >
      <Text variant="title" style={styles.title}>
        {t.onboarding.weightTitle}
      </Text>
      <BigInput
        value={input}
        onChangeText={(v) => setInput(v.replace(/[^\d.,]/g, ''))}
        placeholder={unit === 'kg' ? '75' : '165'}
        suffix={unit}
        keyboardType="decimal-pad"
        autoFocus
        maxLength={5}
        returnKeyType="next"
        onSubmitEditing={next}
      />
      <View style={styles.unit}>
        <Segmented
          value={unit}
          onChange={changeUnit}
          options={[
            { value: 'kg', label: 'kg' },
            { value: 'lb', label: 'lb' },
          ]}
        />
      </View>
      {showError ? (
        <Text variant="caption" tone="muted" style={styles.hint}>
          {t.onboarding.weightError}
        </Text>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { marginBottom: space.xl },
  unit: { marginTop: space.l },
  hint: { marginTop: space.m },
});
