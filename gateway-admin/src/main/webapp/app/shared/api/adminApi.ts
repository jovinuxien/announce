import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQueryWithReauth } from './baseQuery';

/**
 * One RTK Query api instance for every backend resource so tags can be shared; the per-resource files
 * (dashboardApi.ts, announcesApi.ts, ...) inject their endpoints here.
 */
export const adminApi = createApi({
  reducerPath: 'adminApi',
  baseQuery: baseQueryWithReauth,
  tagTypes: [
    'Dashboard', 'Announce', 'Category', 'Brand', 'ListingStats', 'User', 'Admin', 'Role', 'RolePermissions',
    'Permission', 'Region', 'Order', 'Auction', 'Payout', 'Conversation', 'Bulletin',
  ],
  endpoints: () => ({}),
});
