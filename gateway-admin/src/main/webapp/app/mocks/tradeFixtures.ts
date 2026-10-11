import type { AuctionRow, BidRow } from 'app/shared/api/auctionsApi';
import type { OrderRow, PaymentRow, ShipmentRow } from 'app/shared/api/ordersApi';
import type { PayoutOverview, PayoutRow, PayoutStatus, PayoutTotals } from 'app/shared/api/payoutsApi';
import type { PageResponse } from 'app/shared/types';

/**
 * Fixtures in the exact shapes the order module answers (doc/frontend-edges.md §12): read-model rows with decimal
 * amounts and ISO local date-times, and the derived payout lines. Used by the MSW handlers (`features.mocks`) and by
 * the page tests, so a screen renders the same against the fixtures and against the backend.
 */
const now = Date.now();
/** ISO local date-time without zone, as Jackson writes a LocalDateTime. */
export const iso = (offsetMinutes: number) => new Date(now + offsetMinutes * 60_000).toISOString().replace(/Z$/, '');

export function pageOf<T>(items: T[], url: URL): PageResponse<T> {
  const p = Math.max(1, Number(url.searchParams.get('page') ?? 1) || 1);
  const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? 20) || 20));
  return { items: items.slice((p - 1) * size, p * size), page: p, size, total: items.length };
}

// ---- auction context: announce_auction / announce_bid ----
export const bids: BidRow[] = [
  { id: 'bid-1001', auctionId: 'auc-1', announceId: 'ann-auction-1', bidderId: 42, amount: 520.0, maxAmount: null, bidTime: iso(-180), status: 'OUTBID', cancelReason: null, cancelledBy: null },
  { id: 'bid-1002', auctionId: 'auc-1', announceId: 'ann-auction-1', bidderId: 57, amount: 550.0, maxAmount: 700.0, bidTime: iso(-120), status: 'OUTBID', cancelReason: null, cancelledBy: null },
  { id: 'bid-1003', auctionId: 'auc-1', announceId: 'ann-auction-1', bidderId: 42, amount: 580.0, maxAmount: null, bidTime: iso(-30), status: 'ACTIVE', cancelReason: null, cancelledBy: null },
  { id: 'bid-2001', auctionId: 'auc-2', announceId: 'ann-auction-2', bidderId: 61, amount: 150.0, maxAmount: null, bidTime: iso(-2880), status: 'WON', cancelReason: null, cancelledBy: null },
  { id: 'bid-2000', auctionId: 'auc-2', announceId: 'ann-auction-2', bidderId: 99, amount: 120.0, maxAmount: null, bidTime: iso(-3000), status: 'CANCELLED', cancelReason: 'Shill bidding', cancelledBy: 1 },
];
export const auctions: AuctionRow[] = [
  {
    id: 'auc-1', announceId: 'ann-auction-1', sellerId: 7, title: 'Vintage Ercol armchair', mainPicture: null, currency: 'DKK',
    initialPrice: 500.0, reservePrice: 550.0, snagItPrice: null, bidIncrement: 10.0, currentPrice: 580.0, bidCount: 3,
    startTime: iso(-24 * 60), endTime: iso(6 * 60), autoExtendSec: 120, status: 'OPEN', winningBidId: null, winnerId: null, closedTime: null,
  },
  {
    id: 'auc-2', announceId: 'ann-auction-2', sellerId: 12, title: 'Canon EOS 500D body', mainPicture: null, currency: 'SEK',
    initialPrice: 100.0, reservePrice: null, snagItPrice: 200.0, bidIncrement: 5.0, currentPrice: 150.0, bidCount: 1,
    startTime: iso(-5 * 24 * 60), endTime: iso(-2 * 24 * 60), autoExtendSec: null, status: 'CLOSED_WON', winningBidId: 'bid-2001', winnerId: 61, closedTime: iso(-2 * 24 * 60),
  },
  {
    id: 'auc-3', announceId: 'ann-auction-3', sellerId: 7, title: 'Box of LEGO Technic (mixed)', mainPicture: null, currency: 'DKK',
    initialPrice: 300.0, reservePrice: 400.0, snagItPrice: null, bidIncrement: 10.0, currentPrice: 300.0, bidCount: 0,
    startTime: iso(-10 * 24 * 60), endTime: iso(-7 * 24 * 60), autoExtendSec: 120, status: 'CLOSED_UNSOLD', winningBidId: null, winnerId: null, closedTime: iso(-7 * 24 * 60),
  },
  {
    id: 'auc-4', announceId: 'ann-auction-4', sellerId: 19, title: 'Fender Stratocaster MIM', mainPicture: null, currency: 'DKK',
    initialPrice: 3000.0, reservePrice: null, snagItPrice: 4500.0, bidIncrement: 50.0, currentPrice: 4500.0, bidCount: 0,
    startTime: iso(-3 * 24 * 60), endTime: iso(4 * 24 * 60), autoExtendSec: 120, status: 'CLOSED_SNAGGED', winningBidId: null, winnerId: 88, closedTime: iso(-60),
  },
];

// ---- order context: announce_order ----
export const orders: OrderRow[] = [
  {
    id: 'ord-3001', announceId: 'ann-fixed-9', sellerId: 7, buyerId: 42, door: 'FIXED_PRICE', auctionId: null, conversationId: null, title: 'Brompton M6L 2019', mainPicture: null,
    agreedPrice: 9500.0, feeAmount: 570.0, payoutAmount: 8930.0, labelPrice: 49.0, currency: 'DKK', status: 'DISPUTED', paymentId: 'pay-3001', shipmentId: 'shp-3001',
    agreedAt: iso(-6 * 24 * 60), paidAt: iso(-6 * 24 * 60 + 15), shippedAt: iso(-5 * 24 * 60), deliveredAt: iso(-3 * 24 * 60), receiptConfirmedAt: null,
    releasedAt: null, settledAt: null, closedAt: null, disputeReason: 'Frame has a crack not shown in the pictures', disputeOpenedAt: iso(-2 * 24 * 60),
  },
  {
    id: 'ord-3002', announceId: 'ann-auction-2', sellerId: 12, buyerId: 61, door: 'AUCTION', auctionId: 'auc-2', conversationId: null, title: 'Canon EOS 500D body', mainPicture: null,
    agreedPrice: 150.0, feeAmount: 14.0, payoutAmount: 136.0, labelPrice: null, currency: 'SEK', status: 'SHIPPED', paymentId: 'pay-3002', shipmentId: 'shp-3002',
    agreedAt: iso(-2 * 24 * 60), paidAt: iso(-2 * 24 * 60 + 40), shippedAt: iso(-1 * 24 * 60), deliveredAt: null, receiptConfirmedAt: null,
    releasedAt: null, settledAt: null, closedAt: null, disputeReason: null, disputeOpenedAt: null,
  },
  {
    id: 'ord-3003', announceId: 'ann-offer-4', sellerId: 19, buyerId: 42, door: 'OFFER', auctionId: null, conversationId: 'conv-ann-offer-4-42', title: 'IKEA Poäng + footstool', mainPicture: null,
    agreedPrice: 400.0, feeAmount: 34.0, payoutAmount: 366.0, labelPrice: null, currency: 'DKK', status: 'SETTLED', paymentId: 'pay-3003', shipmentId: 'shp-3003',
    agreedAt: iso(-20 * 24 * 60), paidAt: iso(-20 * 24 * 60 + 5), shippedAt: iso(-19 * 24 * 60), deliveredAt: iso(-17 * 24 * 60), receiptConfirmedAt: iso(-16 * 24 * 60),
    releasedAt: iso(-16 * 24 * 60), settledAt: iso(-15 * 24 * 60), closedAt: iso(-15 * 24 * 60), disputeReason: null, disputeOpenedAt: null,
  },
  {
    id: 'ord-3004', announceId: 'ann-fixed-11', sellerId: 7, buyerId: 88, door: 'FIXED_PRICE', auctionId: null, conversationId: null, title: 'Bosch cordless drill', mainPicture: null,
    agreedPrice: 600.0, feeAmount: 46.0, payoutAmount: 554.0, labelPrice: null, currency: 'DKK', status: 'PAYMENT_PENDING', paymentId: 'pay-3004', shipmentId: null,
    agreedAt: iso(-90), paidAt: null, shippedAt: null, deliveredAt: null, receiptConfirmedAt: null,
    releasedAt: null, settledAt: null, closedAt: null, disputeReason: null, disputeOpenedAt: null,
  },
  {
    id: 'ord-3005', announceId: 'ann-fixed-12', sellerId: 23, buyerId: 42, door: 'FIXED_PRICE', auctionId: null, conversationId: null, title: 'Kids bike 20"', mainPicture: null,
    agreedPrice: 800.0, feeAmount: 58.0, payoutAmount: 742.0, labelPrice: null, currency: 'SEK', status: 'REFUNDED', paymentId: 'pay-3005', shipmentId: null,
    agreedAt: iso(-12 * 24 * 60), paidAt: iso(-12 * 24 * 60 + 10), shippedAt: null, deliveredAt: null, receiptConfirmedAt: null,
    releasedAt: null, settledAt: null, closedAt: iso(-7 * 24 * 60), disputeReason: null, disputeOpenedAt: null,
  },
  {
    id: 'ord-3006', announceId: 'ann-fixed-13', sellerId: 23, buyerId: 57, door: 'FIXED_PRICE', auctionId: null, conversationId: null, title: 'Standing desk 120 cm', mainPicture: null,
    agreedPrice: 1200.0, feeAmount: 86.0, payoutAmount: 1114.0, labelPrice: null, currency: 'DKK', status: 'DECLINED', paymentId: null, shipmentId: null,
    agreedAt: null, paidAt: null, shippedAt: null, deliveredAt: null, receiptConfirmedAt: null,
    releasedAt: null, settledAt: null, closedAt: iso(-30), disputeReason: null, disputeOpenedAt: null, addTime: iso(-45),
  },
];

// ---- payment context: announce_payment ----
export const payments: PaymentRow[] = [
  { id: 'pay-3001', orderId: 'ord-3001', announceId: 'ann-fixed-9', buyerId: 42, sellerId: 7, provider: 'FAKE', intentRef: 'pi_fake_3001', status: 'CONFIRMED', amount: 9500.0, feeAmount: 570.0, payoutAmount: 8930.0, refundAmount: null, currency: 'DKK', payoutRef: null, refundRef: null, failureReason: null, confirmedAt: iso(-6 * 24 * 60 + 15), releasedAt: null, refundedAt: null },
  { id: 'pay-3002', orderId: 'ord-3002', announceId: 'ann-auction-2', buyerId: 61, sellerId: 12, provider: 'FAKE', intentRef: 'pi_fake_3002', status: 'CONFIRMED', amount: 150.0, feeAmount: 14.0, payoutAmount: 136.0, refundAmount: null, currency: 'SEK', payoutRef: null, refundRef: null, failureReason: null, confirmedAt: iso(-2 * 24 * 60 + 40), releasedAt: null, refundedAt: null },
  { id: 'pay-3003', orderId: 'ord-3003', announceId: 'ann-offer-4', buyerId: 42, sellerId: 19, provider: 'FAKE', intentRef: 'pi_fake_3003', status: 'PAID_OUT', amount: 400.0, feeAmount: 34.0, payoutAmount: 366.0, refundAmount: null, currency: 'DKK', payoutRef: 'po_fake_3003', refundRef: null, failureReason: null, confirmedAt: iso(-20 * 24 * 60 + 5), releasedAt: iso(-15 * 24 * 60), refundedAt: null },
  { id: 'pay-3004', orderId: 'ord-3004', announceId: 'ann-fixed-11', buyerId: 88, sellerId: 7, provider: 'FAKE', intentRef: 'pi_fake_3004', status: 'INTENT_CREATED', amount: 600.0, feeAmount: 46.0, payoutAmount: 554.0, refundAmount: null, currency: 'DKK', payoutRef: null, refundRef: null, failureReason: null, confirmedAt: null, releasedAt: null, refundedAt: null },
  { id: 'pay-3005', orderId: 'ord-3005', announceId: 'ann-fixed-12', buyerId: 42, sellerId: 23, provider: 'FAKE', intentRef: 'pi_fake_3005', status: 'REFUNDED', amount: 800.0, feeAmount: 58.0, payoutAmount: 742.0, refundAmount: 800.0, currency: 'SEK', payoutRef: null, refundRef: 're_fake_3005', failureReason: null, confirmedAt: iso(-12 * 24 * 60 + 10), releasedAt: null, refundedAt: iso(-7 * 24 * 60) },
];

// ---- shipping context: announce_shipment ----
export const shipments: ShipmentRow[] = [
  { id: 'shp-3001', orderId: 'ord-3001', sellerId: 7, buyerId: 42, carrier: 'POSTNORD', service: 'MyPack Home', parcelSize: 'M', trackingNumber: 'PN123456789DK', labelUrl: 'https://example.invalid/label/3001.pdf', status: 'DELIVERED', lastCarrierEvent: 'Delivered to recipient', lastCarrierEventId: 'ev-9', lastEventAt: iso(-3 * 24 * 60), lastPolledAt: iso(-3 * 24 * 60), requestedAt: iso(-5 * 24 * 60 - 60), dispatchedAt: iso(-5 * 24 * 60), deliveredAt: iso(-3 * 24 * 60) },
  { id: 'shp-3002', orderId: 'ord-3002', sellerId: 12, buyerId: 61, carrier: 'POSTNORD', service: 'MyPack Collect', parcelSize: 'S', trackingNumber: 'PN987654321SE', labelUrl: null, status: 'IN_TRANSIT', lastCarrierEvent: 'Arrived at sorting hub', lastCarrierEventId: 'ev-4', lastEventAt: iso(-6 * 60), lastPolledAt: iso(-10), requestedAt: iso(-1 * 24 * 60 - 30), dispatchedAt: iso(-1 * 24 * 60), deliveredAt: null },
  { id: 'shp-3003', orderId: 'ord-3003', sellerId: 19, buyerId: 42, carrier: 'FAKE', service: 'standard', parcelSize: 'L', trackingNumber: 'FK-3003', labelUrl: null, status: 'DELIVERED', lastCarrierEvent: 'Delivered', lastCarrierEventId: 'ev-2', lastEventAt: iso(-17 * 24 * 60), lastPolledAt: iso(-17 * 24 * 60), requestedAt: iso(-19 * 24 * 60 - 30), dispatchedAt: iso(-19 * 24 * 60), deliveredAt: iso(-17 * 24 * 60) },
];

// ---- payouts: derived from the order rows the way Payouts.row / Payouts.totals do (dev windows: 3 d after delivery) ----
const PAYOUT_OF: Partial<Record<OrderRow['status'], PayoutStatus>> = {
  PAID: 'PENDING_RELEASE', SHIPPED: 'PENDING_RELEASE', DELIVERED: 'PENDING_RELEASE', DISPUTED: 'BLOCKED_DISPUTE',
  RELEASING: 'RELEASING', SETTLED: 'SENT', REFUNDING: 'REFUNDED', REFUNDED: 'REFUNDED',
};
const GATES: Record<number, { accountStatus: string; payoutsEnabled: boolean }> = {
  7: { accountStatus: 'VERIFIED', payoutsEnabled: true }, 12: { accountStatus: 'PENDING', payoutsEnabled: false },
  19: { accountStatus: 'VERIFIED', payoutsEnabled: true }, 23: { accountStatus: 'VERIFIED', payoutsEnabled: true },
};
const plusDays = (at: string, days: number) => new Date(new Date(`${at}Z`).getTime() + days * 86_400_000).toISOString().replace(/Z$/, '');

export function payoutRows(): PayoutRow[] {
  return orders
    .filter((o) => PAYOUT_OF[o.status])
    .map((o) => {
      const gate = GATES[o.sellerId];
      const status = PAYOUT_OF[o.status] as PayoutStatus;
      const releaseDueAt = o.status === 'DELIVERED' && o.deliveredAt ? plusDays(o.deliveredAt, 3) : o.status === 'SHIPPED' && o.shippedAt ? plusDays(o.shippedAt, 14) : null;
      return {
        orderId: o.id, announceId: o.announceId, title: o.title, sellerId: o.sellerId, buyerId: o.buyerId,
        sellerAccountStatus: gate?.accountStatus ?? null, payoutsEnabled: gate?.payoutsEnabled ?? false,
        agreedPrice: o.agreedPrice, feeAmount: o.feeAmount, payoutAmount: o.payoutAmount, currency: o.currency,
        status, orderStatus: o.status, releaseDueAt, paidAt: o.paidAt, releasedAt: o.releasedAt, settledAt: o.settledAt,
        disputeReason: o.disputeReason, disputeOpenedAt: o.disputeOpenedAt,
      };
    })
    .sort((a, b) => (b.paidAt ?? '').localeCompare(a.paidAt ?? ''));
}

export function payoutTotals(rows: PayoutRow[]): PayoutTotals {
  const sum = (s: PayoutStatus, pick: (r: PayoutRow) => number = (r) => r.payoutAmount) => rows.filter((r) => r.status === s).reduce((acc, r) => acc + pick(r), 0);
  const count = (s: PayoutStatus) => rows.filter((r) => r.status === s).length;
  return {
    pendingAmount: sum('PENDING_RELEASE'), pendingOrders: count('PENDING_RELEASE'),
    blockedAmount: sum('BLOCKED_DISPUTE'), blockedOrders: count('BLOCKED_DISPUTE'),
    releasingAmount: sum('RELEASING'), releasingOrders: count('RELEASING'),
    sentAmount: sum('SENT'), sentOrders: count('SENT'),
    refundedAmount: sum('REFUNDED'), refundedOrders: count('REFUNDED'),
    feesCollected: sum('SENT', (r) => r.feeAmount),
    feesPending: sum('PENDING_RELEASE', (r) => r.feeAmount) + sum('BLOCKED_DISPUTE', (r) => r.feeAmount) + sum('RELEASING', (r) => r.feeAmount),
    currency: 'DKK',
  };
}

export function payoutOverview(url: URL): PayoutOverview {
  const all = payoutRows();
  const status = url.searchParams.get('status');
  const seller = url.searchParams.get('seller');
  const rows = all.filter((r) => (!status || r.status === status) && (!seller || String(r.sellerId) === seller));
  return { totals: payoutTotals(all), page: pageOf(rows, url) };
}
