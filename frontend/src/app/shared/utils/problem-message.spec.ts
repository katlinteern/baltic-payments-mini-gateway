import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { problemMessage } from './problem-message';

const failure = (body: unknown) => new HttpErrorResponse({ status: 422, error: body });

describe('problemMessage', () => {
  it('prefers field validation messages', () => {
    expect(problemMessage(failure({ errors: { a: 'A is wrong.', b: 'B is wrong.' }, detail: 'ignored' }), 'x')).toBe(
      'A is wrong. B is wrong.',
    );
  });

  it('uses the problem detail', () => {
    expect(problemMessage(failure({ detail: 'Balance is too low' }), 'x')).toBe('Balance is too low');
  });

  it('falls back when the response has no problem body', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 0 }), 'Something failed')).toBe('Something failed');
  });
});
