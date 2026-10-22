import { describe, expect, it } from 'vitest';

import { formatSize } from './format-size';

describe('formatSize', () => {
  it('shows small sizes in bytes', () => {
    expect(formatSize(512)).toBe('512 B');
  });

  it('shows kilobytes with one decimal', () => {
    expect(formatSize(1536)).toBe('1.5 KB');
  });

  it('shows megabytes with one decimal', () => {
    expect(formatSize(5 * 1024 * 1024)).toBe('5.0 MB');
  });

  it('shows 0 bytes', () => {
    expect(formatSize(0)).toBe('0 B');
  });

  it('shows exactly 1024 bytes as 1.0 KB', () => {
    expect(formatSize(1024)).toBe('1.0 KB');
  });

  it('shows exactly 1024 * 1024 bytes as 1.0 MB', () => {
    expect(formatSize(1024 * 1024)).toBe('1.0 MB');
  });
});
