import { buildProfileBlock } from '../modelContext';

describe('buildProfileBlock', () => {
  const today = new Date(2026, 10, 2);

  it('renders the full block', () => {
    const block = buildProfileBlock({
      profile: { name: 'Fabi', birthYear: 1995, unit: 'kg' },
      weights: [
        { date: '2026-10-01', kg: 84 },
        { date: '2026-11-02', kg: 82 },
      ],
      language: 'de',
      today,
    });

    expect(block).toBe(
      'User: Fabi · Age: 31 · Weight: 82 kg (as of 2026-11-02)\n' +
        'Reply language: German · Units: metric · Today: 2026-11-02',
    );
  });

  it('uses the display unit and skips missing fields', () => {
    const block = buildProfileBlock({
      profile: { name: 'Sam', birthYear: null, unit: 'lb' },
      weights: [{ date: '2026-11-02', kg: 81.6 }],
      language: 'en',
      today,
    });

    expect(block).toBe(
      'User: Sam · Weight: 179.9 lb (as of 2026-11-02)\n' +
        'Reply language: English · Units: imperial · Today: 2026-11-02',
    );
  });
});
