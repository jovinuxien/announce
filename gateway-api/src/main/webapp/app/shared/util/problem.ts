import { ApiError } from 'app/config/http';

/** The text to show for a failed call: the problem detail's `detail`, its field errors, or a fallback. */
export function describeError(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    const errors = e.problem?.errors;
    if (errors && errors.length) return errors.join('; ');
    return e.message || fallback;
  }
  if (e instanceof Error && e.message) return e.message;
  return fallback;
}
