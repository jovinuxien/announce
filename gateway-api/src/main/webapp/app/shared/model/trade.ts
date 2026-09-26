// Phase 2 contracts (doc/order-context.md §8). The order module is not merged yet; these shapes back the MSW mocks
// in app/mocks and will be aligned with order-api when it lands. Amounts are integer cents like the public events.
export type AuctionStatus = 'OPEN' | 'CLOSED' | 'CANCELLED';
export interface AuctionView {
  announceId: string;
  status: AuctionStatus;
  currency: string;
  startPriceCents: number;
  currentBidCents?: number | null;
  minimumNextBidCents: number;
  bidCount: number;
  endsAt: string;
  snagPriceCents?: number | null;
  highestBidderId?: number | null;
  myHighestBidCents?: number | null;
}
export interface BidRequest { amountCents: number }

export type OrderStatus =
  | 'REQUESTED' | 'AGREED' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'SETTLED' | 'DISPUTED' | 'CANCELLED' | 'REFUNDED' | 'EXPIRED';
export interface OrderTimelineEntry { at: string; status: OrderStatus; note?: string }
export interface OrderView {
  id: string;
  announceId: string;
  announceTitle: string;
  buyerId: number;
  sellerId: number;
  status: OrderStatus;
  amountCents: number;
  feeCents: number;
  currency: string;
  createdAt: string;
  payBy?: string | null;
  shipBy?: string | null;
  paymentClientSecret?: string | null;
  timeline: OrderTimelineEntry[];
}
export interface RequestPurchaseRequest { announceId: string }
export type ShipmentStatus = 'REQUESTED' | 'DISPATCHED' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED';
export interface ShipmentView { orderId: string; carrier: string; trackingNumber?: string | null; status: ShipmentStatus; updatedAt: string }
export interface DisputeRequest { reason: string }
