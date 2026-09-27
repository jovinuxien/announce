import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { ROLE_SUPER } from 'app/config/constants';

/** AuthController.AdminSummary */
export interface AdminSummary {
  id: number;
  username: string;
  avatar: string | null;
  roleIds: number[];
  roles: string[];
}

/** AuthController.TokenResponse */
export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  admin: AdminSummary;
}

export type AuthStatus = 'restoring' | 'authenticated' | 'anonymous';

export interface AuthState {
  /** Only ever in memory: never persisted, never in a cookie the SPA can read. */
  accessToken: string | null;
  admin: AdminSummary | null;
  /** Epoch millis when the access token expires. */
  expiresAt: number | null;
  status: AuthStatus;
}

export const initialAuthState: AuthState = {
  accessToken: null,
  admin: null,
  expiresAt: null,
  status: 'restoring',
};

const authSlice = createSlice({
  name: 'auth',
  initialState: initialAuthState,
  reducers: {
    /** Login or refresh succeeded. */
    signedIn(state, action: PayloadAction<TokenResponse>) {
      state.accessToken = action.payload.accessToken;
      state.admin = action.payload.admin;
      state.expiresAt = Date.now() + action.payload.expiresIn * 1000;
      state.status = 'authenticated';
    },
    /** The start-up refresh failed: no session to restore. */
    restoreFailed(state) {
      state.accessToken = null;
      state.admin = null;
      state.expiresAt = null;
      state.status = 'anonymous';
    },
    /** Explicit logout or a refresh that failed after a 401. */
    loggedOut(state) {
      state.accessToken = null;
      state.admin = null;
      state.expiresAt = null;
      state.status = 'anonymous';
    },
  },
});

export const { signedIn, restoreFailed, loggedOut } = authSlice.actions;
export const authReducer = authSlice.reducer;

export interface HasAuth {
  auth: AuthState;
}

export const selectAuth = (s: HasAuth) => s.auth;
export const selectAccessToken = (s: HasAuth) => s.auth.accessToken;
export const selectAdmin = (s: HasAuth) => s.auth.admin;
export const selectIsAuthenticated = (s: HasAuth) => s.auth.status === 'authenticated';
export const selectRoles = (s: HasAuth) => s.auth.admin?.roles ?? [];
export const selectIsSuper = (s: HasAuth) => (s.auth.admin?.roles ?? []).includes(ROLE_SUPER);
export const hasRole = (roles: string[], role: string) => roles.includes(role);
