import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { ListingSource } from 'app/shared/types';
import type { ListingCount } from './dashboardApi';

/** gatewayadmin.web.admin.EdgeListingStatsController */
export interface ListingRow {
  id: string;
  source: string;
  title: string;
  url: string;
  price: number | null;
  currency: string | null;
  status: string;
  clickCount: number;
  lastSeenAt: string | null;
  publishedAt: string | null;
}
export interface ListingStats {
  counts: ListingCount[];
  clicks: number;
  recentGone: ListingRow[];
  topClicked: ListingRow[];
}

/** listing.interfaces.rest.ListingAdminController */
export interface ImportRequest {
  path: string;
  source?: ListingSource | null;
}
/** listing.application.ImportReport */
export interface ImportReport {
  seen: number;
  inserted: number;
  updated: number;
  unchanged: number;
  rejected: number;
  indexed?: number;
}
export interface ReindexResult {
  indexed: number;
}

export const listingsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    getListingStats: build.query<ListingStats, { limit?: number } | void>({
      query: (arg) => `/api/admin/listing-stats${queryString({ limit: arg?.limit })}`,
      providesTags: ['ListingStats'],
    }),
    importListings: build.mutation<ImportReport, ImportRequest>({
      query: (body) => ({ url: '/api/admin/listings/import', method: 'POST', body }),
      invalidatesTags: ['ListingStats', 'Dashboard'],
    }),
    reindexListings: build.mutation<ReindexResult, void>({
      query: () => ({ url: '/api/admin/listings/reindex', method: 'POST' }),
      invalidatesTags: ['ListingStats'],
    }),
  }),
});

export const { useGetListingStatsQuery, useImportListingsMutation, useReindexListingsMutation } = listingsApi;
