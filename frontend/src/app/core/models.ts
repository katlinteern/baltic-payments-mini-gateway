export interface Account { id: string; iban: string; currency: string; balance: number; }
export interface Transaction { id: string; fromIban: string; toIban: string; amount: number; currency: string; reference: string | null; status: string; createdAt: string; }
export interface PageResponse<T> { content: T[]; totalElements: number; page: number; size: number; }
export interface LoginResponse { token: string; username: string; role: string; expiresAt: string; }
export interface PaymentRequest { fromIban: string; toIban: string; amount: number; currency: string; reference: string; }
