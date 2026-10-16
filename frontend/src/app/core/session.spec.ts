import { ActionReducer, createAction, createReducer, on } from '@ngrx/store';
import { describe, expect, it } from 'vitest';
import { clearStateOnSessionEnd, sessionEnded } from './session';

const bump = createAction('[Test] Bump');
const counter = createReducer(
  0,
  on(bump, (value) => value + 1),
);
const reducer: ActionReducer<number> = clearStateOnSessionEnd(
  counter as ActionReducer<unknown>,
) as ActionReducer<number>;

describe('clearStateOnSessionEnd', () => {
  it('passes ordinary actions through to the wrapped reducer', () => {
    expect(reducer(5, bump())).toBe(6);
  });

  it('resets the state to its initial value when the session ends', () => {
    expect(reducer(5, sessionEnded())).toBe(0);
  });
});
