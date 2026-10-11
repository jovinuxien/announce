import { HttpResponse, delay, http } from 'msw';
import type { AuctionView } from 'app/shared/api/auctionsApi';
import type { Bulletin, ConversationSummary } from 'app/shared/api/supportApi';
import { auctions, bids, iso, orders, pageOf, payments, payoutOverview, shipments } from './tradeFixtures';

/**
 * MSW handlers for the phase-2 admin paths, started only with `features.mocks` (app/config/features.ts).
 *  - trade (auctions, bids, orders, payment, shipment, payouts): the order module's admin endpoints in their real
 *    shapes (doc/frontend-edges.md §12), fixtures in ./tradeFixtures.ts. Without the flag these calls reach the
 *    order module through the edge.
 *  - support queue and bulletins: doc/order-context.md §9 sketch; the chat screens are not wired to messaging yet.
 * Only the paths below are intercepted; everything else (auth, phase-1 API) passes through to the edge.
 */

function problem(status: number, detail: string, code?: string) {
  return HttpResponse.json({ type: 'about:blank', title: 'Error', status, detail, code }, { status, headers: { 'content-type': 'application/problem+json' } });
}

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
  // auction context
  http.get('/api/admin/auctions', async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const seller = url.searchParams.get('seller');
    const q = url.searchParams.get('q')?.toLowerCase();
    if (Number(url.searchParams.get('size') ?? 20) > 100) return problem(400, 'size must be between 1 and 100');
    const rows = auctions
      .filter((a) => (!status || a.status === status) && (!seller || String(a.sellerId) === seller) && (!q || (a.title ?? '').toLowerCase().includes(q)))
      .sort((a, b) => b.endTime.localeCompare(a.endTime));
    return HttpResponse.json(pageOf(rows, url));
  }),
  http.get('/api/admin/auctions/:announceId', async ({ params }) => {
    await delay(100);
    const a = auctions.find((x) => x.announceId === params.announceId);
    if (!a) return problem(404, `No auction for announce ${params.announceId}`, 'auction-not-found');
    const view: AuctionView = { auction: a, bids: bids.filter((b) => b.announceId === a.announceId).sort((x, y) => y.bidTime.localeCompare(x.bidTime)) };
    return HttpResponse.json(view);
  }),
  // POST /api/admin/bids/{id}/cancel {reason}; answers the domain record (cents), which the SPA ignores
  http.post('/api/admin/bids/:bidId/cancel', async ({ params, request }) => {
    await delay(200);
    const body = (await request.json()) as { reason?: string };
    const b = bids.find((x) => x.id === params.bidId);
    if (!b) return problem(404, `Unknown bid ${params.bidId}`, 'bid-not-found');
    const a = auctions.find((x) => x.announceId === b.announceId);
    if (!a || a.status !== 'OPEN') return problem(409, 'Auction is not open', 'auction-not-open');
    if (b.status === 'CANCELLED') return problem(409, 'Bid already cancelled', 'bid-cancelled');
    b.status = 'CANCELLED';
    b.cancelReason = body.reason?.trim() || null;
    b.cancelledBy = 1;
    const remaining = bids.filter((x) => x.announceId === a.announceId && x.status !== 'CANCELLED' && x.status !== 'RETRACTED').sort((x, y) => x.bidTime.localeCompare(y.bidTime));
    remaining.forEach((x, i) => (x.status = i === remaining.length - 1 ? 'ACTIVE' : 'OUTBID'));
    a.currentPrice = remaining.length ? remaining[remaining.length - 1].amount : a.initialPrice;
    a.bidCount = remaining.length;
    return HttpResponse.json({ announceId: a.announceId, status: a.status, currentPriceCents: Math.round(a.currentPrice * 100) });
  }),

  // order context
  http.get('/api/admin/orders', async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const seller = url.searchParams.get('seller');
    const buyer = url.searchParams.get('buyer');
    const q = url.searchParams.get('q')?.toLowerCase();
    if (Number(url.searchParams.get('size') ?? 20) > 100) return problem(400, 'size must be between 1 and 100');
    const rows = orders.filter(
      (o) => (!status || o.status === status) && (!seller || String(o.sellerId) === seller) && (!buyer || String(o.buyerId) === buyer) && (!q || (o.title ?? '').toLowerCase().includes(q)),
    );
    return HttpResponse.json(pageOf(rows, url));
  }),
  http.get('/api/admin/orders/:id', async ({ params }) => {
    await delay(100);
    const o = orders.find((x) => x.id === params.id);
    return o ? HttpResponse.json(o) : problem(404, `Unknown order ${params.id}`, 'order-not-found');
  }),
  // payment context: 404 until the buyer started paying
  http.get('/api/admin/orders/:id/payment', async ({ params }) => {
    await delay(80);
    const p = payments.find((x) => x.orderId === params.id);
    return p ? HttpResponse.json(p) : problem(404, `No payment for order ${params.id}`, 'payment-not-found');
  }),
  // shipping context: 404 until the seller requested a shipment
  http.get('/api/admin/orders/:id/shipment', async ({ params }) => {
    await delay(80);
    const sh = shipments.find((x) => x.orderId === params.id);
    return sh ? HttpResponse.json(sh) : problem(404, `No shipment for order ${params.id}`, 'shipment-not-found');
  }),
  // POST /api/admin/orders/{id}/resolve-dispute {resolution, note}; answers the domain record (cents), ignored by the SPA
  http.post('/api/admin/orders/:id/resolve-dispute', async ({ params, request }) => {
    await delay(250);
    const body = (await request.json()) as { resolution?: 'RELEASE' | 'REFUND'; note?: string };
    const o = orders.find((x) => x.id === params.id);
    if (!o) return problem(404, `Unknown order ${params.id}`, 'order-not-found');
    if (!body.resolution) return problem(400, 'resolution must not be blank');
    if (o.status !== 'DISPUTED') return problem(409, `Order ${o.id} is ${o.status}, not DISPUTED`, 'order-not-disputed');
    o.status = body.resolution === 'REFUND' ? 'REFUNDING' : 'RELEASING';
    if (body.resolution === 'RELEASE') o.releasedAt = iso(0);
    return HttpResponse.json({ id: o.id, status: o.status, note: body.note ?? null });
  }),

  // payouts, derived from the order rows
  http.get('/api/admin/payouts', async ({ request }) => {
    await delay(150);
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    if (status && !['PENDING_RELEASE', 'BLOCKED_DISPUTE', 'RELEASING', 'SENT', 'REFUNDED'].includes(status)) return problem(400, `Unknown payout status ${status}`);
    return HttpResponse.json(payoutOverview(url));
  }),

  // support (§9)
  http.get('/api/admin/conversations', async ({ request }) => {
    await delay(120);
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    return HttpResponse.json(pageOf(conversations.filter((c) => !status || c.status === status), url));
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
