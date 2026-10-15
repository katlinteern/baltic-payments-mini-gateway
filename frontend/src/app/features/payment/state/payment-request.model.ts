export interface PaymentRequest {
  fromIban: string;
  toIban: string;
  beneficiaryName: string;
  amount: number;
  currency: string;
  reference: string;
}