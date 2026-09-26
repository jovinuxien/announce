import { AuctionView, OrderView, ShipmentView } from 'app/shared/model/trade';
import { ConversationView, MessageView } from 'app/shared/model/chat';

/** In-memory state of the phase 2 mocks; reset on reload. Shapes: doc/order-context.md §8 and §9. */
export const mockDb = {
  me: 1,
  auctions: new Map<string, AuctionView>(),
  orders: new Map<string, OrderView>(),
  shipments: new Map<string, ShipmentView>(),
  conversations: new Map<string, ConversationView>(),
  messages: new Map<string, MessageView[]>(),
  seq: 0,
};

const now = () => new Date().toISOString().slice(0, 19);
const plusMinutes = (m: number) => new Date(Date.now() + m * 60000).toISOString().slice(0, 19);

export function auctionFor(announceId: string): AuctionView {
  let a = mockDb.auctions.get(announceId);
  if (!a) {
    a = {
      announceId,
      status: 'OPEN',
      currency: 'DKK',
      startPriceCents: 10000,
      currentBidCents: null,
      minimumNextBidCents: 10000,
      bidCount: 0,
      endsAt: plusMinutes(90),
      snagPriceCents: 50000,
      highestBidderId: null,
      myHighestBidCents: null,
    };
    mockDb.auctions.set(announceId, a);
  }
  return a;
}

export function placeBid(announceId: string, amountCents: number, bidderId: number): AuctionView {
  const a = auctionFor(announceId);
  if (a.status !== 'OPEN') throw { status: 409, detail: 'auction closed' };
  if (amountCents < a.minimumNextBidCents) throw { status: 409, detail: `bid below the minimum of ${a.minimumNextBidCents / 100}` };
  a.currentBidCents = amountCents;
  a.bidCount += 1;
  a.minimumNextBidCents = amountCents + 500;
  a.highestBidderId = bidderId;
  a.myHighestBidCents = amountCents;
  return a;
}

export function newOrder(announceId: string, buyerId: number, amountCents = 25000): OrderView {
  const id = `ord-${mockDb.orders.size + 1}`;
  const order: OrderView = {
    id,
    announceId,
    announceTitle: `Announce ${announceId}`,
    buyerId,
    sellerId: 2,
    status: 'REQUESTED',
    amountCents,
    feeCents: Math.round(amountCents * 0.05) + 500,
    currency: 'DKK',
    createdAt: now(),
    payBy: null,
    shipBy: null,
    timeline: [{ at: now(), status: 'REQUESTED', note: 'purchase requested' }],
  };
  mockDb.orders.set(id, order);
  return order;
}

export function transition(id: string, status: OrderView['status'], note?: string): OrderView {
  const o = mockDb.orders.get(id);
  if (!o) throw { status: 404, detail: 'order not found' };
  o.status = status;
  if (status === 'AGREED') o.payBy = plusMinutes(48 * 60);
  if (status === 'PAID') o.shipBy = plusMinutes(5 * 24 * 60);
  o.timeline = [...o.timeline, { at: now(), status, note }];
  return o;
}

export function conversationFor(announceId: string, buyerId: number): ConversationView {
  const id = `conv-${announceId}-${buyerId}`;
  let c = mockDb.conversations.get(id);
  if (!c) {
    c = {
      id,
      type: 'LISTING',
      announceId,
      buyerId,
      sellerId: 2,
      status: 'OPEN',
      lastMessageSeq: 0,
      messageCount: 0,
      unread: 0,
      openOffer: null,
      acceptedOffer: null,
      participants: [{ type: 'MEMBER', id: buyerId }, { type: 'MEMBER', id: 2 }],
      createdAt: now(),
      updatedAt: now(),
    };
    mockDb.conversations.set(id, c);
    mockDb.messages.set(id, []);
    addMessage(id, { senderType: 'SYSTEM', senderId: null, contentType: 'SYSTEM', content: 'Conversation opened (mock)' });
  }
  return c;
}

export function addMessage(conversationId: string, m: Partial<MessageView>): MessageView {
  const c = mockDb.conversations.get(conversationId);
  if (!c) throw { status: 404, detail: 'conversation not found' };
  const list = mockDb.messages.get(conversationId) ?? [];
  if (m.clientMsgId && list.some(x => x.clientMsgId === m.clientMsgId)) return list.find(x => x.clientMsgId === m.clientMsgId)!;
  const seq = c.lastMessageSeq + 1;
  const msg: MessageView = {
    id: `msg-${++mockDb.seq}`,
    conversationId,
    seq,
    senderType: m.senderType ?? 'MEMBER',
    senderId: m.senderId ?? mockDb.me,
    contentType: m.contentType ?? 'TEXT',
    content: m.content ?? null,
    attachmentUrl: m.attachmentUrl ?? null,
    offerAmount: m.offerAmount ?? null,
    offerCurrency: m.offerCurrency ?? null,
    offerStatus: m.offerStatus ?? null,
    clientMsgId: m.clientMsgId ?? null,
    sentAt: now(),
  };
  list.push(msg);
  mockDb.messages.set(conversationId, list);
  c.lastMessageSeq = seq;
  c.lastMessageId = msg.id;
  c.lastMessageAt = msg.sentAt;
  c.messageCount = list.length;
  c.updatedAt = msg.sentAt;
  if (msg.contentType === 'OFFER') c.openOffer = { messageId: msg.id, amountCents: Math.round((msg.offerAmount ?? 0) * 100), currency: msg.offerCurrency ?? 'DKK', status: 'OPEN', buyerId: c.buyerId ?? undefined };
  return msg;
}
