/**
 * Finds the source numbers an answer cites, like [1] or [3].
 * Returns each number once, sorted ascending. The contract is defined by citations.spec.ts.
 */
export function citedNumbers(text: string): number[] {
  const numbers = new Set<number>();
  for (const match of text.matchAll(/\[(\d+)\]/g)) {
    const n = Number(match[1]);
    if (n > 0) {
      numbers.add(n);
    }
  }
  return [...numbers].sort((a, b) => a - b);
}
