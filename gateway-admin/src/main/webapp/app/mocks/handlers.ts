import { HttpResponse, delay, http } from 'msw';
import type { AuctionView } from 'app/shared/api/auctionsApi';
import type { BulletinView, ConversationView, MessageView } from 'app/shared/api/supportApi';
import { auctions, bids, iso, orders, pageOf, payments, payoutOverview, shipments } from './tradeFixtures';
import { bulletins, chatPageOf, conversations, currentBulletins, messages, refreshCounters } from './chatFixtures';

/**
 * MSW handlers for the phase-2 admin paths, started only with `features.mocks` (app/config/features.ts).
 *  - trade (auctions, bids, orders, payment, shipment, payouts): the order module's admin endpoints in their real
 *    shapes (doc/frontend-edges.md §12), fixtures in ./tradeFixtures.ts. Without the flag these calls reach the
 *    order module through the edge.
 *  - chat (support queue, conversation, bulletins): messaging's staff routes in their real shapes (doc/frontend-edges.md
 *    §14), fixtures in ./chatFixtures.ts; the live frames of the WebSockets are not mocked (the screens poll instead).
 * Only the paths below are intercepted; everything else (auth, phase-1 API) passes through to the edge.
 */

function problem(status: number, detail: string, code?: string) {
  return HttpResponse.json({ type: 'about:blank', title: 'Error', status, detail, code }, { status, headers: { 'content-type': 'application/problem+json' } });
}

// ---- chat: messaging's staff routes (doc/frontend-edges.md §14). Join, close and block change the row only: the
// backend pushes a `status` frame, it posts no message (SYSTEM messages come from order events). ----
const STAFF_ID = 1; // the mocked session's admin
const conv = (id: string | readonly string[] | undefined) => conversations.find((x) => x.id === id);
const notWritable = (c: ConversationView) =>
  c.status === 'CLOSED' ? problem(409, 'The conversation is closed', 'conversation-closed')
    : c.status === 'BLOCKED' ? problem(409, 'The conversation is blocked', 'conversation-blocked') : null;
// the aggregate state a command answers (ConversationResponse); the SPA ignores it and refetches the view
const response = (c: ConversationView) => ({ id: c.id, type: c.type, announceId: c.announceId, buyerId: c.buyerId, sellerId: c.sellerId, subject: c.subject, status: c.status, lastSeq: c.lastMessageSeq, participants: c.participants.map((p) => ({ type: p.type, id: p.id, role: p.role })), openOffer: null, acceptedOffer: null, createdAt: c.createdAt, updatedAt: iso(0) });


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

  // chat (§14)
  // GET /api/admin/conversations?status=WAITING&page&size: one status, oldest first, {items,page,size} without total
  http.get('/api/admin/conversations', async ({ request }) => {
    await delay(120);
    const url = new URL(request.url);
    const status = url.searchParams.get('status') ?? 'WAITING';
    if (!['WAITING', 'OPEN', 'CLOSED', 'BLOCKED'].includes(status)) return problem(400, `No enum constant ConversationStatus.${status}`);
    const rows = conversations.filter((c) => c.status === status).sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id));
    return HttpResponse.json(chatPageOf(rows, url));
  }),
  http.post('/api/admin/conversations/:id/join', async ({ params }) => {
    await delay(200);
    const c = conv(params.id);
    if (!c) return problem(404, `Conversation ${params.id} does not exist`, 'conversation-not-found');
    const blocked = notWritable(c);
    if (blocked) return blocked;
    if (!c.participants.some((p) => p.type === 'ADMIN' && p.id === STAFF_ID)) {
      c.participants = [...c.participants, { type: 'ADMIN', id: STAFF_ID, role: 'CSR', lastReadSeq: 0, joinedAt: iso(0) }];
      if (c.status === 'WAITING') c.status = 'OPEN';
    }
    return HttpResponse.json(response(c));
  }),
  http.post('/api/admin/conversations/:id/close', async ({ params }) => {
    await delay(200);
    const c = conv(params.id);
    if (!c) return problem(404, `Conversation ${params.id} does not exist`, 'conversation-not-found');
    const blocked = notWritable(c);
    if (blocked) return blocked;
    c.status = 'CLOSED';
    c.closedAt = iso(0);
    return HttpResponse.json(response(c));
  }),
  http.post('/api/admin/conversations/:id/block', async ({ params, request }) => {
    await delay(200);
    const c = conv(params.id);
    if (!c) return problem(404, `Conversation ${params.id} does not exist`, 'conversation-not-found');
    const blocked = notWritable(c);
    if (blocked) return blocked;
    const body = ((await request.json().catch(() => null)) ?? {}) as { reason?: string };
    c.status = 'BLOCKED';
    c.blockedReason = body.reason ?? null;
    return HttpResponse.json(response(c));
  }),
  // the member routes staff may use: view, history, post, read marker
  http.get('/api/conversations/:id', async ({ params }) => {
    await delay(100);
    const c = conv(params.id);
    return c ? HttpResponse.json({ ...c, unread: c.participants.some((p) => p.type === 'ADMIN' && p.id === STAFF_ID) ? 0 : null }) : problem(404, `Conversation ${params.id} does not exist`, 'conversation-not-found');
  }),
  http.get('/api/conversations/:id/messages', async ({ params, request }) => {
    await delay(100);
    const c = conv(params.id);
    if (!c) return problem(404, `Conversation ${params.id} does not exist`, 'conversation-not-found');
    const url = new URL(request.url);
    const after = Number(url.searchParams.get('after') ?? 0) || 0;
    const limit = Math.min(200, Math.max(1, Number(url.searchParams.get('limit') ?? 50) || 50));
    return HttpResponse.json(messages.filter((m) => m.conversationId === c.id && m.seq > after).sort((a, b) => a.seq - b.seq).slice(0, limit));
  }),
  http.post('/api/conversations/:id/messages', async ({ params, request }) => {
    await delay(150);
    const c = conv(params.id);
    if (!c) return problem(404, `Conversation ${params.id} does not exist`, 'conversation-not-found');
    if (!c.participants.some((p) => p.type === 'ADMIN' && p.id === STAFF_ID)) return problem(403, `Not a participant of conversation ${c.id}`, 'not-participant');
    const blocked = notWritable(c);
    if (blocked) return blocked;
    const body = (await request.json()) as { clientMsgId?: string; contentType?: MessageView['contentType']; content?: string };
    if (!body.content?.trim()) return problem(400, 'The message text is required');
    const duplicate = body.clientMsgId ? messages.find((m) => m.conversationId === c.id && m.clientMsgId === body.clientMsgId) : undefined;
    if (duplicate) return HttpResponse.json({ conversationId: c.id, messageId: duplicate.id, seq: duplicate.seq, clientMsgId: duplicate.clientMsgId, duplicate: true, at: duplicate.sentAt }, { status: 201 });
    const seq = c.lastMessageSeq + 1;
    const m: MessageView = { id: `${c.id}-m${seq}`, conversationId: c.id, seq, senderType: 'ADMIN', senderId: STAFF_ID, contentType: body.contentType ?? 'TEXT', content: body.content, attachmentUrl: null, offerAmount: null, offerCurrency: null, offerStatus: null, clientMsgId: body.clientMsgId ?? null, sentAt: iso(0) };
    messages.push(m);
    refreshCounters();
    return HttpResponse.json({ conversationId: c.id, messageId: m.id, seq, clientMsgId: m.clientMsgId, duplicate: false, at: m.sentAt }, { status: 201 });
  }),
  http.post('/api/conversations/:id/read', async ({ params, request }) => {
    const c = conv(params.id);
    if (!c) return problem(404, `Conversation ${params.id} does not exist`, 'conversation-not-found');
    const body = (await request.json()) as { seq?: number };
    const me = c.participants.find((p) => p.type === 'ADMIN' && p.id === STAFF_ID);
    if (me && body.seq != null) me.lastReadSeq = Math.max(me.lastReadSeq, body.seq);
    return new HttpResponse(null, { status: 204 });
  }),
  // bulletins: GET ?audience=STAFF (current, newest first), POST {audience, clientType, content, expiresAt}
  http.get('/api/admin/bulletins', async ({ request }) => {
    await delay(100);
    return HttpResponse.json(currentBulletins(new URL(request.url).searchParams.get('audience') ?? 'STAFF'));
  }),
  http.post('/api/admin/bulletins', async ({ request }) => {
    await delay(200);
    const body = (await request.json()) as { audience?: string; clientType?: string | null; content?: string; expiresAt?: string | null };
    if (!body.content?.trim() || body.content.length > 1023) return problem(400, 'The bulletin text is required and at most 1023 characters');
    const b: BulletinView = { id: Math.max(0, ...bulletins.map((x) => x.id)) + 1, adminId: STAFF_ID, audience: (body.audience?.toUpperCase() as BulletinView['audience']) || 'STAFF', clientType: body.clientType || null, content: body.content, expiresAt: body.expiresAt ?? null, createdAt: iso(0) };
    bulletins.push(b);
    return HttpResponse.json(b, { status: 201 });
  }),
];
