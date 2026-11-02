import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { IconButton, Progress } from '@/components/ui';
import { useI18n } from '@/i18n';
import { space } from '@/theme';

export const ONBOARDING_STEPS = 4;

export function OnboardingHeader({ step }: { step: number }) {
  const { t } = useI18n();
  return (
    <View>
      <View style={styles.bar}>
        {step > 1 ? (
          <IconButton
            icon="back"
            label={t.common.back}
            onPress={() => (router.canGoBack() ? router.back() : router.replace('/onboarding/name'))}
          />
        ) : null}
      </View>
      <Progress step={step} total={ONBOARDING_STEPS} />
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { height: 40, justifyContent: 'center', marginBottom: space.m },
});
