import { router } from 'expo-router';

import { ConnectAi } from '@/features/ai/ConnectAi';
import { OnboardingHeader } from '@/features/onboarding/OnboardingHeader';
import { actions } from '@/store/AppStore';

export default function AiStep() {
  const finish = () => {
    actions.completeOnboarding();
    router.replace('/home');
  };

  return <ConnectAi header={<OnboardingHeader step={4} />} onConnected={finish} onSkip={finish} />;
}
