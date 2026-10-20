import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { PaymentIntent, PaymentRequest } from './payment-intent.model';

@Injectable({ providedIn: 'root' })
export class PaymentsApiService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/payments/intents';

  createIntent(request: PaymentRequest) {
    return this.http.post<PaymentIntent>(this.url, request);
  }

  startConfirmation(id: string, acknowledgeNameMismatch: boolean) {
    return this.http.post<PaymentIntent>(`${this.url}/${id}/confirmation`, { acknowledgeNameMismatch });
  }

  getIntent(id: string) {
    return this.http.get<PaymentIntent>(`${this.url}/${id}`);
  }

  cancelIntent(id: string) {
    return this.http.delete<PaymentIntent>(`${this.url}/${id}`);
  }
}
