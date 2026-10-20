import { describe, expect, it } from 'vitest';
import { startOfLocalDay, startOfNextLocalDay } from './date-range';

describe('date range', () => {
  it('starts a day at local midnight, whatever the time zone', () => {
    const start = new Date(startOfLocalDay('2026-03-11'));

    expect([start.getFullYear(), start.getMonth(), start.getDate()]).toEqual([2026, 2, 11]);
    expect([start.getHours(), start.getMinutes(), start.getSeconds()]).toEqual([0, 0, 0]);
  });

  it('ends a day at the next local midnight, including across a month boundary', () => {
    const end = new Date(startOfNextLocalDay('2026-03-31'));

    expect([end.getFullYear(), end.getMonth(), end.getDate(), end.getHours()]).toEqual([2026, 3, 1, 0]);
  });

  it('produces an ISO instant the API can parse', () => {
    expect(startOfLocalDay('2026-03-11')).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
  });
});
