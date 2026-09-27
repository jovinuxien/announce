import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/**
 * Phase 2 (features.trade). Contract sketched from doc/order-context.md §4 (order states), §5 (announce_order,
 * announce_payment, announce_shipment read models) and §8 (admin REST: POST /api/admin/orders/{id}/resolve-dispute).
 * Served by MSW (app/mocks/handlers.ts) until the order module is routed through the edge.
 */
export type OrderStatus =
  | 'PROPOSED' | 'AGREED' | 'PAYMENT_PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'RELEASING' | 'SETTLED'
  | 'CANCELLED' | 'DISPUTED' | 'REFUNDING' | 'REFUNDED';
export const ORDER_STATUSES: OrderStatus[] = [
  'PROPOSED', 'AGREED', 'PAYMENT_PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'RELEASING', 'SETTLED', 'CANCELLED', 'DISPUTED', 'REFUNDING', 'REFUNDED',
];
export type OrderDoor = 'AUCTION' | 'FIXED_PRICE' | 'OFFER';

/** announce_order row (§5) */
export interface OrderSummary {
  id: string;
  announceId: string;
  announceTitle: string | null;
  sellerId: number;
  buyerId: number;
  door: OrderDoor;
  agreedPrice: number;
  feeAmount: number;
  payoutAmount: number;
  labelPrice: number | null;
  currency: string;
  status: OrderStatus;
  agreedAt: string | null;
  paidAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  releasedAt: string | null;
  settledAt: string | null;
  disputeReason: string | null;
}
/** announce_payment row (§5) */
export interface PaymentView {
  id: string;
  provider: string;
  intentRef: string | null;
  status: string;
  amount: number;
  feeAmount: number;
  payoutRef: string | null;
  refundRef: string | null;
  confirmedAt: string | null;
  releasedAt: string | null;
  refundedAt: string | null;
}
/** announce_shipment row (§5) */
export interface ShipmentView {
  id: string;
  carrier: string;
  service: string | null;
  trackingNumber: string | null;
  labelUrl: string | null;
  status: string;
  lastCarrierEvent: string | null;
  lastEventAt: string | null;
  deliveredAt: string | null;
}
export interface TimelineEntry {
  at: string;
  type: string;
  detail: string | null;
}
export interface OrderDetail extends OrderSummary {
  payment: PaymentView | null;
  shipment: ShipmentView | null;
  timeline: TimelineEntry[];
}
export type DisputeResolution = 'RELEASE' | 'REFUND';
export interface ResolveDisputeRequest {
  resolution: DisputeResolution;
  reason: string;
}

export const ordersApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listOrders: build.query<PageResponse<OrderSummary>, { status?: OrderStatus | ''; page?: number; size?: number }>({
      query: (params) => `/api/admin/orders${queryString(params)}`,
      providesTags: [{ type: 'Order', id: 'LIST' }],
    }),
    getOrder: build.query<OrderDetail, string>({
      query: (id) => `/api/admin/orders/${encodeURIComponent(id)}`,
      providesTags: (_r, _e, id) => [{ type: 'Order', id }],
    }),
    resolveDispute: build.mutation<OrderDetail, { id: string; body: ResolveDisputeRequest }>({
      query: ({ id, body }) => ({ url: `/api/admin/orders/${encodeURIComponent(id)}/resolve-dispute`, method: 'POST', body }),
      invalidatesTags: (_r, _e, { id }) => [{ type: 'Order', id }, { type: 'Order', id: 'LIST' }, 'Payout'],
    }),
  }),
});

export const { useListOrdersQuery, useGetOrderQuery, useResolveDisputeMutation } = ordersApi;
