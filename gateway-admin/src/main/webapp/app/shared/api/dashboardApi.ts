import { adminApi } from './adminApi';
import { queryString } from './baseQuery';

/** gatewayadmin.web.admin.EdgeDashboardController */
export interface AnnounceCount {
  kind: string;
  status: string;
  total: number;
}
export interface ListingCount {
  source: string;
  status: string;
  total: number;
}
export interface DayCount {
  day: string;
  total: number;
}
export interface Dashboard {
  announces: AnnounceCount[];
  listings: ListingCount[];
  members: number;
  admins: number;
  categories: number;
  brands: number;
  listingClicks: number;
  newMembersPerDay: DayCount[];
  newAnnouncesPerDay: DayCount[];
  generatedAt: string;
}

export const dashboardApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    getDashboard: build.query<Dashboard, { days?: number } | void>({
      query: (arg) => `/api/admin/dashboard${queryString({ days: arg?.days })}`,
      providesTags: ['Dashboard'],
    }),
  }),
});

export const { useGetDashboardQuery } = dashboardApi;
