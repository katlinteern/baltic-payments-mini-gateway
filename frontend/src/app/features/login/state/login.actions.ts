import { createAction, props } from '@ngrx/store';

export const login = createAction('[Login] Submit', props<{ username: string; password: string }>());
export const loginSucceeded = createAction('[Login] Success');
export const loginFailed = createAction('[Login] Failure', props<{ error: string }>());
export const clearLogin = createAction('[Login] Clear');
