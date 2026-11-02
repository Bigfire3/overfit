import {
  ageFromBirthYear,
  birthYearFromAge,
  formatWeight,
  latestWeight,
  parseAge,
  parseDecimal,
  parseWeightToKg,
  upsertWeight,
} from '../profile';

describe('parseDecimal', () => {
  it('accepts comma and dot', () => {
    expect(parseDecimal('82,4')).toBe(82.4);
    expect(parseDecimal('82.4')).toBe(82.4);
    expect(parseDecimal(' 82 ')).toBe(82);
  });

  it('rejects garbage', () => {
    expect(parseDecimal('')).toBeNull();
    expect(parseDecimal('abc')).toBeNull();
    expect(parseDecimal('-3')).toBeNull();
    expect(parseDecimal('1,2,3')).toBeNull();
  });
});

describe('parseAge', () => {
  it('accepts plausible ages', () => {
    expect(parseAge('31')).toBe(31);
  });

  it('rejects out-of-range values', () => {
    expect(parseAge('5')).toBeNull();
    expect(parseAge('200')).toBeNull();
    expect(parseAge('3.5')).toBeNull();
  });
});

describe('parseWeightToKg', () => {
  it('keeps kg and converts lb', () => {
    expect(parseWeightToKg('82,4', 'kg')).toBe(82.4);
    expect(parseWeightToKg('180', 'lb')).toBe(81.6);
  });

  it('rejects implausible weights', () => {
    expect(parseWeightToKg('10', 'kg')).toBeNull();
    expect(parseWeightToKg('900', 'kg')).toBeNull();
  });
});

describe('age <-> birth year', () => {
  const today = new Date(2026, 10, 2);

  it('round-trips', () => {
    expect(birthYearFromAge(31, today)).toBe(1995);
    expect(ageFromBirthYear(1995, today)).toBe(31);
  });

  it('stays current a year later', () => {
    expect(ageFromBirthYear(1995, new Date(2027, 10, 2))).toBe(32);
  });
});

describe('weights', () => {
  it('replaces an entry on the same day and keeps order', () => {
    const entries = upsertWeight(
      [
        { date: '2026-11-01', kg: 83 },
        { date: '2026-11-02', kg: 82.8 },
      ],
      { date: '2026-11-02', kg: 82.4 },
    );
    expect(entries).toEqual([
      { date: '2026-11-01', kg: 83 },
      { date: '2026-11-02', kg: 82.4 },
    ]);
    expect(latestWeight(entries)).toEqual({ date: '2026-11-02', kg: 82.4 });
  });

  it('formats per language and unit', () => {
    expect(formatWeight(82.4, 'kg', 'de')).toBe('82,4 kg');
    expect(formatWeight(82.4, 'kg', 'en')).toBe('82.4 kg');
    expect(formatWeight(81.6, 'lb', 'en')).toBe('179.9 lb');
  });
});
