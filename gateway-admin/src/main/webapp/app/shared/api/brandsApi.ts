import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/** goods.interfaces.rest.brand.BrandController */
export interface BrandResponse {
  id: number;
  name: string;
  description: string | null;
  pictureUrl: string | null;
  sortOrder: number;
  floorPrice: number | null;
  hidden: boolean;
}
export interface BrandRequest {
  name: string;
  description?: string | null;
  pictureUrl?: string | null;
  sortOrder?: number | null;
}

const list = { type: 'Brand' as const, id: 'LIST' };

export const brandsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listBrands: build.query<PageResponse<BrandResponse>, { page?: number; size?: number }>({
      query: (params) => `/api/brands${queryString(params)}`,
      providesTags: [list],
    }),
    getBrand: build.query<BrandResponse, number>({
      query: (id) => `/api/brands/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'Brand', id }],
    }),
    createBrand: build.mutation<BrandResponse, BrandRequest>({
      query: (body) => ({ url: '/api/brands', method: 'POST', body }),
      invalidatesTags: [list, 'Dashboard'],
    }),
    updateBrand: build.mutation<BrandResponse, { id: number; body: BrandRequest }>({
      query: ({ id, body }) => ({ url: `/api/brands/${id}`, method: 'PUT', body }),
      invalidatesTags: (_r, _e, { id }) => [list, { type: 'Brand', id }],
    }),
    hideBrand: build.mutation<void, number>({
      query: (id) => ({ url: `/api/brands/${id}`, method: 'DELETE' }),
      invalidatesTags: [list],
    }),
  }),
});

export const { useListBrandsQuery, useGetBrandQuery, useCreateBrandMutation, useUpdateBrandMutation, useHideBrandMutation } = brandsApi;
