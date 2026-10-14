import { createFeatureSelector, createSelector } from '@ngrx/store';
import { LoginState } from './login.reducer';

export const selectLoginState = createFeatureSelector<LoginState>('login');
export const selectLoginSubmitting = createSelector(selectLoginState, state => state.submitting);
export const selectLoginError = createSelector(selectLoginState, state => state.error);