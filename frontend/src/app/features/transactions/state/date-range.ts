function localDay(date: string, offsetDays: number): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day + offsetDays).toISOString();
}

/** First instant of a calendar day (yyyy-MM-dd) in the user's time zone; the API's `from` is inclusive. */
export function startOfLocalDay(date: string): string {
  return localDay(date, 0);
}

/** First instant of the following day in the user's time zone; the API's `to` is exclusive. */
export function startOfNextLocalDay(date: string): string {
  return localDay(date, 1);
}
