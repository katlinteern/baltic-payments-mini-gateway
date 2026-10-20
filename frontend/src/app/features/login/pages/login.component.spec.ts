import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { login } from '../state/login.actions';
import { selectLoginError, selectLoginSubmitting } from '../state/login.selectors';
import { LoginComponent } from './login.component';

function render(options: { submitting?: boolean; error?: string | null } = {}) {
  const dispatch = vi.fn();
  const values = new Map<unknown, unknown>([
    [selectLoginSubmitting, signal(options.submitting ?? false)],
    [selectLoginError, signal(options.error ?? null)],
  ]);
  TestBed.configureTestingModule({
    imports: [LoginComponent],
    providers: [{ provide: Store, useValue: { dispatch, selectSignal: (selector: unknown) => values.get(selector) } }],
  });
  const fixture = TestBed.createComponent(LoginComponent);
  fixture.detectChanges();
  return { dispatch, fixture, component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement };
}

describe('LoginComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('does not dispatch while the form is incomplete', () => {
    const { dispatch, component } = render();

    component.submit();

    expect(dispatch).not.toHaveBeenCalled();
  });

  it('dispatches the entered credentials', () => {
    const { dispatch, component } = render();
    component.form.setValue({ username: 'alice', password: 'Alice123!' });

    component.submit();

    expect(dispatch).toHaveBeenCalledWith(login({ username: 'alice', password: 'Alice123!' }));
  });

  it('ignores repeated submits while a login is in flight', () => {
    const { dispatch, component } = render({ submitting: true });
    component.form.setValue({ username: 'alice', password: 'Alice123!' });

    component.submit();

    expect(dispatch).not.toHaveBeenCalled();
  });

  it('shows the failure message as an alert', () => {
    const { element } = render({ error: 'Login failed. Check your credentials and try again.' });

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Login failed');
  });
});
