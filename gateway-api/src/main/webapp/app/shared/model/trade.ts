// The order module's shapes as built (doc/frontend-edges.md §12, §16): the read-model rows its member routes
// return (db-reactive `announce_auction`, `announce_bid`, `announce_order`, `announce_payment`, `announce_shipment`,
// `announce_seller_account`; decimal amounts, ISO local date-times) and the request records of its controllers
// (amounts in cents). The commands answer the aggregate; the screens use a few of its fields and refetch the rows.
export type AuctionStatus = 'OPEN' | 'CLOSED_WON' | 'CLOSED_UNSOLD' | 'CLOSED_SNAGGED' | 'CANCELLED';
/** `announce_auction` row. */
export interface AuctionRow {
  id: string;
  announceId: string;
  sellerId: number;
  title: string | null;
  mainPicture: string | null;
  currency: string;
  initialPrice: number;
  reservePrice: number | null;
  snagItPrice: number | null;
  bidIncrement: number;
  currentPrice: number;
  bidCount: number;
  startTime: string;
  endTime: string;
  autoExtendSec: number | null;
  status: AuctionStatus;
  winningBidId: string | null;
  winnerId: number | null;
  closedTime: string | null;
}
export type BidStatus = 'ACTIVE' | 'OUTBID' | 'WON' | 'RETRACTED' | 'CANCELLED';
/** `announce_bid` row. */
export interface BidRow {
  id: string;
  auctionId: string;
  announceId: string;
  bidderId: number;
  amount: number;
  maxAmount: number | null;
  bidTime: string;
  status: BidStatus;
  cancelReason: string | null;
  cancelledBy: number | null;
}
/** `GET /api/auctions/{announceId}`: the row plus every bid. 404 while no auction is open for the announce. */
export interface AuctionView {
  auction: AuctionRow;
  bids: BidRow[];
}
/** `AuctionController.BidRequest`. */
export interface BidRequest {
  amountCents: number;
  maxAmountCents?: number | null;
}
/** `ReasonRequest` of the auction and order controllers (optional body). */
export interface ReasonRequest {
  reason?: string | null;
}
/** What bid, snag and retract answer: the aggregate; only these fields are read. */
export interface AuctionAnswer {
  announceId: string;
  status: AuctionStatus;
  currentPriceCents: number;
  bidCount?: number;
}

export type OrderStatus =
  'PROPOSED' | 'AGREED' | 'PAYMENT_PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'RELEASING' | 'SETTLED' | 'DISPUTED' | 'REFUNDING' | 'REFUNDED' | 'CANCELLED' | 'DECLINED';
export const ORDER_STATUSES: OrderStatus[] = [
  'PROPOSED',
  'AGREED',
  'PAYMENT_PENDING',
  'PAID',
  'SHIPPED',
  'DELIVERED',
  'RELEASING',
  'SETTLED',
  'DISPUTED',
  'REFUNDING',
  'REFUNDED',
  'CANCELLED',
  'DECLINED',
];
export type OrderDoor = 'AUCTION' | 'FIXED_PRICE' | 'OFFER';
/** `announce_order` row (`GET /api/orders`, `GET /api/orders/{id}`; the buyer's or the seller's only). */
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
  addTime?: string | null;
  updateTime?: string | null;
}
/** `OrderController.PurchaseRequest`. */
export interface PurchaseRequest {
  announceId: string;
}
/** What the order commands answer: the aggregate; only these fields are read. */
export interface OrderAnswer {
  id: string;
  status: OrderStatus;
}

export type PaymentStatus = 'INTENT_CREATED' | 'CONFIRMED' | 'FAILED' | 'RELEASING' | 'PAID_OUT' | 'REFUNDING' | 'REFUNDED' | 'CANCELLED';
/** `announce_payment` row (`GET /api/orders/{id}/payment`; 404 before the deal is agreed). The client secret is the provider's. */
export interface PaymentRow {
  id: string;
  orderId: string;
  announceId: string | null;
  buyerId: number | null;
  sellerId: number | null;
  provider: string;
  intentRef: string | null;
  clientSecret: string | null;
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
/** `announce_shipment` row (`GET /api/orders/{id}/shipment`; 404 before the order is paid). */
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
/** `ShipmentController.ShipmentRequest`: the seller books the carrier. */
export interface ShipmentRequest {
  service: string;
  parcelSize: string;
  fromAddress: string;
  toAddress: string;
}
export interface ShipmentAnswer {
  orderId: string;
  status: ShipmentStatus;
  trackingNumber?: string | null;
}

/** `announce_seller_account` row (`GET /api/sellers/me/account`; 404 until the seller onboarded). */
export interface SellerAccountRow {
  id: string;
  userId: number;
  provider: string;
  accountRef: string | null;
  status: string;
  payoutsEnabled: boolean;
  onboardingUrl: string | null;
  addTime?: string | null;
  updateTime?: string | null;
}
/** `POST /api/sellers/me/account` answers the aggregate. */
export interface SellerAccountAnswer {
  userId: number;
  provider: string;
  accountRef: string | null;
  onboardingUrl: string | null;
  status: string;
  payoutsEnabled: boolean;
}

/** `Auction.minimumNextBidCents` in decimals: the current price plus the increment once there is a bid, the start price before. */
export const minimumNextBid = (a: Pick<AuctionRow, 'bidCount' | 'currentPrice' | 'bidIncrement' | 'initialPrice'>): number =>
  a.bidCount > 0 ? Math.round((a.currentPrice + a.bidIncrement) * 100) / 100 : a.initialPrice;
/** Open and inside its window. */
export const isAuctionOpen = (a: Pick<AuctionRow, 'status' | 'endTime' | 'startTime'>, now: Date = new Date()): boolean =>
  a.status === 'OPEN' && new Date(a.startTime).getTime() <= now.getTime() && new Date(a.endTime).getTime() > now.getTime();
/** The member's highest standing bid, if any. */
export const myStandingBid = (bids: BidRow[], me: number | undefined): BidRow | undefined =>
  me == null
    ? undefined
    : [...bids].filter(b => b.bidderId === me && (b.status === 'ACTIVE' || b.status === 'OUTBID' || b.status === 'WON')).sort((a, b) => b.amount - a.amount)[0];

export const isBeforePayment = (s: OrderStatus): boolean => s === 'PROPOSED' || s === 'AGREED' || s === 'PAYMENT_PENDING';
export const isAwaitingPayment = (s: OrderStatus): boolean => s === 'AGREED' || s === 'PAYMENT_PENDING';
export const canConfirmReceipt = (s: OrderStatus): boolean => s === 'SHIPPED' || s === 'DELIVERED';
export const canDispute = (s: OrderStatus): boolean => s === 'PAID' || s === 'SHIPPED' || s === 'DELIVERED';
export const isClosed = (s: OrderStatus): boolean => s === 'SETTLED' || s === 'REFUNDED' || s === 'CANCELLED' || s === 'DECLINED';
