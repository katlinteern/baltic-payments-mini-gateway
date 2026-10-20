import { NameCheck } from '@shared/models/transaction.model';

export interface PaymentRequest {
  fromIban: string;
  toIban: string;
  beneficiaryName: string;
  amount: number;
  currency: string;
  reference: string;
}

export type PaymentStatus = 'READY' | 'AWAITING_AUTHENTICATION' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'EXPIRED';

export interface Payment {
  id: string;
  status: PaymentStatus;
  fromIban: string;
  toIban: string;
  beneficiaryName: string;
  amount: number;
  currency: string;
  reference: string;
  nameCheck: NameCheck;
  registeredName: string | null;
  verificationCode: string | null;
  expiresAt: string;
  transactionId: string | null;
  completedAt: string | null;
  balanceAfter: number | null;
  failureCode: string | null;
  failureMessage: string | null;
}
