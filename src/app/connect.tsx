import { router } from 'expo-router';
import { View } from 'react-native';

import { IconButton } from '@/components/ui';
import { ConnectAi } from '@/features/ai/ConnectAi';
import { useI18n } from '@/i18n';
import { space } from '@/theme';

/** Connect or change the AI key from the settings. */
export default function ConnectScreen() {
  const { t } = useI18n();
  return (
    <ConnectAi
      header={
        <View style={{ height: 40, justifyContent: 'center', marginBottom: space.l }}>
          <IconButton icon="back" label={t.common.back} onPress={() => router.back()} />
        </View>
      }
      onConnected={() => router.back()}
    />
  );
}
