import { Page } from '@playwright/test';

export async function readBalance(page: Page, iban: string): Promise<number> {
  const row = page.getByRole('row').filter({ hasText: iban });
  const text = await row.innerText();
  const match = text.slice(text.indexOf(iban) + iban.length).match(/(-?\d[\d,\s]*\.\d{2})\s*EUR/);
  if (!match) throw new Error(`No balance found for ${iban} in: ${text}`);
  return Number(match[1].replace(/[,\s]/g, ''));
}

export function formatEur(amount: number): string {
  return `${amount.toFixed(2)} EUR`;
}
