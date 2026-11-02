import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { IconButton, Screen, Text } from '@/components/ui';
import { formatWeight, latestWeight } from '@/domain/profile';
import { useI18n } from '@/i18n';
import { useAppStore } from '@/store/AppStore';
import { space } from '@/theme';

export default function Home() {
  const { t, language, format, formatDate } = useI18n();
  const name = useAppStore((s) => s.profile.name);
  const unit = useAppStore((s) => s.profile.unit);
  const weights = useAppStore((s) => s.weights);
  const latest = latestWeight(weights);

  return (
    <Screen
      header={
        <View style={styles.bar}>
          <Text variant="label" tone="muted">
            {format(t.home.greeting, { name })}
          </Text>
          <IconButton icon="settings" label={t.settings.title} onPress={() => router.push('/settings')} />
        </View>
      }
    >
      <View style={styles.hero}>
        {latest ? (
          <>
            <Text variant="hero" accessibilityRole="header">
              {formatWeight(latest.kg, unit, language)}
            </Text>
            <Text variant="caption" tone="muted" style={styles.caption}>
              {format(t.home.asOf, { date: formatDate(latest.date) })}
            </Text>
          </>
        ) : (
          <Text variant="title" tone="muted">
            {t.home.noWeight}
          </Text>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  bar: {
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  hero: { marginTop: space.xxl },
  caption: { marginTop: space.s },
});
