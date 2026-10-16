import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '@core/auth.service';
import { login, loginFailed, loginSucceeded } from './login.actions';
import { LoginEffects } from './login.effects';

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
});
