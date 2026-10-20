import { createAction, MetaReducer } from '@ngrx/store';

/** Dispatched when the user signs out or the API rejects the session; every feature slice is reset. */
export const sessionEnded = createAction('[Session] Ended');

/** Resets the whole store on sign-out, including lazily registered slices, so no feature has to know about the others. */
export const clearStateOnSessionEnd: MetaReducer = (reducer) => (state, action) =>
  reducer(action.type === sessionEnded.type ? undefined : state, action);
