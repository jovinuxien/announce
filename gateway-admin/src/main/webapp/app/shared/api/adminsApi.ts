import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/** gatewayadmin.web.admin.EdgeAdminAccountController (ROLE_SUPER only) */
export interface AdminResponse {
  id: number;
  username: string;
  avatar: string | null;
  roleIds: number[];
  lastLoginTime: string | null;
  lastLoginIp: string | null;
  addTime: string | null;
  updateTime: string | null;
}
export interface AdminRequest {
  username: string;
  password?: string | null;
  avatar?: string | null;
  roleIds: number[];
}
export interface AdminListParams {
  q?: string;
  page?: number;
  size?: number;
}

const list = { type: 'Admin' as const, id: 'LIST' };

export const adminsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listAdmins: build.query<PageResponse<AdminResponse>, AdminListParams>({
      query: (params) => `/api/admin/admins${queryString(params)}`,
      providesTags: [list],
    }),
    getAdmin: build.query<AdminResponse, number>({
      query: (id) => `/api/admin/admins/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'Admin', id }],
    }),
    createAdmin: build.mutation<AdminResponse, AdminRequest>({
      query: (body) => ({ url: '/api/admin/admins', method: 'POST', body }),
      invalidatesTags: [list, 'Dashboard'],
    }),
    updateAdmin: build.mutation<AdminResponse, { id: number; body: AdminRequest }>({
      query: ({ id, body }) => ({ url: `/api/admin/admins/${id}`, method: 'PUT', body }),
      invalidatesTags: (_r, _e, { id }) => [list, { type: 'Admin', id }],
    }),
    deleteAdmin: build.mutation<void, number>({
      query: (id) => ({ url: `/api/admin/admins/${id}`, method: 'DELETE' }),
      invalidatesTags: [list, 'Dashboard'],
    }),
  }),
});

export const { useListAdminsQuery, useGetAdminQuery, useCreateAdminMutation, useUpdateAdminMutation, useDeleteAdminMutation } = adminsApi;
