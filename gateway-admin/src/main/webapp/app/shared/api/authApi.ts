import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQueryWithReauth } from './baseQuery';
import { loggedOut, restoreFailed, signedIn, type AdminSummary, type TokenResponse } from 'app/shared/auth/authSlice';

/** gatewayadmin.auth.AuthController */
export interface LoginRequest {
  username: string;
  password: string;
}

export const authApi = createApi({
  reducerPath: 'authApi',
  baseQuery: baseQueryWithReauth,
  endpoints: (build) => ({
    login: build.mutation<TokenResponse, LoginRequest>({
      query: (body) => ({ url: '/auth/login', method: 'POST', body }),
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(signedIn(data));
        } catch {
          /* the component shows the problem */
        }
      },
    }),
    /** Start-up session restore; the refresh cookie travels on its own. */
    refresh: build.mutation<TokenResponse, void>({
      query: () => ({ url: '/auth/refresh', method: 'POST' }),
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(signedIn(data));
        } catch {
          dispatch(restoreFailed());
        }
      },
    }),
    logout: build.mutation<void, void>({
      query: () => ({ url: '/auth/logout', method: 'POST' }),
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          await queryFulfilled;
        } finally {
          dispatch(loggedOut());
        }
      },
    }),
    me: build.query<AdminSummary, void>({
      query: () => '/auth/me',
    }),
  }),
});

export const { useLoginMutation, useRefreshMutation, useLogoutMutation, useMeQuery } = authApi;
