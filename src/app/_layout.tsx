import { Inter_400Regular, Inter_500Medium, Inter_600SemiBold, useFonts } from '@expo-google-fonts/inter';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SystemUI from 'expo-system-ui';
import { useEffect } from 'react';
import { View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { hydrate, useAppStore } from '@/store/AppStore';
import { useColors, useScheme } from '@/theme';

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts({ Inter_400Regular, Inter_500Medium, Inter_600SemiBold });
  const hydrated = useAppStore((s) => s.hydrated);
  const scheme = useScheme();
  const c = useColors();

  useEffect(() => {
    hydrate();
  }, []);

  useEffect(() => {
    SystemUI.setBackgroundColorAsync(c.background).catch(() => undefined);
  }, [c.background]);

  const ready = hydrated && (fontsLoaded || fontError);

  return (
    <SafeAreaProvider>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      {ready ? (
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: c.background },
            animation: 'fade',
          }}
        />
      ) : (
        <View style={{ flex: 1, backgroundColor: c.background }} />
      )}
    </SafeAreaProvider>
  );
}
