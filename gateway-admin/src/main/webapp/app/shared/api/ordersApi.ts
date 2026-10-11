import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/**
 * Admin reads of the order, payment and shipping contexts (order module), through the admin edge
 * (`/api/admin/orders/**` -> lb://order). Each context answers its own read-model row as it is (db-reactive
 * `announce_order`, `announce_payment`, `announce_shipment`): amounts are decimals, timestamps ISO local
 * date-times. The order detail screen combines three calls; there is no timeline endpoint, the screen derives one
 * from the row's timestamps (see ./orderTimeline.ts). See doc/frontend-edges.md §12.
 */
export type OrderStatus =
  | 'PROPOSED' | 'AGREED' | 'PAYMENT_PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'RELEASING' | 'SETTLED'
  | 'DISPUTED' | 'REFUNDING' | 'REFUNDED' | 'CANCELLED' | 'DECLINED';
export const ORDER_STATUSES: OrderStatus[] = [
  'PROPOSED', 'AGREED', 'PAYMENT_PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'RELEASING', 'SETTLED',
  'DISPUTED', 'REFUNDING', 'REFUNDED', 'CANCELLED', 'DECLINED',
];
export type OrderDoor = 'AUCTION' | 'FIXED_PRICE' | 'OFFER';

/** `announce_order` row. */
export interface OrderRow {
  id: string;
  announceId: string;
  sellerId: number;
  buyerId: number;
  door: OrderDoor;
  auctionId: string | null;
  conversationId: string | null;
  title: string | null;
  mainPicture: string | null;
  agreedPrice: number;
  feeAmount: number;
  payoutAmount: number;
  labelPrice: number | null;
  currency: string;
  status: OrderStatus;
  paymentId: string | null;
  shipmentId: string | null;
  agreedAt: string | null;
  paidAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  receiptConfirmedAt: string | null;
  releasedAt: string | null;
  settledAt: string | null;
  closedAt: string | null;
  disputeReason: string | null;
  disputeOpenedAt: string | null;
  lastEventId?: string | null;
  addTime?: string | null;
  updateTime?: string | null;
}
export type PaymentStatus = 'INTENT_CREATED' | 'CONFIRMED' | 'FAILED' | 'RELEASING' | 'PAID_OUT' | 'REFUNDING' | 'REFUNDED' | 'CANCELLED';
/** `announce_payment` row (`GET /api/admin/orders/{id}/payment`, 404 before the buyer started paying). */
export interface PaymentRow {
  id: string;
  orderId: string;
  announceId: string | null;
  buyerId: number | null;
  sellerId: number | null;
  provider: string;
  intentRef: string | null;
  status: PaymentStatus;
  amount: number;
  feeAmount: number | null;
  payoutAmount: number | null;
  refundAmount: number | null;
  currency: string;
  payoutRef: string | null;
  refundRef: string | null;
  failureReason: string | null;
  confirmedAt: string | null;
  releasedAt: string | null;
  refundedAt: string | null;
}
export type ShipmentStatus = 'OPEN' | 'REQUESTED' | 'DISPATCHED' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED' | 'CANCELLED';
/** `announce_shipment` row (`GET /api/admin/orders/{id}/shipment`, 404 before the seller requested one). */
export interface ShipmentRow {
  id: string;
  orderId: string;
  sellerId: number | null;
  buyerId: number | null;
  carrier: string;
  service: string | null;
  parcelSize: string | null;
  trackingNumber: string | null;
  labelUrl: string | null;
  status: ShipmentStatus;
  lastCarrierEvent: string | null;
  lastCarrierEventId: string | null;
  lastEventAt: string | null;
  lastPolledAt: string | null;
  requestedAt: string | null;
  dispatchedAt: string | null;
  deliveredAt: string | null;
}
export interface OrderFilter {
  status?: OrderStatus | '';
  seller?: number | '';
  buyer?: number | '';
  q?: string;
  page?: number;
  size?: number;
}
export type DisputeResolution = 'RELEASE' | 'REFUND';
/** `OrderController.ResolutionRequest`. */
export interface ResolutionRequest {
  resolution: DisputeResolution;
  note: string;
}

export const ordersApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listOrders: build.query<PageResponse<OrderRow>, OrderFilter>({
      query: (params) => `/api/admin/orders${queryString(params)}`,
      providesTags: [{ type: 'Order', id: 'LIST' }],
    }),
    getOrder: build.query<OrderRow, string>({
      query: (id) => `/api/admin/orders/${encodeURIComponent(id)}`,
      providesTags: (_r, _e, id) => [{ type: 'Order', id }],
    }),
    getOrderPayment: build.query<PaymentRow, string>({
      query: (id) => `/api/admin/orders/${encodeURIComponent(id)}/payment`,
      providesTags: (_r, _e, id) => [{ type: 'Order', id }],
    }),
    getOrderShipment: build.query<ShipmentRow, string>({
      query: (id) => `/api/admin/orders/${encodeURIComponent(id)}/shipment`,
      providesTags: (_r, _e, id) => [{ type: 'Order', id }],
    }),
    /** Answers the domain record (amounts in cents); the screens refetch the rows through the tags instead. */
    resolveDispute: build.mutation<unknown, { id: string; body: ResolutionRequest }>({
      query: ({ id, body }) => ({ url: `/api/admin/orders/${encodeURIComponent(id)}/resolve-dispute`, method: 'POST', body }),
      invalidatesTags: (_r, _e, { id }) => [{ type: 'Order', id }, { type: 'Order', id: 'LIST' }, 'Payout'],
    }),
  }),
});

export const { useListOrdersQuery, useGetOrderQuery, useGetOrderPaymentQuery, useGetOrderShipmentQuery, useResolveDisputeMutation } = ordersApi;

/** A 404 from the payment or shipment read means "none yet", not an error. */
export function isNotFound(error: unknown): boolean {
  return !!error && typeof error === 'object' && (error as { status?: unknown }).status === 404;
}
