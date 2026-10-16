import { NameCheck } from '@shared/models/transaction.model';

/** Outcome of Verification of Payee; the registered name is present only for a close match. */
export interface PayeeVerification {
  result: NameCheck;
  registeredName: string | null;
}
