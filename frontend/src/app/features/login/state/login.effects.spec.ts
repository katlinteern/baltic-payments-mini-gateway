import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '@core/auth.service';
import { login, loginFailed, loginSucceeded } from './login.actions';
import { LoginEffects, loginErrorMessage } from './login.effects';

function setup(actions: Observable<unknown>, authResult: Observable<unknown>) {
  const navigate = vi.fn().mockResolvedValue(true);
  TestBed.configureTestingModule({
    providers: [
      LoginEffects,
      provideMockActions(() => actions as Observable<never>),
      { provide: AuthService, useValue: { login: () => authResult } },
      { provide: Router, useValue: { navigate } },
    ],
  });
  return { effects: TestBed.inject(LoginEffects), navigate };
}

describe('LoginEffects', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('emits loginSucceeded when authentication succeeds', () => {
    const { effects } = setup(of(login({ username: 'alice', password: 'x' })), of({}));
    const emitted: unknown[] = [];

    effects.login$.subscribe((action) => emitted.push(action));

    expect(emitted).toEqual([loginSucceeded()]);
  });

  it('emits a generic failure that does not leak whether the user exists', () => {
    const { effects } = setup(
      of(login({ username: 'alice', password: 'x' })),
      throwError(() => ({ status: 401 })),
    );
    const emitted: unknown[] = [];

    effects.login$.subscribe((action) => emitted.push(action));

    expect(emitted).toEqual([loginFailed({ error: 'Login failed. Check your credentials and try again.' })]);
  });

  it('navigates to the accounts page after a successful login', () => {
    const { effects, navigate } = setup(of(loginSucceeded()), of({}));

    effects.navigateAfterLogin$.subscribe();

    expect(navigate).toHaveBeenCalledWith(['/accounts']);
  });

  it('reports the wait time instead of blaming the credentials when sign-in is rate limited', () => {
    const rateLimited = new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '901' }) });
    const { effects } = setup(
      of(login({ username: 'alice', password: 'x' })),
      throwError(() => rateLimited),
    );
    const emitted: unknown[] = [];

    effects.login$.subscribe((action) => emitted.push(action));

    expect(emitted).toEqual([loginFailed({ error: 'Too many failed attempts. Try again in 16 minutes.' })]);
  });
});

describe('loginErrorMessage', () => {
  const httpError = (status: number, headers?: Record<string, string>) =>
    new HttpErrorResponse({ status, headers: new HttpHeaders(headers) });

  it('uses the singular for one minute and a generic text without Retry-After', () => {
    expect(loginErrorMessage(httpError(429, { 'Retry-After': '60' }))).toBe(
      'Too many failed attempts. Try again in 1 minute.',
    );
    expect(loginErrorMessage(httpError(429))).toBe('Too many failed attempts. Try again later.');
  });

  it('reports an unreachable or failing server separately from bad credentials', () => {
    expect(loginErrorMessage(httpError(0))).toBe('Sign-in is temporarily unavailable. Try again shortly.');
    expect(loginErrorMessage(httpError(503))).toBe('Sign-in is temporarily unavailable. Try again shortly.');
  });
});
