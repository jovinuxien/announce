import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/** gatewayadmin.web.admin.EdgeUserController; status 0 active, 1 disabled, 2 deregistered */
export type UserStatus = 0 | 1 | 2;
export const USER_STATUSES: UserStatus[] = [0, 1, 2];

export interface UserResponse {
  id: number;
  username: string;
  nickname: string | null;
  mobile: string | null;
  avatar: string | null;
  gender: number | null;
  birthday: string | null;
  userLevel: number | null;
  status: UserStatus;
  lastLoginTime: string | null;
  lastLoginIp: string | null;
  addTime: string | null;
}
export interface UserListParams {
  q?: string;
  status?: UserStatus | '';
  page?: number;
  size?: number;
}
export interface StatusRequest {
  status: UserStatus;
}

const list = { type: 'User' as const, id: 'LIST' };

export const usersApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listUsers: build.query<PageResponse<UserResponse>, UserListParams>({
      query: (params) => `/api/admin/users${queryString(params)}`,
      providesTags: [list],
    }),
    getUser: build.query<UserResponse, number>({
      query: (id) => `/api/admin/users/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'User', id }],
    }),
    setUserStatus: build.mutation<UserResponse, { id: number; body: StatusRequest }>({
      query: ({ id, body }) => ({ url: `/api/admin/users/${id}/status`, method: 'PUT', body }),
      invalidatesTags: (_r, _e, { id }) => [list, { type: 'User', id }],
    }),
  }),
});

export const { useListUsersQuery, useGetUserQuery, useSetUserStatusMutation } = usersApi;
