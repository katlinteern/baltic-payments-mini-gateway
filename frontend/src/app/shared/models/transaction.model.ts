export type NameCheck = 'MATCH' | 'CLOSE_MATCH' | 'NO_MATCH' | 'NOT_AVAILABLE';

export interface Transaction {
  id: string;
  fromIban: string;
  toIban: string;
  beneficiaryName: string | null;
  nameCheck: NameCheck | null;
  amount: number;
  currency: string;
  reference: string | null;
  status: string;
  createdAt: string;
}
