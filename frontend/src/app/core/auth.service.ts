import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { LoginResponse } from '../shared/models/login-response.model';

interface Session { token: string; username: string; role: string; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(this.restore());
  readonly token = computed(() => this.session()?.token ?? null);
  readonly username = computed(() => this.session()?.username ?? '');
  readonly role = computed(() => this.session()?.role ?? '');
  readonly isLoggedIn = computed(() => this.session() !== null);

  login(username: string, password: string) {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password }).pipe(tap(result => {
      const session = { token: result.token, username: result.username, role: result.role };
      localStorage.setItem('session', JSON.stringify(session));
      this.session.set(session);
    }));
  }
  logout(): void {
    localStorage.removeItem('session');
    this.session.set(null);
  }
  private restore(): Session | null {
    try {
      const raw = localStorage.getItem('session');
      return raw ? JSON.parse(raw) as Session : null;
    } catch {
      return null;
    }
  }
}
