export type NameCheck = 'MATCH' | 'CLOSE_MATCH' | 'NO_MATCH' | 'NOT_AVAILABLE';

export interface Transaction {
  id: string;
  fromIban: string;
  toIban: string;
  fromName: string | null;
  toName: string | null;
  beneficiaryName: string | null;
  nameCheck: NameCheck | null;
  amount: number;
  currency: string;
  reference: string | null;
  status: string;
  fromBalanceAfter: number | null;
  toBalanceAfter: number | null;
  createdAt: string;
  /** Which half of a transfer between the user's own accounts this row is. */
  entry?: 'DEBIT' | 'CREDIT' | null;
}
