export interface Transaction {
  id: string;
  fromIban: string;
  toIban: string;
  beneficiaryName: string | null;
  amount: number;
  currency: string;
  reference: string | null;
  status: string;
  createdAt: string;
}