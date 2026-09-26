// The access token lives in memory only (never in localStorage); the refresh token is an httpOnly cookie the
// browser sends to /auth/refresh by itself. A page reload therefore starts logged out and silently refreshes.
let accessToken: string | null = null;
const listeners = new Set<(token: string | null) => void>();

export const tokenStore = {
  get(): string | null {
    return accessToken;
  },
  set(token: string | null): void {
    accessToken = token;
    listeners.forEach(l => l(token));
  },
  subscribe(listener: (token: string | null) => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
