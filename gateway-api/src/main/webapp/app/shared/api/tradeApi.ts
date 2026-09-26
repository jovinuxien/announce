import { http } from 'app/config/http';
import { AuctionView, DisputeRequest, OrderView, ShipmentView } from 'app/shared/model/trade';

// doc/order-context.md §8, owner header injected by the edge
export const tradeApi = {
  auction: (announceId: string) => http.get<AuctionView>(`/api/auctions/${encodeURIComponent(announceId)}`).then(r => r.data),
  bid: (announceId: string, amountCents: number) =>
    http.post<AuctionView>(`/api/auctions/${encodeURIComponent(announceId)}/bids`, { amountCents }).then(r => r.data),
  snag: (announceId: string) => http.post<AuctionView>(`/api/auctions/${encodeURIComponent(announceId)}/snag`).then(r => r.data),
  requestPurchase: (announceId: string) => http.post<OrderView>('/api/orders', { announceId }).then(r => r.data),
  orders: (role: 'buyer' | 'seller') => http.get<OrderView[]>(`/api/orders?role=${role}`).then(r => r.data),
  order: (id: string) => http.get<OrderView>(`/api/orders/${encodeURIComponent(id)}`).then(r => r.data),
  accept: (id: string) => http.post<OrderView>(`/api/orders/${encodeURIComponent(id)}/accept`).then(r => r.data),
  decline: (id: string) => http.post<OrderView>(`/api/orders/${encodeURIComponent(id)}/decline`).then(r => r.data),
  cancel: (id: string) => http.post<OrderView>(`/api/orders/${encodeURIComponent(id)}/cancel`).then(r => r.data),
  confirmReceipt: (id: string) => http.post<OrderView>(`/api/orders/${encodeURIComponent(id)}/confirm-receipt`).then(r => r.data),
  dispute: (id: string, body: DisputeRequest) => http.post<OrderView>(`/api/orders/${encodeURIComponent(id)}/dispute`, body).then(r => r.data),
  requestShipment: (id: string) => http.post<ShipmentView>(`/api/orders/${encodeURIComponent(id)}/shipment`).then(r => r.data),
  shipment: (id: string) => http.get<ShipmentView>(`/api/orders/${encodeURIComponent(id)}/shipment`).then(r => r.data),
  /** The fake payment provider of the order module: confirms the payment of an order (development only). */
  fakePay: (orderId: string) => http.post<void>('/api/payments/webhook/fake', { type: 'confirmed', orderId }).then(() => undefined),
};
