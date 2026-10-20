import { HttpErrorResponse } from '@angular/common/http';

export function problemMessage(error: HttpErrorResponse, fallback: string): string {
  const fieldErrors = error.error?.errors as Record<string, string> | undefined;
  if (fieldErrors) return Object.values(fieldErrors).join(' ');
  return error.error?.detail ?? fallback;
}
