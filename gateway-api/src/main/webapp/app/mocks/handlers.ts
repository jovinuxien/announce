import { http, HttpResponse } from 'msw';
import { BidRequest, DisputeRequest, RequestPurchaseRequest, ShipmentView } from 'app/shared/model/trade';
import { OpenConversationRequest, PostMessageRequest } from 'app/shared/model/chat';
import { auctionFor, mockDb, newOrder, placeBid, transition } from './data';
import { chatDb, closeConversation, conversationOf, decideOffer, inboxOf, markRead, ME, MockProblem, openListing, openSupport, postMessage, replyFrom, responseOf } from './chatData';

const problem = (e: unknown) => {
  const p = e as Partial<MockProblem>;
  return HttpResponse.json({ status: p.status ?? 500, title: 'mock', code: p.code, detail: p.detail ?? String(e) }, { status: p.status ?? 500, headers: { 'Content-Type': 'application/problem+json' } });
};
const page = <T,>(items: T[], url: string) => {
  const q = new URL(url, 'http://localhost').searchParams;
  const p = Math.max(1, Number(q.get('page') ?? 1) || 1);
  const size = Math.min(200, Math.max(1, Number(q.get('size') ?? 20) || 20));
  return { items: items.slice((p - 1) * size, p * size), page: p, size };
};

/**
 * REST mocks of the order (doc/order-context.md §8) contract and of messaging's member routes as built
 * (doc/frontend-edges.md §15). The WebSocket is not mocked: in the browser the thread polls; the page tests use
 * app/test/fakeWebSocket.ts for the frames.
 */
export const handlers = [
  // ---- auctions
  http.get('/api/auctions/:announceId', ({ params }) => HttpResponse.json(auctionFor(String(params.announceId)))),
  http.post('/api/auctions/:announceId/bids', async ({ params, request }) => {
    try {
      const body = (await request.json()) as BidRequest;
      return HttpResponse.json(placeBid(String(params.announceId), body.amountCents, mockDb.me));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/auctions/:announceId/snag', ({ params }) => {
    const a = auctionFor(String(params.announceId));
    if (a.snagPriceCents == null) return problem({ status: 409, detail: 'no snag price' });
    a.currentBidCents = a.snagPriceCents;
    a.status = 'CLOSED';
    a.highestBidderId = mockDb.me;
    a.myHighestBidCents = a.snagPriceCents;
    a.bidCount += 1;
    const order = newOrder(a.announceId, mockDb.me, a.snagPriceCents);
    transition(order.id, 'AGREED', 'snagged');
    return HttpResponse.json(a);
  }),

  // ---- orders
  http.post('/api/orders', async ({ request }) => {
    const body = (await request.json()) as RequestPurchaseRequest;
    return HttpResponse.json(newOrder(body.announceId, mockDb.me), { status: 201 });
  }),
  http.get('/api/orders', ({ request }) => {
    const role = new URL(request.url).searchParams.get('role') ?? 'buyer';
    const list = [...mockDb.orders.values()].filter(o => (role === 'seller' ? o.sellerId === mockDb.me : o.buyerId === mockDb.me));
    return HttpResponse.json(list);
  }),
  http.get('/api/orders/:id', ({ params }) => {
    const o = mockDb.orders.get(String(params.id));
    return o ? HttpResponse.json(o) : problem({ status: 404, detail: 'order not found' });
  }),
  http.post('/api/orders/:id/accept', ({ params }) => HttpResponse.json(transition(String(params.id), 'AGREED', 'seller accepted'))),
  http.post('/api/orders/:id/decline', ({ params }) => HttpResponse.json(transition(String(params.id), 'CANCELLED', 'seller declined'))),
  http.post('/api/orders/:id/cancel', ({ params }) => HttpResponse.json(transition(String(params.id), 'CANCELLED', 'cancelled'))),
  http.post('/api/orders/:id/confirm-receipt', ({ params }) => HttpResponse.json(transition(String(params.id), 'SETTLED', 'receipt confirmed, payout released'))),
  http.post('/api/orders/:id/dispute', async ({ params, request }) => {
    const body = (await request.json()) as DisputeRequest;
    return HttpResponse.json(transition(String(params.id), 'DISPUTED', body.reason));
  }),
  http.post('/api/orders/:id/shipment', ({ params }) => {
    const id = String(params.id);
    const s: ShipmentView = { orderId: id, carrier: 'FakeCarrier', trackingNumber: `FK${Date.now()}`, status: 'DISPATCHED', updatedAt: new Date().toISOString().slice(0, 19) };
    mockDb.shipments.set(id, s);
    transition(id, 'SHIPPED', 'dispatched');
    window.setTimeout(() => {
      s.status = 'DELIVERED';
      s.updatedAt = new Date().toISOString().slice(0, 19);
      transition(id, 'DELIVERED', 'carrier delivered');
    }, 15000);
    return HttpResponse.json(s, { status: 201 });
  }),
  http.get('/api/orders/:id/shipment', ({ params }) => {
    const s = mockDb.shipments.get(String(params.id));
    return s ? HttpResponse.json(s) : problem({ status: 404, detail: 'no shipment' });
  }),
  http.post('/api/payments/webhook/fake', async ({ request }) => {
    const body = (await request.json()) as { orderId: string };
    transition(body.orderId, 'PAID', 'fake provider confirmed');
    return new HttpResponse(null, { status: 204 });
  }),

  // ---- conversations (messaging's member routes; the caller is member #1)
  http.get('/api/conversations', ({ request }) => HttpResponse.json(page(inboxOf(ME), request.url))),
  http.post('/api/conversations', async ({ request }) => {
    try {
      const body = (await request.json()) as OpenConversationRequest;
      const c = body.type === 'SUPPORT' ? openSupport(body.subject, ME) : openListing(body.announceId ?? '', ME);
      return HttpResponse.json(responseOf(c), { status: 201 });
    } catch (e) {
      return problem(e);
    }
  }),
  http.get('/api/conversations/:id', ({ params }) => {
    try {
      return HttpResponse.json(conversationOf(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.get('/api/conversations/:id/messages', ({ params, request }) => {
    try {
      const c = conversationOf(String(params.id), ME);
      const q = new URL(request.url, 'http://localhost').searchParams;
      const after = Number(q.get('after') ?? '0');
      const limit = Number(q.get('limit') ?? '50');
      return HttpResponse.json((chatDb.messages.get(c.id) ?? []).filter(m => m.seq > after).slice(0, limit));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/conversations/:id/messages', async ({ params, request }) => {
    try {
      const body = (await request.json()) as PostMessageRequest;
      const id = String(params.id);
      const result = postMessage(id, ME, body);
      if (chatDb.autoReply && !result.duplicate && body.contentType === 'TEXT' && id.startsWith('lst-')) {
        window.setTimeout(() => replyFrom(id, 2, `(mock seller) got: ${body.content}`), 1500);
      }
      return HttpResponse.json(result, { status: 201 });
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/conversations/:id/offers/accept', ({ params }) => {
    try {
      return HttpResponse.json(responseOf(decideOffer(String(params.id), ME, true)));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/conversations/:id/offers/decline', ({ params }) => {
    try {
      return HttpResponse.json(responseOf(decideOffer(String(params.id), ME, false)));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/conversations/:id/read', async ({ params, request }) => {
    try {
      const body = (await request.json()) as { seq: number };
      markRead(String(params.id), ME, body.seq);
      return new HttpResponse(null, { status: 204 });
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/conversations/:id/close', ({ params }) => {
    try {
      return HttpResponse.json(responseOf(closeConversation(String(params.id), ME)));
    } catch (e) {
      return problem(e);
    }
  }),
];
