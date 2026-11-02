import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet } from 'react-native';

import { BigInput, PrimaryButton, Screen, Text } from '@/components/ui';
import { OnboardingHeader } from '@/features/onboarding/OnboardingHeader';
import { useI18n } from '@/i18n';
import { actions, useAppStore } from '@/store/AppStore';
import { space } from '@/theme';

export default function NameStep() {
  const { t } = useI18n();
  const stored = useAppStore((s) => s.profile.name);
  const [name, setName] = useState(stored);
  const valid = name.trim().length > 0;

  const next = () => {
    if (!valid) return;
    actions.setName(name);
    router.push('/onboarding/age');
  };

  return (
    <Screen
      header={<OnboardingHeader step={1} />}
      footer={<PrimaryButton label={t.common.next} onPress={next} disabled={!valid} />}
    >
      <Text variant="title" style={styles.title}>
        {t.onboarding.nameTitle}
      </Text>
      <BigInput
        value={name}
        onChangeText={setName}
        placeholder={t.onboarding.namePlaceholder}
        autoFocus
        autoCapitalize="words"
        autoComplete="given-name"
        textContentType="givenName"
        returnKeyType="next"
        maxLength={40}
        onSubmitEditing={next}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { marginBottom: space.xl },
});
