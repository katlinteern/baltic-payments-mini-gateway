export interface Account {
  id: string;
  iban: string;
  currency: string;
  holderName: string | null;
  balance: number;
}
