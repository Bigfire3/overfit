import { Redirect } from 'expo-router';

import { useAppStore } from '@/store/AppStore';

export default function Index() {
  const onboarded = useAppStore((s) => s.onboardedAt !== null);
  return <Redirect href={onboarded ? '/home' : '/onboarding/name'} />;
}
