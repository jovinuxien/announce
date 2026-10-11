import { http, HttpResponse } from 'msw';
import { BidRequest, PurchaseRequest, ReasonRequest, ShipmentRequest } from 'app/shared/model/trade';
import { OpenConversationRequest, PostMessageRequest } from 'app/shared/model/chat';
import {
  accept,
  auctionOf,
  cancel,
  carrierDispatched,
  confirmReceipt,
  decline,
  dispute,
  onboard,
  ordersOf,
  orderOf,
  paymentOf,
  placeBid,
  requestPurchase,
  requestShipment,
  retractBid,
  sellerAccount,
  shipmentOf,
  snag,
  tradeDb,
} from './tradeData';
import {
  chatDb,
  closeConversation,
  conversationOf,
  decideOffer,
  inboxOf,
  markRead,
  ME,
  MockProblem,
  openListing,
  openSupport,
  postMessage,
  replyFrom,
  responseOf,
} from './chatData';

const problem = (e: unknown) => {
  const p = e as Partial<MockProblem>;
  return HttpResponse.json(
    { status: p.status ?? 500, title: 'mock', code: p.code, detail: p.detail ?? String(e) },
    { status: p.status ?? 500, headers: { 'Content-Type': 'application/problem+json' } }
  );
};
const page = <T>(items: T[], url: string) => {
  const q = new URL(url, 'http://localhost').searchParams;
  const p = Math.max(1, Number(q.get('page') ?? 1) || 1);
  const size = Math.min(200, Math.max(1, Number(q.get('size') ?? 20) || 20));
  return { items: items.slice((p - 1) * size, p * size), page: p, size };
};

/**
 * REST mocks of the order module's member routes (doc/frontend-edges.md §16) and of messaging's member routes
 * (§15) as built. The WebSocket is not mocked: in the browser the thread polls; the page tests use
 * app/test/fakeWebSocket.ts for the frames.
 */
export const handlers = [
  // ---- order module: auction, order, payment, shipping (the caller is member #1)
  http.get('/api/auctions/:announceId', ({ params }) => {
    try {
      return HttpResponse.json(auctionOf(String(params.announceId)));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/auctions/:announceId/bids', async ({ params, request }) => {
    try {
      const body = (await request.json()) as BidRequest;
      return HttpResponse.json(placeBid(String(params.announceId), ME, body.amountCents), { status: 201 });
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/auctions/:announceId/snag', ({ params }) => {
    try {
      return HttpResponse.json(snag(String(params.announceId), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.delete('/api/auctions/:announceId/bids/:bidId', ({ params }) => {
    try {
      return HttpResponse.json(retractBid(String(params.announceId), String(params.bidId), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/orders', async ({ request }) => {
    try {
      const body = (await request.json()) as PurchaseRequest;
      return HttpResponse.json(requestPurchase(body.announceId, ME), { status: 201 });
    } catch (e) {
      return problem(e);
    }
  }),
  http.get('/api/orders', ({ request }) => {
    const q = new URL(request.url, 'http://localhost').searchParams;
    return HttpResponse.json(ordersOf(ME, q.get('role') ?? 'buyer', Number(q.get('page') ?? 0), Number(q.get('size') ?? 20)));
  }),
  http.get('/api/orders/:id', ({ params }) => {
    try {
      return HttpResponse.json(orderOf(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/orders/:id/accept', ({ params }) => {
    try {
      return HttpResponse.json(accept(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/orders/:id/decline', ({ params }) => {
    try {
      return HttpResponse.json(decline(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/orders/:id/cancel', ({ params }) => {
    try {
      return HttpResponse.json(cancel(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/orders/:id/confirm-receipt', ({ params }) => {
    try {
      return HttpResponse.json(confirmReceipt(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/orders/:id/dispute', async ({ params, request }) => {
    try {
      const body = (await request.json()) as ReasonRequest;
      return HttpResponse.json(dispute(String(params.id), ME, body.reason ?? undefined));
    } catch (e) {
      return problem(e);
    }
  }),
  http.get('/api/orders/:id/payment', ({ params }) => {
    try {
      return HttpResponse.json(paymentOf(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.get('/api/orders/:id/shipment', ({ params }) => {
    try {
      return HttpResponse.json(shipmentOf(String(params.id), ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/orders/:id/shipment', async ({ params, request }) => {
    try {
      const body = (await request.json()) as ShipmentRequest;
      const id = String(params.id);
      const answer = requestShipment(id, ME, body);
      if (tradeDb.autoCarrier) window.setTimeout(() => carrierDispatched(id), 1500);
      return HttpResponse.json(answer, { status: 201 });
    } catch (e) {
      return problem(e);
    }
  }),
  http.get('/api/sellers/me/account', () => {
    try {
      return HttpResponse.json(sellerAccount(ME));
    } catch (e) {
      return problem(e);
    }
  }),
  http.post('/api/sellers/me/account', () => HttpResponse.json(onboard(ME), { status: 201 })),

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
