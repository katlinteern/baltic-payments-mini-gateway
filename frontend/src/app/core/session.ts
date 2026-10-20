import { createAction, MetaReducer } from '@ngrx/store';

export const sessionEnded = createAction('[Session] Ended');

export const clearStateOnSessionEnd: MetaReducer = (reducer) => (state, action) =>
  reducer(action.type === sessionEnded.type ? undefined : state, action);
