import { ApiError, http } from 'app/config/http';
import {
  AuctionAnswer,
  AuctionView,
  BidRequest,
  OrderAnswer,
  OrderRow,
  PaymentRow,
  ReasonRequest,
  SellerAccountAnswer,
  SellerAccountRow,
  ShipmentAnswer,
  ShipmentRequest,
  ShipmentRow,
} from 'app/shared/model/trade';

const enc = encodeURIComponent;
/** A 404 on the payment or shipment of an order means "none yet", not an error. */
const noneOn404 = <T>(e: unknown): T | null => {
  if (e instanceof ApiError && e.status === 404) return null;
  throw e;
};

/**
 * The order module's member routes through the customer edge (doc/frontend-edges.md §3, §16): the auction,
 * order, payment and shipping contexts. The member comes from the token (X-User-Id / X-User-Type=MEMBER at
 * the edge). Reads answer the read-model rows; commands answer the aggregate, after which the screens refetch.
 */
export const tradeApi = {
  // auction
  auction: (announceId: string) => http.get<AuctionView>(`/api/auctions/${enc(announceId)}`).then(r => r.data),
  bid: (announceId: string, body: BidRequest) => http.post<AuctionAnswer>(`/api/auctions/${enc(announceId)}/bids`, body).then(r => r.data),
  snag: (announceId: string) => http.post<AuctionAnswer>(`/api/auctions/${enc(announceId)}/snag`).then(r => r.data),
  retractBid: (announceId: string, bidId: string) => http.delete<AuctionAnswer>(`/api/auctions/${enc(announceId)}/bids/${enc(bidId)}`).then(r => r.data),
  // order
  requestPurchase: (announceId: string) => http.post<OrderAnswer>('/api/orders', { announceId }).then(r => r.data),
  orders: (role: 'buyer' | 'seller', page = 0, size = 50) => http.get<OrderRow[]>(`/api/orders?role=${role}&page=${page}&size=${size}`).then(r => r.data),
  order: (id: string) => http.get<OrderRow>(`/api/orders/${enc(id)}`).then(r => r.data),
  accept: (id: string) => http.post<OrderAnswer>(`/api/orders/${enc(id)}/accept`).then(r => r.data),
  decline: (id: string, body: ReasonRequest = {}) => http.post<OrderAnswer>(`/api/orders/${enc(id)}/decline`, body).then(r => r.data),
  cancel: (id: string, body: ReasonRequest = {}) => http.post<OrderAnswer>(`/api/orders/${enc(id)}/cancel`, body).then(r => r.data),
  confirmReceipt: (id: string) => http.post<OrderAnswer>(`/api/orders/${enc(id)}/confirm-receipt`).then(r => r.data),
  dispute: (id: string, reason: string) => http.post<OrderAnswer>(`/api/orders/${enc(id)}/dispute`, { reason }).then(r => r.data),
  // payment and shipping
  payment: (id: string) =>
    http
      .get<PaymentRow>(`/api/orders/${enc(id)}/payment`)
      .then(r => r.data as PaymentRow | null)
      .catch(noneOn404<PaymentRow>),
  shipment: (id: string) =>
    http
      .get<ShipmentRow>(`/api/orders/${enc(id)}/shipment`)
      .then(r => r.data as ShipmentRow | null)
      .catch(noneOn404<ShipmentRow>),
  requestShipment: (id: string, body: ShipmentRequest) => http.post<ShipmentAnswer>(`/api/orders/${enc(id)}/shipment`, body).then(r => r.data),
  // seller payouts
  sellerAccount: () =>
    http
      .get<SellerAccountRow>('/api/sellers/me/account')
      .then(r => r.data as SellerAccountRow | null)
      .catch(noneOn404<SellerAccountRow>),
  onboard: () => http.post<SellerAccountAnswer>('/api/sellers/me/account').then(r => r.data),
};
