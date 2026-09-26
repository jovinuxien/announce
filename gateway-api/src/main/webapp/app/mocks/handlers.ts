import { http, HttpResponse } from 'msw';
import { BidRequest, DisputeRequest, RequestPurchaseRequest, ShipmentView } from 'app/shared/model/trade';
import { OpenConversationRequest, PostMessageRequest } from 'app/shared/model/chat';
import { addMessage, auctionFor, conversationFor, mockDb, newOrder, placeBid, transition } from './data';

const problem = (e: unknown) => {
  const p = e as { status?: number; detail?: string };
  return HttpResponse.json({ status: p.status ?? 500, title: 'mock', detail: p.detail ?? String(e) }, { status: p.status ?? 500, headers: { 'Content-Type': 'application/problem+json' } });
};

/** REST mocks of the order (§8) and messaging (§9) contracts; the WebSocket is not mocked, the thread polls. */
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

  // ---- conversations
  http.get('/api/conversations', () => HttpResponse.json({ items: [...mockDb.conversations.values()], page: 1, size: 50 })),
  http.post('/api/conversations', async ({ request }) => {
    const body = (await request.json()) as OpenConversationRequest;
    return HttpResponse.json(conversationFor(body.announceId ?? 'unknown', mockDb.me), { status: 201 });
  }),
  http.get('/api/conversations/:id', ({ params }) => {
    const c = mockDb.conversations.get(String(params.id));
    return c ? HttpResponse.json(c) : problem({ status: 404, detail: 'conversation not found' });
  }),
  http.get('/api/conversations/:id/messages', ({ params, request }) => {
    const after = Number(new URL(request.url).searchParams.get('after') ?? '0');
    return HttpResponse.json((mockDb.messages.get(String(params.id)) ?? []).filter(m => m.seq > after));
  }),
  http.post('/api/conversations/:id/messages', async ({ params, request }) => {
    try {
      const body = (await request.json()) as PostMessageRequest;
      const id = String(params.id);
      const m = addMessage(id, {
        clientMsgId: body.clientMsgId,
        contentType: body.contentType,
        content: body.content,
        attachmentUrl: body.attachmentUrl,
        offerAmount: body.amountCents != null ? body.amountCents / 100 : null,
        offerCurrency: body.currency,
        offerStatus: body.contentType === 'OFFER' ? 'OPEN' : null,
      });
      if (body.contentType === 'TEXT') {
        window.setTimeout(() => addMessage(id, { senderId: 2, contentType: 'TEXT', content: `(mock seller) got: ${body.content}` }), 1500);
      }
      return HttpResponse.json({ conversationId: id, messageId: m.id, seq: m.seq, clientMsgId: m.clientMsgId, duplicate: false, at: m.sentAt }, { status: 201 });
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/conversations/:id/offers/accept', ({ params }) => {
    const c = mockDb.conversations.get(String(params.id));
    if (!c?.openOffer) return problem({ status: 409, detail: 'no open offer' });
    c.acceptedOffer = { ...c.openOffer, status: 'ACCEPTED' };
    c.openOffer = null;
    addMessage(c.id, { senderType: 'SYSTEM', senderId: null, contentType: 'SYSTEM', content: 'Offer accepted; order agreed (mock)' });
    return HttpResponse.json(c);
  }),
  http.post('/api/conversations/:id/offers/decline', ({ params }) => {
    const c = mockDb.conversations.get(String(params.id));
    if (!c?.openOffer) return problem({ status: 409, detail: 'no open offer' });
    c.openOffer = null;
    return HttpResponse.json(c);
  }),
  http.post('/api/conversations/:id/read', () => new HttpResponse(null, { status: 204 })),
  http.post('/api/conversations/:id/close', ({ params }) => {
    const c = mockDb.conversations.get(String(params.id));
    if (!c) return problem({ status: 404, detail: 'conversation not found' });
    c.status = 'CLOSED';
    return HttpResponse.json(c);
  }),
];
