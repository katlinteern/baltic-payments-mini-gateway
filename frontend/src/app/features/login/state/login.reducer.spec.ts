import { describe, expect, it } from 'vitest';
import { clearLogin, login, loginFailed, loginSucceeded } from './login.actions';
import { initialLoginState, loginReducer } from './login.reducer';

describe('loginReducer', () => {
  it('tracks pending, failed and successful login states', () => {
    const pending = loginReducer(initialLoginState, login({ username: 'alice', password: 'secret' }));
    expect(pending).toEqual({ submitting: true, error: null });
    expect(loginReducer(pending, loginFailed({ error: 'invalid credentials' }))).toEqual({
      submitting: false,
      error: 'invalid credentials',
    });
    expect(loginReducer(pending, loginSucceeded())).toEqual(initialLoginState);
  });

  it('clears login state', () => {
    expect(loginReducer({ submitting: false, error: 'old error' }, clearLogin())).toEqual(initialLoginState);
  });
});
