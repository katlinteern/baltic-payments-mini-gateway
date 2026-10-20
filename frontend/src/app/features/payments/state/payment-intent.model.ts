import { NameCheck } from '@shared/models/transaction.model';

/** What the payer types into the payment form. */
export interface PaymentRequest {
  fromIban: string;
  toIban: string;
  beneficiaryName: string;
  /** At most two decimals and 12 integer digits, which a JSON number carries without losing a digit. */
  amount: number;
  currency: string;
  reference: string;
}

export type IntentStatus = 'READY' | 'AWAITING_AUTHENTICATION' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'EXPIRED';

/** A prepared payment: reviewed by the payer, then authorised, and only then executed by the server. */
export interface PaymentIntent {
  id: string;
  status: IntentStatus;
  fromIban: string;
  toIban: string;
  beneficiaryName: string;
  amount: number;
  currency: string;
  reference: string;
  nameCheck: NameCheck;
  /** Present only for a close match, so the payer can check it is the intended person. */
  registeredName: string | null;
  /** The code to compare with the authentication app; present only while it is being asked. */
  verificationCode: string | null;
  expiresAt: string;
  transactionId: string | null;
  completedAt: string | null;
  /** The payer's balance after the payment; present once it is executed. */
  balanceAfter: number | null;
  failureCode: string | null;
  failureMessage: string | null;
}
