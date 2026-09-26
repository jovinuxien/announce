import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/** gatewayadmin.web.admin.EdgeRegionController; type 1 country/state, 2 city/county, 3 area */
export type RegionType = 1 | 2 | 3;
export const REGION_TYPES: RegionType[] = [1, 2, 3];

export interface RegionResponse {
  id: number;
  pid: number;
  name: string;
  type: RegionType;
  code: number;
}
export interface RegionRequest {
  pid: number;
  name: string;
  type: RegionType;
  code?: number | null;
}
export interface RegionListParams {
  pid?: number | '';
  type?: RegionType | '';
  q?: string;
  page?: number;
  size?: number;
}

const list = { type: 'Region' as const, id: 'LIST' };

export const regionsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listRegions: build.query<PageResponse<RegionResponse>, RegionListParams>({
      query: (params) => `/api/admin/regions${queryString(params)}`,
      providesTags: [list],
    }),
    getRegion: build.query<RegionResponse, number>({
      query: (id) => `/api/admin/regions/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'Region', id }],
    }),
    createRegion: build.mutation<RegionResponse, RegionRequest>({
      query: (body) => ({ url: '/api/admin/regions', method: 'POST', body }),
      invalidatesTags: [list],
    }),
    updateRegion: build.mutation<RegionResponse, { id: number; body: RegionRequest }>({
      query: ({ id, body }) => ({ url: `/api/admin/regions/${id}`, method: 'PUT', body }),
      invalidatesTags: (_r, _e, { id }) => [list, { type: 'Region', id }],
    }),
    deleteRegion: build.mutation<void, number>({
      query: (id) => ({ url: `/api/admin/regions/${id}`, method: 'DELETE' }),
      invalidatesTags: [list],
    }),
  }),
});

export const { useListRegionsQuery, useGetRegionQuery, useCreateRegionMutation, useUpdateRegionMutation, useDeleteRegionMutation } = regionsApi;
