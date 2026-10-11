import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';
import type { OrderStatus } from './ordersApi';

/**
 * `GET /api/admin/payouts` (order module, `OrderController` + `Payouts`), through the admin edge. There is no
 * payout table: the order context derives one line per order that holds or held money from the `announce_order`
 * row and its copy of the seller gate (`announce_order_seller`), and sums every live order for the totals. The
 * provider's payout reference belongs to the payment context (`GET /api/admin/orders/{id}/payment`).
 * See doc/frontend-edges.md §12.
 */
export type PayoutStatus = 'PENDING_RELEASE' | 'BLOCKED_DISPUTE' | 'RELEASING' | 'SENT' | 'REFUNDED';
export const PAYOUT_STATUSES: PayoutStatus[] = ['PENDING_RELEASE', 'BLOCKED_DISPUTE', 'RELEASING', 'SENT', 'REFUNDED'];
export type SellerAccountStatus = 'PENDING' | 'VERIFIED' | 'RESTRICTED';

/** `Payouts.PayoutRow`. */
export interface PayoutRow {
  orderId: string;
  announceId: string;
  title: string | null;
  sellerId: number;
  buyerId: number;
  sellerAccountStatus: SellerAccountStatus | string | null;
  payoutsEnabled: boolean;
  agreedPrice: number;
  feeAmount: number;
  payoutAmount: number;
  currency: string;
  status: PayoutStatus;
  orderStatus: OrderStatus;
  releaseDueAt: string | null;
  paidAt: string | null;
  releasedAt: string | null;
  settledAt: string | null;
  disputeReason: string | null;
  disputeOpenedAt: string | null;
}
/** `Payouts.PayoutTotals`: sums over every live order that holds or held money, not only the page. */
export interface PayoutTotals {
  pendingAmount: number;
  pendingOrders: number;
  blockedAmount: number;
  blockedOrders: number;
  releasingAmount: number;
  releasingOrders: number;
  sentAmount: number;
  sentOrders: number;
  refundedAmount: number;
  refundedOrders: number;
  feesCollected: number;
  feesPending: number;
  currency: string | null;
}
/** `OrderController.PayoutOverview`. */
export interface PayoutOverview {
  totals: PayoutTotals;
  page: PageResponse<PayoutRow>;
}
export interface PayoutFilter {
  status?: PayoutStatus | '';
  seller?: number | '';
  page?: number;
  size?: number;
}

export const payoutsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    getPayouts: build.query<PayoutOverview, PayoutFilter>({
      query: (params) => `/api/admin/payouts${queryString(params)}`,
      providesTags: ['Payout'],
    }),
  }),
});

export const { useGetPayoutsQuery } = payoutsApi;
