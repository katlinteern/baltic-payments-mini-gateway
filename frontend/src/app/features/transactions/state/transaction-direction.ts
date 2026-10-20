import { Transaction } from '@shared/models/transaction.model';

/** Money leaving the user's accounts, arriving on them, moving between them, or neither (the user's accounts are not known). */
export type Direction = 'outgoing' | 'incoming' | 'internal' | 'other';

export function directionOf(
  transaction: Pick<Transaction, 'fromIban' | 'toIban'>,
  ownIbans: ReadonlySet<string>,
): Direction {
  const from = ownIbans.has(transaction.fromIban);
  const to = ownIbans.has(transaction.toIban);
  if (from && to) return 'internal';
  if (from) return 'outgoing';
  if (to) return 'incoming';
  return 'other';
}

export const DIRECTION_SIGN: Record<Direction, string> = { outgoing: '−', incoming: '+', internal: '', other: '' };
