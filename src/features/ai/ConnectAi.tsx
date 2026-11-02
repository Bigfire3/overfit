import * as Haptics from 'expo-haptics';
import * as Linking from 'expo-linking';
import { useState, type ReactNode } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import { getProvider, PROVIDERS } from '@/ai/providers';
import type { KeyValidationResult, ProviderId } from '@/ai/types';
import { Icon, PrimaryButton, Row, Screen, Text, TextButton } from '@/components/ui';
import { useI18n } from '@/i18n';
import { actions, useAppStore } from '@/store/AppStore';
import { space, type, useColors } from '@/theme';

type Status = 'idle' | 'checking' | KeyValidationResult;

interface ConnectAiProps {
  header: ReactNode;
  onConnected: () => void;
  /** Optional "set up later" action (onboarding only). */
  onSkip?: () => void;
}

export function ConnectAi({ header, onConnected, onSkip }: ConnectAiProps) {
  const { t } = useI18n();
  const c = useColors();
  const storedProvider = useAppStore((s) => s.ai.providerId);
  const [providerId, setProviderId] = useState<ProviderId>(storedProvider);
  const [apiKey, setApiKey] = useState('');
  const [status, setStatus] = useState<Status>('idle');
  const provider = getProvider(providerId);
  const trimmed = apiKey.trim();

  const connect = async () => {
    if (!trimmed) return;
    setStatus('checking');
    const result = await provider.validateKey(trimmed);
    setStatus(result);
    if (result !== 'ok') {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => undefined);
      return;
    }
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => undefined);
    await actions.connectAi(providerId, trimmed);
    onConnected();
  };

  const message =
    status === 'invalid' ? t.onboarding.aiInvalid : status === 'network' ? t.onboarding.aiNetwork : null;

  return (
    <Screen
      scroll
      header={header}
      footer={
        <>
          <PrimaryButton
            label={status === 'ok' ? t.onboarding.aiConnected : t.onboarding.aiConnect}
            onPress={connect}
            disabled={!trimmed}
            loading={status === 'checking'}
          />
          {onSkip ? <TextButton label={t.onboarding.aiLater} onPress={onSkip} /> : null}
        </>
      }
    >
      <Text variant="title">{t.onboarding.aiTitle}</Text>
      <Text tone="muted" style={styles.subtitle}>
        {t.onboarding.aiSubtitle}
      </Text>

      <View style={[styles.providers, { borderTopColor: c.hairline }]}>
        {PROVIDERS.map((p) => (
          <Row
            key={p.id}
            label={p.label}
            tone={p.id === providerId ? 'default' : 'muted'}
            value={p.id === providerId ? <Icon name="check" size={20} /> : undefined}
            onPress={() => {
              setProviderId(p.id);
              setStatus('idle');
            }}
          />
        ))}
      </View>

      <TextInput
        value={apiKey}
        onChangeText={(v) => {
          setApiKey(v);
          if (status !== 'checking') setStatus('idle');
        }}
        placeholder={`${t.onboarding.aiKeyPlaceholder} (${provider.keyPlaceholder})`}
        placeholderTextColor={c.faint}
        selectionColor={c.foreground}
        autoCapitalize="none"
        autoCorrect={false}
        secureTextEntry
        returnKeyType="done"
        onSubmitEditing={connect}
        style={[type.body, styles.keyInput, { color: c.foreground, borderBottomColor: c.hairline }]}
      />
      {message ? (
        <Text variant="caption" style={styles.message}>
          {message}
        </Text>
      ) : null}

      <View style={styles.howTo}>
        <TextButton
          label={t.onboarding.aiHowTo}
          tone="default"
          align="flex-start"
          onPress={() => Linking.openURL(provider.keyUrl)}
        />
      </View>

      <Text variant="caption" tone="muted" style={styles.privacy}>
        {t.onboarding.aiPrivacy}
      </Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: { marginTop: space.m },
  providers: { marginTop: space.xl, borderTopWidth: StyleSheet.hairlineWidth },
  keyInput: {
    marginTop: space.xl,
    paddingVertical: space.m,
    borderBottomWidth: StyleSheet.hairlineWidth,
    outlineStyle: 'none',
  } as object,
  message: { marginTop: space.m },
  howTo: { marginTop: space.l, alignItems: 'flex-start' },
  privacy: { marginTop: space.l },
});
