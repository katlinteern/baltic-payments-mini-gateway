import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Transaction } from '@shared/models/transaction.model';
import { PayeeVerification } from './payee-verification.model';
import { PaymentRequest } from './payment-request.model';

@Injectable({ providedIn: 'root' })
export class PaymentsApiService {
  private readonly http = inject(HttpClient);

  verifyPayee(toIban: string, beneficiaryName: string) {
    return this.http.post<PayeeVerification>('/api/payments/verify-payee', { toIban, beneficiaryName });
  }

  submit(request: PaymentRequest, idempotencyKey: string) {
    return this.http.post<Transaction>('/api/payments', request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }
}
