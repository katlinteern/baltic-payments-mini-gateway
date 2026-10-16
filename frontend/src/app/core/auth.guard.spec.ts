import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

function runGuard(loggedIn: boolean) {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: AuthService, useValue: { isLoggedIn: signal(loggedIn) } }],
  });
  return TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
}

describe('authGuard', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('allows authenticated users through', () => {
    expect(runGuard(true)).toBe(true);
  });

  it('redirects anonymous users to the login page', () => {
    const result = runGuard(false);

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login');
  });
});
