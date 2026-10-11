import { fetchBaseQuery, type BaseQueryFn, type FetchArgs, type FetchBaseQueryError } from '@reduxjs/toolkit/query/react';
import { loggedOut, signedIn, type HasAuth, type TokenResponse } from 'app/shared/auth/authSlice';

/**
 * fetchBaseQuery + the refresh-and-retry rule of doc/frontend-edges.md §2:
 *  - every request carries `Authorization: Bearer <access token from memory>`;
 *  - a 401 from /api/** triggers ONE silent `POST /auth/refresh` (httpOnly cookie on /auth, same-origin) and a
 *    retry of the original request;
 *  - when the refresh fails the session is cleared (loggedOut) and RequireAuth routes to /login.
 * Concurrent 401s share a single in-flight refresh.
 */
/** The page's own origin (a WHATWG Request needs an absolute URL); Node (jest) falls back to localhost. */
const BASE_URL = typeof window !== 'undefined' && window.location && window.location.origin && window.location.origin !== 'null' ? window.location.origin : 'http://localhost';

export const rawBaseQuery = fetchBaseQuery({
  baseUrl: BASE_URL,
  credentials: 'same-origin',
  // resolved at call time so a test can swap global fetch after this module loaded
  fetchFn: (...args) => fetch(...args),
  prepareHeaders: (headers, { getState }) => {
    const token = (getState() as HasAuth).auth.accessToken;
    if (token && !headers.has('authorization')) headers.set('authorization', `Bearer ${token}`);
    if (!headers.has('accept')) headers.set('accept', 'application/json, application/problem+json');
    return headers;
  },
});

export const REFRESH_URL = '/auth/refresh';

let refreshInFlight: Promise<boolean> | null = null;

/** Exposed for tests. */
export function resetRefreshState() {
  refreshInFlight = null;
}

function urlOf(args: string | FetchArgs): string {
  return typeof args === 'string' ? args : args.url;
}

function isAuthEndpoint(url: string): boolean {
  return url.startsWith('/auth/') || url.startsWith('auth/');
}

async function refreshOnce(api: Parameters<BaseQueryFn>[1], extra: Parameters<BaseQueryFn>[2]): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const result = await rawBaseQuery({ url: REFRESH_URL, method: 'POST' }, api, extra);
        if (result.error || !result.data) return false;
        api.dispatch(signedIn(result.data as TokenResponse));
        return true;
      } catch {
        return false;
      } finally {
        // let the next 401 start a fresh refresh
        setTimeout(() => {
          refreshInFlight = null;
        }, 0);
      }
    })();
  }
  return refreshInFlight;
}

export const baseQueryWithReauth: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (args, api, extra) => {
  let result = await rawBaseQuery(args, api, extra);
  if (result.error && result.error.status === 401 && !isAuthEndpoint(urlOf(args))) {
    const refreshed = await refreshOnce(api, extra);
    if (refreshed) {
      result = await rawBaseQuery(args, api, extra);
    } else {
      api.dispatch(loggedOut());
    }
  }
  return result;
};

/** Idempotency key for command endpoints (X-Command-Id). */
export function commandId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** Builds a query string from an object, skipping empty values. Arrays repeat the key. */
export function queryString(params: object): string {
  const sp = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    if (Array.isArray(v)) v.forEach((item) => sp.append(k, String(item)));
    else sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}
