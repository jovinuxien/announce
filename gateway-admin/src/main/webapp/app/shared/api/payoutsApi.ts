import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/**
 * Phase 2 (features.trade). Payout overview derived from doc/order-context.md §3 (release rule), §4 (`payment`
 * aggregate: PayoutRequested, PayoutSent) and §5 (announce_payment, announce_seller_account). The edge routes
 * /api/admin/payouts/** to lb://order (doc/frontend-edges.md §4). Served by MSW for now.
 */
export type PayoutStatus = 'PENDING_RELEASE' | 'RELEASING' | 'SENT' | 'FAILED' | 'BLOCKED_DISPUTE';
export const PAYOUT_STATUSES: PayoutStatus[] = ['PENDING_RELEASE', 'RELEASING', 'SENT', 'FAILED', 'BLOCKED_DISPUTE'];

export interface PayoutRow {
  orderId: string;
  paymentId: string;
  sellerId: number;
  sellerAccountStatus: 'PENDING' | 'VERIFIED' | 'RESTRICTED';
  payoutAmount: number;
  feeAmount: number;
  currency: string;
  status: PayoutStatus;
  releaseDueAt: string | null;
  releasedAt: string | null;
  payoutRef: string | null;
}
export interface PayoutTotals {
  pendingAmount: number;
  sentAmount: number;
  blockedAmount: number;
  feesCollected: number;
  currency: string;
}
export interface PayoutOverview {
  totals: PayoutTotals;
  page: PageResponse<PayoutRow>;
}

export const payoutsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    getPayouts: build.query<PayoutOverview, { status?: PayoutStatus | ''; page?: number; size?: number }>({
      query: (params) => `/api/admin/payouts${queryString(params)}`,
      providesTags: ['Payout'],
    }),
  }),
});

export const { useGetPayoutsQuery } = payoutsApi;
