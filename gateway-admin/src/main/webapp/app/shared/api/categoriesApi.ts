import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { Kind } from 'app/shared/types';

/** goods.interfaces.rest.category.CategoryController */
export interface CategoryResponse {
  id: number;
  kind: Kind;
  name: string;
  parentId: number | null;
  level: string;
  description: string | null;
  iconUrl: string | null;
  pictureUrl: string | null;
  keywords: string | null;
  sortOrder: number;
  hidden: boolean;
}
export interface CategoryNodeResponse {
  category: CategoryResponse;
  children: CategoryResponse[];
}
export interface CategoryRequest {
  kind: Kind;
  name: string;
  description?: string | null;
  iconUrl?: string | null;
  pictureUrl?: string | null;
  keywords?: string | null;
  sortOrder?: number | null;
}
export interface MoveRequest {
  parentId: number;
}

const list = { type: 'Category' as const, id: 'LIST' };

export const categoriesApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    getCategoryTree: build.query<CategoryNodeResponse[], { kind?: Kind } | void>({
      query: (arg) => `/api/categories${queryString({ kind: arg?.kind })}`,
      providesTags: [list],
    }),
    getCategory: build.query<CategoryResponse, number>({
      query: (id) => `/api/categories/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'Category', id }],
    }),
    createRootCategory: build.mutation<CategoryResponse, CategoryRequest>({
      query: (body) => ({ url: '/api/categories', method: 'POST', body }),
      invalidatesTags: [list, 'Dashboard'],
    }),
    createChildCategory: build.mutation<CategoryResponse, { parentId: number; body: CategoryRequest }>({
      query: ({ parentId, body }) => ({ url: `/api/categories/${parentId}/children`, method: 'POST', body }),
      invalidatesTags: [list, 'Dashboard'],
    }),
    updateCategory: build.mutation<CategoryResponse, { id: number; body: Partial<CategoryRequest> }>({
      query: ({ id, body }) => ({ url: `/api/categories/${id}`, method: 'PATCH', body }),
      invalidatesTags: (_r, _e, { id }) => [list, { type: 'Category', id }],
    }),
    moveCategory: build.mutation<CategoryResponse, { id: number; body: MoveRequest }>({
      query: ({ id, body }) => ({ url: `/api/categories/${id}/parent`, method: 'PUT', body }),
      invalidatesTags: [list],
    }),
    hideCategory: build.mutation<void, number>({
      query: (id) => ({ url: `/api/categories/${id}`, method: 'DELETE' }),
      invalidatesTags: [list],
    }),
    showCategory: build.mutation<CategoryResponse, number>({
      query: (id) => ({ url: `/api/categories/${id}/show`, method: 'POST' }),
      invalidatesTags: [list],
    }),
  }),
});

export const {
  useGetCategoryTreeQuery, useGetCategoryQuery, useCreateRootCategoryMutation, useCreateChildCategoryMutation,
  useUpdateCategoryMutation, useMoveCategoryMutation, useHideCategoryMutation, useShowCategoryMutation,
} = categoriesApi;
