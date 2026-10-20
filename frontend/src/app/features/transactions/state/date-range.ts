function localDay(date: string, offsetDays: number): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day + offsetDays).toISOString();
}

export function startOfLocalDay(date: string): string {
  return localDay(date, 0);
}

export function startOfNextLocalDay(date: string): string {
  return localDay(date, 1);
}
