import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/** gatewayadmin.web.admin.EdgeRoleController and EdgePermissionController (ROLE_SUPER only) */
export interface RoleResponse {
  id: number;
  name: string;
  description: string | null;
  enabled: boolean;
  addTime: string | null;
  updateTime: string | null;
}
export interface RoleOption {
  id: number;
  name: string;
}
export interface RoleRequest {
  name: string;
  description?: string | null;
  enabled: boolean;
}
export interface PermissionsRequest {
  permissions: string[];
}
export interface Permission {
  code: string;
  description: string;
}

const list = { type: 'Role' as const, id: 'LIST' };

export const rolesApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listRoles: build.query<PageResponse<RoleResponse>, { q?: string; page?: number; size?: number }>({
      query: (params) => `/api/admin/roles${queryString(params)}`,
      providesTags: [list],
    }),
    getAllRoles: build.query<RoleOption[], void>({
      query: () => '/api/admin/roles/all',
      providesTags: [list],
    }),
    getRole: build.query<RoleResponse, number>({
      query: (id) => `/api/admin/roles/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'Role', id }],
    }),
    createRole: build.mutation<RoleResponse, RoleRequest>({
      query: (body) => ({ url: '/api/admin/roles', method: 'POST', body }),
      invalidatesTags: [list],
    }),
    updateRole: build.mutation<RoleResponse, { id: number; body: RoleRequest }>({
      query: ({ id, body }) => ({ url: `/api/admin/roles/${id}`, method: 'PUT', body }),
      invalidatesTags: (_r, _e, { id }) => [list, { type: 'Role', id }],
    }),
    deleteRole: build.mutation<void, number>({
      query: (id) => ({ url: `/api/admin/roles/${id}`, method: 'DELETE' }),
      invalidatesTags: [list],
    }),
    getRolePermissions: build.query<string[], number>({
      query: (id) => `/api/admin/roles/${id}/permissions`,
      providesTags: (_r, _e, id) => [{ type: 'RolePermissions', id }],
    }),
    setRolePermissions: build.mutation<void, { id: number; body: PermissionsRequest }>({
      query: ({ id, body }) => ({ url: `/api/admin/roles/${id}/permissions`, method: 'PUT', body }),
      invalidatesTags: (_r, _e, { id }) => [{ type: 'RolePermissions', id }],
    }),
    getPermissions: build.query<Permission[], void>({
      query: () => '/api/admin/permissions',
      providesTags: ['Permission'],
    }),
  }),
});

export const {
  useListRolesQuery, useGetAllRolesQuery, useGetRoleQuery, useCreateRoleMutation, useUpdateRoleMutation, useDeleteRoleMutation,
  useGetRolePermissionsQuery, useSetRolePermissionsMutation, useGetPermissionsQuery,
} = rolesApi;
