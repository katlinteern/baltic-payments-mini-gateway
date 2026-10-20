import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Payment, PaymentRequest } from './payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentsApiService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/payments';

  createPayment(request: PaymentRequest) {
    return this.http.post<Payment>(this.url, request);
  }

  startConfirmation(id: string, acknowledgeNameMismatch: boolean) {
    return this.http.post<Payment>(`${this.url}/${id}/confirmation`, { acknowledgeNameMismatch });
  }

  getPayment(id: string) {
    return this.http.get<Payment>(`${this.url}/${id}`);
  }

  cancelPayment(id: string) {
    return this.http.delete<Payment>(`${this.url}/${id}`);
  }
}
