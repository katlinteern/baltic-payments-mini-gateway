export interface PaymentRequest {
  fromIban: string;
  toIban: string;
  amount: number;
  currency: string;
  reference: string;
}