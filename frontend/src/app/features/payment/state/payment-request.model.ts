export interface PaymentRequest {
  fromIban: string;
  toIban: string;
  beneficiaryName: string;
  amount: number;
  currency: string;
  reference: string;
  /** Set only after the payer saw a failed payee verification and chose to send anyway. */
  proceedDespiteNameMismatch?: boolean;
}
