import { HttpResponse, delay, http } from 'msw';
import type { AuctionDetail, AuctionSummary, Bid } from 'app/shared/api/auctionsApi';
import type { OrderDetail, OrderStatus } from 'app/shared/api/ordersApi';
import type { PayoutOverview, PayoutRow } from 'app/shared/api/payoutsApi';
import type { Bulletin, ConversationSummary } from 'app/shared/api/supportApi';
import type { PageResponse } from 'app/shared/types';

/**
 * MSW handlers for the phase-2 screens (features.trade / features.chat). Every shape follows doc/order-context.md:
 *  - auctions and bids: §4 `auction` (OpenAuction, PlaceBid, CancelBid by staff) and §5 announce_auction / announce_bid;
 *    cancel endpoint from §8 (`POST /api/admin/bids/{id}/cancel`).
 *  - orders: §4 `order` state machine, §5 announce_order / announce_payment / announce_shipment, §8
 *    (`POST /api/admin/orders/{id}/resolve-dispute` with RELEASE or REFUND).
 *  - payouts: §3 release rule and §4 `payment` (PayoutRequested / PayoutSent); route from doc/frontend-edges.md §4.
 *  - support queue and bulletins: §9 (support conversation WAITING until staff joins; bulletins are announce_bulletin rows).
 * Only the admin paths below are intercepted; everything else (auth, phase-1 API) passes through to the edge.
 */

const now = Date.now();
const iso = (offsetMinutes: number) => new Date(now + offsetMinutes * 60_000).toISOString();

function page<T>(items: T[], url: URL): PageResponse<T> {
  const p = Math.max(1, Number(url.searchParams.get('page') ?? 1) || 1);
  const size = Math.max(1, Number(url.searchParams.get('size') ?? 20) || 20);
  return { items: items.slice((p - 1) * size, p * size), page: p, size, total: items.length };
}

function problem(status: number, detail: string, code?: string) {
  return HttpResponse.json({ type: 'about:blank', title: 'Error', status, detail, code }, { status, headers: { 'content-type': 'application/problem+json' } });
}

// ---- auctions (§4 auction, §5 announce_auction / announce_bid) ----
const bids: Bid[] = [
  { id: 'bid-1001', announceId: 'ann-auction-1', bidderId: 42, amount: 52000, maxAmount: null, placedAt: iso(-180), status: 'OUTBID', cancelReason: null },
  { id: 'bid-1002', announceId: 'ann-auction-1', bidderId: 57, amount: 55000, maxAmount: 70000, placedAt: iso(-120), status: 'OUTBID', cancelReason: null },
  { id: 'bid-1003', announceId: 'ann-auction-1', bidderId: 42, amount: 58000, maxAmount: null, placedAt: iso(-30), status: 'ACTIVE', cancelReason: null },
  { id: 'bid-2001', announceId: 'ann-auction-2', bidderId: 61, amount: 15000, maxAmount: null, placedAt: iso(-2880), status: 'WON', cancelReason: null },
];
const auctions: AuctionSummary[] = [
  {
    announceId: 'ann-auction-1', announceTitle: 'Vintage Ercol armchair', sellerId: 7, initialPrice: 50000, reservePrice: 55000, snagItPrice: null, increment: 1000,
    currentPrice: 58000, currency: 'DKK', bidCount: 3, startTime: iso(-24 * 60), endTime: iso(6 * 60), autoExtend: true, status: 'OPEN', winnerId: null, winningBidId: null, orderId: null,
  },
  {
    announceId: 'ann-auction-2', announceTitle: 'Canon EOS 500D body', sellerId: 12, initialPrice: 10000, reservePrice: null, snagItPrice: 20000, increment: 500,
    currentPrice: 15000, currency: 'SEK', bidCount: 1, startTime: iso(-5 * 24 * 60), endTime: iso(-2 * 24 * 60), autoExtend: false, status: 'CLOSED_WON', winnerId: 61, winningBidId: 'bid-2001', orderId: 'ord-3002',
  },
  {
    announceId: 'ann-auction-3', announceTitle: 'Box of LEGO Technic (mixed)', sellerId: 7, initialPrice: 30000, reservePrice: 40000, snagItPrice: null, increment: 1000,
    currentPrice: 30000, currency: 'DKK', bidCount: 0, startTime: iso(-10 * 24 * 60), endTime: iso(-7 * 24 * 60), autoExtend: true, status: 'CLOSED_UNSOLD', winnerId: null, winningBidId: null, orderId: null,
  },
];

// ---- orders (§4 order states, §5 read models) ----
const orders: OrderDetail[] = [
  {
    id: 'ord-3001', announceId: 'ann-fixed-9', announceTitle: 'Brompton M6L 2019', sellerId: 7, buyerId: 42, door: 'FIXED_PRICE', agreedPrice: 950000, feeAmount: 57000, payoutAmount: 893000,
    labelPrice: 4900, currency: 'DKK', status: 'DISPUTED', agreedAt: iso(-6 * 24 * 60), paidAt: iso(-6 * 24 * 60 + 15), shippedAt: iso(-5 * 24 * 60), deliveredAt: iso(-3 * 24 * 60),
    releasedAt: null, settledAt: null, disputeReason: 'Frame has a crack not shown in the pictures',
    payment: { id: 'pay-3001', provider: 'FAKE', intentRef: 'pi_fake_3001', status: 'CONFIRMED', amount: 950000, feeAmount: 57000, payoutRef: null, refundRef: null, confirmedAt: iso(-6 * 24 * 60 + 15), releasedAt: null, refundedAt: null },
    shipment: { id: 'shp-3001', orderId: 'ord-3001', carrier: 'POSTNORD', service: 'MyPack Home', trackingNumber: 'PN123456789DK', labelUrl: 'https://example.invalid/label/3001.pdf', status: 'DELIVERED', lastCarrierEvent: 'Delivered to recipient', lastEventAt: iso(-3 * 24 * 60), deliveredAt: iso(-3 * 24 * 60) } as OrderDetail['shipment'],
    timeline: [
      { at: iso(-6 * 24 * 60), type: 'OrderAgreed', detail: 'Fixed price accepted by seller' },
      { at: iso(-6 * 24 * 60 + 15), type: 'OrderPaid', detail: 'PaymentConfirmed pi_fake_3001' },
      { at: iso(-5 * 24 * 60), type: 'OrderShipped', detail: 'ShipmentDispatched PN123456789DK' },
      { at: iso(-3 * 24 * 60), type: 'OrderDelivered', detail: 'ShipmentDelivered' },
      { at: iso(-2 * 24 * 60), type: 'DisputeOpened', detail: 'Frame has a crack not shown in the pictures' },
    ],
  },
  {
    id: 'ord-3002', announceId: 'ann-auction-2', announceTitle: 'Canon EOS 500D body', sellerId: 12, buyerId: 61, door: 'AUCTION', agreedPrice: 15000, feeAmount: 1400, payoutAmount: 13600,
    labelPrice: null, currency: 'SEK', status: 'SHIPPED', agreedAt: iso(-2 * 24 * 60), paidAt: iso(-2 * 24 * 60 + 40), shippedAt: iso(-1 * 24 * 60), deliveredAt: null, releasedAt: null, settledAt: null, disputeReason: null,
    payment: { id: 'pay-3002', provider: 'FAKE', intentRef: 'pi_fake_3002', status: 'CONFIRMED', amount: 15000, feeAmount: 1400, payoutRef: null, refundRef: null, confirmedAt: iso(-2 * 24 * 60 + 40), releasedAt: null, refundedAt: null },
    shipment: { id: 'shp-3002', orderId: 'ord-3002', carrier: 'POSTNORD', service: 'MyPack Collect', trackingNumber: 'PN987654321SE', labelUrl: null, status: 'IN_TRANSIT', lastCarrierEvent: 'Arrived at sorting hub', lastEventAt: iso(-6 * 60), deliveredAt: null } as OrderDetail['shipment'],
    timeline: [
      { at: iso(-2 * 24 * 60), type: 'OrderAgreed', detail: 'CreateFromAuction (AuctionClosedWonV1)' },
      { at: iso(-2 * 24 * 60 + 40), type: 'OrderPaid', detail: null },
      { at: iso(-1 * 24 * 60), type: 'OrderShipped', detail: 'PN987654321SE' },
    ],
  },
  {
    id: 'ord-3003', announceId: 'ann-offer-4', announceTitle: 'IKEA Poäng + footstool', sellerId: 19, buyerId: 42, door: 'OFFER', agreedPrice: 40000, feeAmount: 3400, payoutAmount: 36600,
    labelPrice: null, currency: 'DKK', status: 'SETTLED', agreedAt: iso(-20 * 24 * 60), paidAt: iso(-20 * 24 * 60 + 5), shippedAt: iso(-19 * 24 * 60), deliveredAt: iso(-17 * 24 * 60), releasedAt: iso(-14 * 24 * 60), settledAt: iso(-13 * 24 * 60), disputeReason: null,
    payment: { id: 'pay-3003', provider: 'FAKE', intentRef: 'pi_fake_3003', status: 'RELEASED', amount: 40000, feeAmount: 3400, payoutRef: 'po_fake_3003', refundRef: null, confirmedAt: iso(-20 * 24 * 60 + 5), releasedAt: iso(-13 * 24 * 60), refundedAt: null },
    shipment: { id: 'shp-3003', orderId: 'ord-3003', carrier: 'FAKE', service: 'standard', trackingNumber: 'FK-3003', labelUrl: null, status: 'DELIVERED', lastCarrierEvent: 'Delivered', lastEventAt: iso(-17 * 24 * 60), deliveredAt: iso(-17 * 24 * 60) } as OrderDetail['shipment'],
    timeline: [
      { at: iso(-20 * 24 * 60), type: 'OrderAgreed', detail: 'CreateFromOffer (OfferAcceptedV1)' },
      { at: iso(-20 * 24 * 60 + 5), type: 'OrderPaid', detail: null },
      { at: iso(-19 * 24 * 60), type: 'OrderShipped', detail: null },
      { at: iso(-17 * 24 * 60), type: 'OrderDelivered', detail: null },
      { at: iso(-14 * 24 * 60), type: 'PayoutReleaseRequested', detail: 'Grace period elapsed' },
      { at: iso(-13 * 24 * 60), type: 'OrderSettled', detail: 'PayoutSent po_fake_3003' },
    ],
  },
  {
    id: 'ord-3004', announceId: 'ann-fixed-11', announceTitle: 'Bosch cordless drill', sellerId: 7, buyerId: 88, door: 'FIXED_PRICE', agreedPrice: 60000, feeAmount: 4600, payoutAmount: 55400,
    labelPrice: null, currency: 'DKK', status: 'PAYMENT_PENDING', agreedAt: iso(-90), paidAt: null, shippedAt: null, deliveredAt: null, releasedAt: null, settledAt: null, disputeReason: null,
    payment: { id: 'pay-3004', provider: 'FAKE', intentRef: 'pi_fake_3004', status: 'INTENT_CREATED', amount: 60000, feeAmount: 4600, payoutRef: null, refundRef: null, confirmedAt: null, releasedAt: null, refundedAt: null },
    shipment: null,
    timeline: [{ at: iso(-90), type: 'OrderAgreed', detail: 'AcceptPurchase' }, { at: iso(-89), type: 'PaymentIntentCreated', detail: 'pi_fake_4' }],
  },
  {
    id: 'ord-3005', announceId: 'ann-fixed-12', announceTitle: 'Kids bike 20"', sellerId: 23, buyerId: 42, door: 'FIXED_PRICE', agreedPrice: 80000, feeAmount: 5800, payoutAmount: 74200,
    labelPrice: null, currency: 'SEK', status: 'REFUNDED', agreedAt: iso(-12 * 24 * 60), paidAt: iso(-12 * 24 * 60 + 10), shippedAt: null, deliveredAt: null, releasedAt: null, settledAt: null, disputeReason: null,
    payment: { id: 'pay-3005', provider: 'FAKE', intentRef: 'pi_fake_3005', status: 'REFUNDED', amount: 80000, feeAmount: 5800, payoutRef: null, refundRef: 're_fake_3005', confirmedAt: iso(-12 * 24 * 60 + 10), releasedAt: null, refundedAt: iso(-7 * 24 * 60) },
    shipment: null,
    timeline: [
      { at: iso(-12 * 24 * 60), type: 'OrderAgreed', detail: null },
      { at: iso(-12 * 24 * 60 + 10), type: 'OrderPaid', detail: null },
      { at: iso(-7 * 24 * 60), type: 'OrderRefunded', detail: 'Ship window expired (OrderShipWindowJob)' },
    ],
  },
];

// ---- payouts (§3, §4 payment, §5 announce_payment / announce_seller_account) ----
const payouts: PayoutRow[] = [
  { orderId: 'ord-3003', paymentId: 'pay-3003', sellerId: 19, sellerAccountStatus: 'VERIFIED', payoutAmount: 36600, feeAmount: 3400, currency: 'DKK', status: 'SENT', releaseDueAt: null, releasedAt: iso(-13 * 24 * 60), payoutRef: 'po_fake_3003' },
  { orderId: 'ord-3001', paymentId: 'pay-3001', sellerId: 7, sellerAccountStatus: 'VERIFIED', payoutAmount: 893000, feeAmount: 57000, currency: 'DKK', status: 'BLOCKED_DISPUTE', releaseDueAt: iso(+2 * 24 * 60), releasedAt: null, payoutRef: null },
  { orderId: 'ord-3002', paymentId: 'pay-3002', sellerId: 12, sellerAccountStatus: 'PENDING', payoutAmount: 13600, feeAmount: 1400, currency: 'SEK', status: 'PENDING_RELEASE', releaseDueAt: iso(+5 * 24 * 60), releasedAt: null, payoutRef: null },
];

// ---- support (§9) ----
const conversations: ConversationSummary[] = [
  { id: 'conv-sup-1', type: 'SUPPORT', status: 'WAITING', announceId: null, openedBy: 42, openedAt: iso(-45), lastMessageAt: iso(-45), lastMessagePreview: 'I paid but the order still says pending', lastMessageSeq: 1, participantIds: [42], staffId: null },
  { id: 'conv-sup-2', type: 'SUPPORT', status: 'WAITING', announceId: 'ann-fixed-9', openedBy: 7, openedAt: iso(-20), lastMessageAt: iso(-18), lastMessagePreview: 'Buyer opened a dispute, what happens now?', lastMessageSeq: 2, participantIds: [7], staffId: null },
  { id: 'conv-sup-3', type: 'SUPPORT', status: 'OPEN', announceId: null, openedBy: 88, openedAt: iso(-3 * 60), lastMessageAt: iso(-10), lastMessagePreview: 'Thanks, that solved it', lastMessageSeq: 6, participantIds: [88, 1], staffId: 1 },
  { id: 'conv-sup-4', type: 'SUPPORT', status: 'CLOSED', announceId: null, openedBy: 61, openedAt: iso(-2 * 24 * 60), lastMessageAt: iso(-2 * 24 * 60 + 30), lastMessagePreview: 'Closed by staff', lastMessageSeq: 4, participantIds: [61, 1], staffId: 1 },
];
const bulletins: Bulletin[] = [
  { id: 1, title: 'Planned maintenance', content: 'The marketplace is read-only on Sunday 02:00-03:00 CET.', clientType: 'ALL', postedBy: 1, postedAt: iso(-3 * 24 * 60) },
  { id: 2, title: 'New PostNord labels', content: 'Sellers in SE can now buy MyPack Collect labels at checkout.', clientType: 'WEB', postedBy: 1, postedAt: iso(-24 * 60) },
];

export const handlers = [
  // auctions
  http.get('/api/admin/auctions', async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    return HttpResponse.json(page(auctions.filter((a) => !status || a.status === status), url));
  }),
  http.get('/api/admin/auctions/:announceId', async ({ params }) => {
    await delay(100);
    const a = auctions.find((x) => x.announceId === params.announceId);
    if (!a) return problem(404, `No auction for announce ${params.announceId}`);
    const detail: AuctionDetail = { ...a, bids: bids.filter((b) => b.announceId === a.announceId) };
    return HttpResponse.json(detail);
  }),
  // §8: POST /api/admin/bids/{id}/cancel (staff, with reason)
  http.post('/api/admin/bids/:bidId/cancel', async ({ params, request }) => {
    await delay(200);
    const body = (await request.json()) as { reason?: string };
    if (!body.reason?.trim()) return problem(400, 'A reason is required', 'reason-required');
    const b = bids.find((x) => x.id === params.bidId);
    if (!b) return problem(404, `Unknown bid ${params.bidId}`);
    if (b.status === 'CANCELLED') return problem(409, 'Bid already cancelled', 'bid-cancelled');
    b.status = 'CANCELLED';
    b.cancelReason = body.reason.trim();
    const a = auctions.find((x) => x.announceId === b.announceId);
    if (a) {
      const remaining = bids.filter((x) => x.announceId === a.announceId && x.status !== 'CANCELLED' && x.status !== 'RETRACTED');
      remaining.forEach((x, i) => (x.status = i === remaining.length - 1 ? 'ACTIVE' : 'OUTBID'));
      a.currentPrice = remaining.length ? remaining[remaining.length - 1].amount : a.initialPrice;
      a.bidCount = remaining.length;
    }
    return HttpResponse.json(b);
  }),

  // orders
  http.get('/api/admin/orders', async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const status = url.searchParams.get('status') as OrderStatus | null;
    const items = orders.filter((o) => !status || o.status === status).map(({ payment: _p, shipment: _s, timeline: _t, ...summary }) => summary);
    return HttpResponse.json(page(items, url));
  }),
  http.get('/api/admin/orders/:id', async ({ params }) => {
    await delay(100);
    const o = orders.find((x) => x.id === params.id);
    return o ? HttpResponse.json(o) : problem(404, `Unknown order ${params.id}`);
  }),
  // §8: POST /api/admin/orders/{id}/resolve-dispute -> DisputeResolved(RELEASE | REFUND)
  http.post('/api/admin/orders/:id/resolve-dispute', async ({ params, request }) => {
    await delay(250);
    const body = (await request.json()) as { resolution?: 'RELEASE' | 'REFUND'; reason?: string };
    const o = orders.find((x) => x.id === params.id);
    if (!o) return problem(404, `Unknown order ${params.id}`);
    if (o.status !== 'DISPUTED') return problem(409, `Order ${o.id} is ${o.status}, not DISPUTED`, 'order-not-disputed');
    if (!body.reason?.trim()) return problem(400, 'A reason is required', 'reason-required');
    o.status = body.resolution === 'REFUND' ? 'REFUNDING' : 'RELEASING';
    o.timeline.push({ at: new Date().toISOString(), type: 'DisputeResolved', detail: `${body.resolution}: ${body.reason.trim()}` });
    const p = payouts.find((x) => x.orderId === o.id);
    if (p) p.status = body.resolution === 'REFUND' ? 'FAILED' : 'RELEASING';
    return HttpResponse.json(o);
  }),

  // payouts
  http.get('/api/admin/payouts', async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const rows = payouts.filter((p) => !status || p.status === status);
    const sum = (pred: (p: PayoutRow) => boolean, pick: (p: PayoutRow) => number = (p) => p.payoutAmount) => payouts.filter(pred).reduce((s, p) => s + pick(p), 0);
    const overview: PayoutOverview = {
      totals: {
        pendingAmount: sum((p) => p.status === 'PENDING_RELEASE' || p.status === 'RELEASING'),
        sentAmount: sum((p) => p.status === 'SENT'),
        blockedAmount: sum((p) => p.status === 'BLOCKED_DISPUTE'),
        feesCollected: sum(() => true, (p) => p.feeAmount),
        currency: 'DKK',
      },
      page: page(rows, url),
    };
    return HttpResponse.json(overview);
  }),

  // support (§9)
  http.get('/api/admin/conversations', async ({ request }) => {
    await delay(120);
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    return HttpResponse.json(page(conversations.filter((c) => !status || c.status === status), url));
  }),
  http.post('/api/admin/conversations/:id/join', async ({ params }) => {
    await delay(200);
    const c = conversations.find((x) => x.id === params.id);
    if (!c) return problem(404, `Unknown conversation ${params.id}`);
    if (c.status !== 'WAITING') return problem(409, 'Conversation is not waiting for staff', 'conversation-not-waiting');
    c.status = 'OPEN';
    c.staffId = 1;
    c.participantIds = [...c.participantIds, 1];
    return HttpResponse.json(c);
  }),
  http.get('/api/admin/bulletins', async () => {
    await delay(100);
    return HttpResponse.json([...bulletins].sort((a, b) => b.postedAt.localeCompare(a.postedAt)));
  }),
  http.post('/api/admin/bulletins', async ({ request }) => {
    await delay(200);
    const body = (await request.json()) as { title?: string; content?: string; clientType?: Bulletin['clientType'] };
    if (!body.title?.trim() || !body.content?.trim()) return problem(400, 'title and content are required', 'bulletin-invalid');
    const b: Bulletin = { id: bulletins.length + 1, title: body.title.trim(), content: body.content.trim(), clientType: body.clientType ?? 'ALL', postedBy: 1, postedAt: new Date().toISOString() };
    bulletins.push(b);
    return HttpResponse.json(b, { status: 201 });
  }),
];
