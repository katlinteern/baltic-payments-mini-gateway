import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

const future = () => new Date(Date.now() + 60 * 60 * 1000).toISOString();
const past = () => new Date(Date.now() - 1000).toISOString();

function createService(): AuthService {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return TestBed.inject(AuthService);
}

describe('AuthService', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => TestBed.resetTestingModule());

  it('stores the session after a successful login', () => {
    const service = createService();
    const http = TestBed.inject(HttpTestingController);

    service.login('alice', 'secret').subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ username: 'alice', password: 'secret' });
    request.flush({ token: 'jwt', username: 'alice', expiresAt: future() });

    expect(service.isLoggedIn()).toBe(true);
    expect(service.token()).toBe('jwt');
    expect(service.username()).toBe('alice');
    expect(JSON.parse(localStorage.getItem('session') ?? '{}').token).toBe('jwt');
  });

  it('restores an unexpired session from storage', () => {
    localStorage.setItem('session', JSON.stringify({ token: 'jwt', username: 'bob', expiresAt: future() }));

    const service = createService();

    expect(service.isLoggedIn()).toBe(true);
    expect(service.username()).toBe('bob');
  });

  it('discards an expired session instead of restoring it', () => {
    localStorage.setItem('session', JSON.stringify({ token: 'old', username: 'bob', expiresAt: past() }));

    const service = createService();

    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('session')).toBeNull();
  });

  it('ignores corrupt stored data', () => {
    localStorage.setItem('session', '{not json');

    expect(createService().isLoggedIn()).toBe(false);
  });

  it('clears the session on logout', () => {
    localStorage.setItem('session', JSON.stringify({ token: 'jwt', username: 'bob', expiresAt: future() }));
    const service = createService();

    service.logout();

    expect(service.isLoggedIn()).toBe(false);
    expect(service.token()).toBeNull();
    expect(localStorage.getItem('session')).toBeNull();
  });
});
