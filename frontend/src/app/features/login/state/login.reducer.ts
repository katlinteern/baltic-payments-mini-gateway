import { createReducer, on } from '@ngrx/store';
import { clearLogin, login, loginFailed, loginSucceeded } from './login.actions';

export interface LoginState {
  submitting: boolean;
  error: string | null;
}
export const initialLoginState: LoginState = { submitting: false, error: null };

export const loginReducer = createReducer(
  initialLoginState,
  on(login, (state) => ({ ...state, submitting: true, error: null })),
  on(loginSucceeded, () => initialLoginState),
  on(loginFailed, (state, { error }) => ({ ...state, submitting: false, error })),
  on(clearLogin, () => initialLoginState),
);
