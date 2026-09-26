import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { ApiError } from 'app/config/http';
import { tokenStore } from 'app/config/tokenStore';
import { authApi } from 'app/shared/api/authApi';
import { LoginRequest, RegisterRequest, TokenResponse, UserResponse } from 'app/shared/model/auth';

export type AuthStatus = 'unknown' | 'anonymous' | 'authenticated';

export interface AuthState {
  status: AuthStatus;
  user: UserResponse | null;
  loading: boolean;
  error: string | null;
}

const initialState: AuthState = { status: 'unknown', user: null, loading: false, error: null };

const describe = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);

/** On page load: try the refresh cookie once; anonymous when it fails. */
export const bootstrapSession = createAsyncThunk<TokenResponse | null>('auth/bootstrap', async () => {
  try {
    return await authApi.refresh();
  } catch {
    return null;
  }
});

export const login = createAsyncThunk<TokenResponse, LoginRequest, { rejectValue: string }>('auth/login', async (body, api) => {
  try {
    return await authApi.login(body);
  } catch (e) {
    return api.rejectWithValue(describe(e, 'login failed'));
  }
});

export const register = createAsyncThunk<TokenResponse, RegisterRequest, { rejectValue: string }>('auth/register', async (body, api) => {
  try {
    return await authApi.register(body);
  } catch (e) {
    return api.rejectWithValue(describe(e, 'registration failed'));
  }
});

export const logout = createAsyncThunk('auth/logout', async () => {
  try {
    await authApi.logout();
  } finally {
    tokenStore.set(null);
  }
});

function signedIn(state: AuthState, payload: TokenResponse) {
  tokenStore.set(payload.accessToken);
  state.status = 'authenticated';
  state.user = payload.user;
  state.loading = false;
  state.error = null;
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionExpired(state) {
      tokenStore.set(null);
      state.status = 'anonymous';
      state.user = null;
      state.loading = false;
    },
    clearError(state) {
      state.error = null;
    },
    userUpdated(state, action: PayloadAction<UserResponse>) {
      state.user = action.payload;
    },
  },
  extraReducers: builder => {
    builder
      .addCase(bootstrapSession.fulfilled, (state, action) => {
        if (action.payload) signedIn(state, action.payload);
        else {
          state.status = 'anonymous';
          state.user = null;
        }
      })
      .addCase(login.pending, state => {
        state.loading = true;
        state.error = null;
      })
      .addCase(login.fulfilled, (state, action) => signedIn(state, action.payload))
      .addCase(login.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload ?? 'login failed';
      })
      .addCase(register.pending, state => {
        state.loading = true;
        state.error = null;
      })
      .addCase(register.fulfilled, (state, action) => signedIn(state, action.payload))
      .addCase(register.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload ?? 'registration failed';
      })
      .addCase(logout.fulfilled, state => {
        state.status = 'anonymous';
        state.user = null;
      })
      .addCase(logout.rejected, state => {
        state.status = 'anonymous';
        state.user = null;
      });
  },
});

export const { sessionExpired, clearError, userUpdated } = authSlice.actions;
export default authSlice.reducer;
