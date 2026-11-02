import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { getProvider } from '@/ai/providers';
import { IconButton, Row, RowInput, Screen, SectionTitle, Segmented, Text } from '@/components/ui';
import { ageFromBirthYear, birthYearFromAge, parseAge } from '@/domain/profile';
import { useI18n } from '@/i18n';
import { actions, useAppStore } from '@/store/AppStore';
import { space } from '@/theme';

export default function Settings() {
  const { t } = useI18n();
  const language = useAppStore((s) => s.settings.language);
  const theme = useAppStore((s) => s.settings.theme);
  const profile = useAppStore((s) => s.profile);
  const providerId = useAppStore((s) => s.ai.providerId);
  const hasApiKey = useAppStore((s) => s.hasApiKey);

  const [name, setName] = useState(profile.name);
  const [age, setAge] = useState(
    profile.birthYear !== null ? String(ageFromBirthYear(profile.birthYear, new Date())) : '',
  );
  const [confirmReset, setConfirmReset] = useState(false);

  useEffect(() => {
    if (!confirmReset) return;
    const timer = setTimeout(() => setConfirmReset(false), 4000);
    return () => clearTimeout(timer);
  }, [confirmReset]);

  const saveName = () => {
    if (name.trim()) actions.setName(name);
    else setName(profile.name);
  };

  const saveAge = () => {
    const parsed = parseAge(age);
    if (parsed !== null) actions.setBirthYear(birthYearFromAge(parsed, new Date()));
    else setAge(profile.birthYear !== null ? String(ageFromBirthYear(profile.birthYear, new Date())) : '');
  };

  const reset = async () => {
    if (!confirmReset) {
      setConfirmReset(true);
      return;
    }
    await actions.resetAll();
    router.replace('/onboarding/name');
  };

  return (
    <Screen
      scroll
      header={
        <View style={styles.bar}>
          <IconButton icon="back" label={t.common.back} onPress={() => router.back()} />
        </View>
      }
    >
      <Text variant="title">{t.settings.title}</Text>

      <SectionTitle>{t.settings.general}</SectionTitle>
      <Row
        label={t.settings.language}
        value={
          <Segmented
            value={language}
            onChange={actions.setLanguage}
            options={[
              { value: 'de', label: 'Deutsch' },
              { value: 'en', label: 'English' },
            ]}
          />
        }
      />
      <Row
        label={t.settings.theme}
        value={
          <Segmented
            value={theme}
            onChange={actions.setTheme}
            options={[
              { value: 'system', label: t.settings.themeSystem },
              { value: 'dark', label: t.settings.themeDark },
              { value: 'light', label: t.settings.themeLight },
            ]}
          />
        }
      />

      <SectionTitle>{t.settings.profile}</SectionTitle>
      <Row
        label={t.settings.name}
        value={
          <RowInput
            value={name}
            onChangeText={setName}
            onBlur={saveName}
            onSubmitEditing={saveName}
            autoCapitalize="words"
            maxLength={40}
          />
        }
      />
      <Row
        label={t.settings.age}
        value={
          <RowInput
            value={age}
            onChangeText={(v) => setAge(v.replace(/\D/g, ''))}
            onBlur={saveAge}
            onSubmitEditing={saveAge}
            keyboardType="number-pad"
            maxLength={3}
          />
        }
      />
      <Row
        label={t.settings.unit}
        value={
          <Segmented
            value={profile.unit}
            onChange={actions.setUnit}
            options={[
              { value: 'kg', label: 'kg' },
              { value: 'lb', label: 'lb' },
            ]}
          />
        }
      />

      <SectionTitle>{t.settings.ai}</SectionTitle>
      <Row
        label={getProvider(providerId).label}
        value={hasApiKey ? t.settings.connected : t.settings.notConnected}
      />
      <Row label={hasApiKey ? t.settings.change : t.settings.connect} onPress={() => router.push('/connect')} />
      {hasApiKey ? <Row label={t.settings.disconnect} tone="muted" onPress={actions.disconnectAi} /> : null}

      <SectionTitle>{t.settings.data}</SectionTitle>
      <Row
        label={confirmReset ? t.settings.resetConfirm : t.settings.reset}
        tone={confirmReset ? 'default' : 'muted'}
        onPress={reset}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  bar: { height: 40, justifyContent: 'center', marginBottom: space.l },
});
