import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { sessionEnded } from './session';

function setup(token: string | null) {
  const logout = vi.fn();
  const dispatch = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { token: signal(token), logout } },
      { provide: Store, useValue: { dispatch } },
    ],
  });
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  return {
    logout,
    dispatch,
    navigate,
    http: TestBed.inject(HttpClient),
    controller: TestBed.inject(HttpTestingController),
  };
}

describe('authInterceptor', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('adds the bearer token and a correlation id to requests', () => {
    const { http, controller } = setup('jwt-token');

    http.get('/api/accounts').subscribe();
    const request = controller.expectOne('/api/accounts');

    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    expect(request.request.headers.get('X-Correlation-Id')).toMatch(/^[A-Za-z0-9-]{1,64}$/);
  });

  it('sends no Authorization header when signed out', () => {
    const { http, controller } = setup(null);

    http.get('/api/accounts').subscribe();

    expect(controller.expectOne('/api/accounts').request.headers.has('Authorization')).toBe(false);
  });

  it('signs the user out and redirects on a 401 from a protected endpoint', () => {
    const { http, controller, logout, navigate, dispatch } = setup('expired');

    http.get('/api/accounts').subscribe({ error: () => undefined });
    controller.expectOne('/api/accounts').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalled();
    expect(dispatch).toHaveBeenCalledWith(sessionEnded());
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not sign out when the login request itself is rejected', () => {
    const { http, controller, logout, dispatch } = setup(null);

    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    controller.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(logout).not.toHaveBeenCalled();
    expect(dispatch).not.toHaveBeenCalled();
  });
});
