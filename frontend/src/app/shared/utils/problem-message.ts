import { HttpErrorResponse } from '@angular/common/http';

/**
 * Turns an RFC 7807 problem response into a message for the user: field validation messages when present,
 * otherwise the problem detail, otherwise the given fallback.
 */
export function problemMessage(error: HttpErrorResponse, fallback: string): string {
  const fieldErrors = error.error?.errors as Record<string, string> | undefined;
  if (fieldErrors) return Object.values(fieldErrors).join(' ');
  return error.error?.detail ?? fallback;
}
