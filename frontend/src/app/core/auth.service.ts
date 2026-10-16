import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { LoginResponse } from '../shared/models/login-response.model';

interface Session {
  token: string;
  username: string;
  role: string;
  expiresAt: string;
}

const STORAGE_KEY = 'session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(this.restore());
  readonly token = computed(() => this.session()?.token ?? null);
  readonly username = computed(() => this.session()?.username ?? '');
  readonly role = computed(() => this.session()?.role ?? '');
  readonly isLoggedIn = computed(() => this.session() !== null);

  login(username: string, password: string) {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password }).pipe(
      tap((result) => {
        const session: Session = {
          token: result.token,
          username: result.username,
          role: result.role,
          expiresAt: result.expiresAt,
        };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.session.set(session);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }

  /** Drops a persisted session whose token has already expired so the user is not sent to the API with it. */
  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const session = JSON.parse(raw) as Session;
      if (!session.expiresAt || Date.parse(session.expiresAt) <= Date.now()) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      return null;
    }
  }
}
