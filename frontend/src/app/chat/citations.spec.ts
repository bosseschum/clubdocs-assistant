import { describe, expect, it } from 'vitest';

import { citedNumbers } from './citations';

describe('citedNumbers', () => {
  it('finds a single citation', () => {
    expect(citedNumbers('Der Beitrag beträgt 48 Euro [1].')).toEqual([1]);
  });

  it('finds several citations and returns them sorted and without duplicates', () => {
    expect(citedNumbers('Zehn Mitglieder [2]. Mehrheit [1]. Nochmal [2].')).toEqual([1, 2]);
  });

  it('returns an empty list when nothing is cited', () => {
    expect(citedNumbers('Dazu habe ich in den Dokumenten keine Information gefunden.')).toEqual([]);
  });

  it('finds citations with more than one digit', () => {
    expect(citedNumbers('Siehe [10] und [3].')).toEqual([3, 10]);
  });

  it('ignores brackets that are not citations', () => {
    expect(citedNumbers('Absatz [a] und [] und [1.5] und (2)')).toEqual([]);
  });

  it('ignores citations with more than one digit', () => {
    expect(citedNumbers('Siehe [1, 2]')).toEqual([]);
  });

  it('ignores citations with zero', () => {
    expect(citedNumbers('Siehe [0]')).toEqual([]);
  });

  it('ignores citations with non-integer values', () => {
    expect(citedNumbers('Siehe [1.5]')).toEqual([]);
  });
});
