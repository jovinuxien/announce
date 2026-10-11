import {
  ConversationResponse,
  ConversationStatus,
  ConversationView,
  MessageView,
  ParticipantRole,
  ParticipantType,
  ParticipantView,
  PostMessageRequest,
  PostResult,
} from 'app/shared/model/chat';

/**
 * In-memory messaging for `features.mocks` and the page tests, in messaging's real shapes and with its rules
 * (doc/frontend-edges.md §15): deterministic listing ids, WAITING support conversations, 403 not-participant,
 * 409 buyer-is-seller / conversation-closed / offer-not-allowed / no-open-offer, duplicate client message ids,
 * no message posted by open, accept, decline or close. The signed-in member is #1.
 */
export const ME = 1;

export interface MockProblem {
  status: number;
  code: string;
  detail: string;
}
export const reject = (status: number, code: string, detail: string): MockProblem => ({ status, code, detail });

export const chatDb = {
  conversations: new Map<string, ConversationView>(),
  messages: new Map<string, MessageView[]>(),
  seq: 0,
  /** The mock seller answers a TEXT after 1.5 s in the browser; the tests switch it off. */
  autoReply: true,
};

export const iso = (minutesFromNow: number) => new Date(Date.now() + minutesFromNow * 60000).toISOString().slice(0, 19);

const participant = (type: ParticipantType, id: number, role: ParticipantRole, lastReadSeq: number, joinedAt: string | null): ParticipantView => ({
  type,
  id,
  role,
  lastReadSeq,
  joinedAt,
});

function conversation(partial: Partial<ConversationView> & Pick<ConversationView, 'id' | 'type' | 'status' | 'createdAt' | 'participants'>): ConversationView {
  return {
    announceId: null,
    buyerId: null,
    sellerId: null,
    subject: null,
    lastMessageId: null,
    lastMessageSeq: 0,
    lastMessageAt: null,
    messageCount: 0,
    unread: 0,
    openOffer: null,
    acceptedOffer: null,
    updatedAt: partial.createdAt,
    closedAt: null,
    blockedReason: null,
    ...partial,
  };
}

function message(
  conversationId: string,
  seq: number,
  senderType: MessageView['senderType'],
  senderId: number | null,
  content: string | null,
  sentAt: string,
  extra: Partial<MessageView> = {}
): MessageView {
  return {
    id: `${conversationId}-m${seq}`,
    conversationId,
    seq,
    senderType,
    senderId,
    contentType: 'TEXT',
    content,
    attachmentUrl: null,
    offerAmount: null,
    offerCurrency: null,
    offerStatus: null,
    clientMsgId: null,
    sentAt,
    ...extra,
  };
}

/** Message counters and the viewer's unread, derived from the messages as the projection does. */
export function refreshCounters(c: ConversationView) {
  const own = (chatDb.messages.get(c.id) ?? []).sort((a, b) => a.seq - b.seq);
  const last = own[own.length - 1];
  c.messageCount = own.length;
  c.lastMessageSeq = last?.seq ?? 0;
  c.lastMessageId = last?.id ?? null;
  c.lastMessageAt = last?.sentAt ?? null;
  if (last) c.updatedAt = last.sentAt;
  const mine = c.participants.find(p => p.type === 'USER' && p.id === ME);
  c.unread = mine ? Math.max(0, c.lastMessageSeq - mine.lastReadSeq) : null;
}

export function seedChat() {
  chatDb.conversations.clear();
  chatDb.messages.clear();
  chatDb.seq = 0;
  const put = (c: ConversationView, msgs: MessageView[]) => {
    chatDb.conversations.set(c.id, c);
    chatDb.messages.set(c.id, msgs);
    refreshCounters(c);
  };
  // me as the buyer of ann-fixed-9; the seller answered, one unread
  put(
    conversation({
      id: 'lst-ann-fixed-9-1',
      type: 'LISTING',
      announceId: 'ann-fixed-9',
      buyerId: ME,
      sellerId: 2,
      status: 'OPEN',
      createdAt: iso(-45),
      participants: [participant('USER', ME, 'BUYER', 1, iso(-45)), participant('USER', 2, 'SELLER', 2, iso(-45))],
    }),
    [message('lst-ann-fixed-9-1', 1, 'USER', ME, 'Is the bike still available?', iso(-45)), message('lst-ann-fixed-9-1', 2, 'USER', 2, 'Yes, it is. Pick-up in Vamdrup.', iso(-40))]
  );
  // me as the seller of ann-goods-4; the buyer made an offer that is still open
  put(
    conversation({
      id: 'lst-ann-goods-4-3',
      type: 'LISTING',
      announceId: 'ann-goods-4',
      buyerId: 3,
      sellerId: ME,
      status: 'OPEN',
      createdAt: iso(-30),
      participants: [participant('USER', 3, 'BUYER', 2, iso(-30)), participant('USER', ME, 'SELLER', 0, iso(-30))],
      openOffer: {
        messageId: 'lst-ann-goods-4-3-m2',
        amount: 450,
        currency: 'DKK',
        at: iso(-29),
      },
    }),
    [
      message('lst-ann-goods-4-3', 1, 'USER', 3, 'Would you take 450 for it?', iso(-30)),
      message('lst-ann-goods-4-3', 2, 'USER', 3, null, iso(-29), {
        contentType: 'OFFER',
        offerAmount: 450,
        offerCurrency: 'DKK',
        offerStatus: 'OPEN',
      }),
    ]
  );
  // a support conversation of mine, waiting for staff
  put(
    conversation({
      id: 'sup-1-waiting',
      type: 'SUPPORT',
      buyerId: ME,
      subject: 'Payment stuck on pending',
      status: 'WAITING',
      createdAt: iso(-20),
      participants: [participant('USER', ME, 'MEMBER', 1, iso(-20))],
    }),
    [message('sup-1-waiting', 1, 'USER', ME, 'I paid order ord-3004 an hour ago but it still says payment pending.', iso(-20))]
  );
  // a closed support conversation with a staff reply and an order event
  put(
    conversation({
      id: 'sup-1-closed',
      type: 'SUPPORT',
      buyerId: ME,
      subject: 'Refund timing',
      status: 'CLOSED',
      createdAt: iso(-2 * 24 * 60),
      closedAt: iso(-2 * 24 * 60 + 30),
      participants: [participant('USER', ME, 'MEMBER', 3, iso(-2 * 24 * 60)), participant('ADMIN', 1, 'CSR', 3, iso(-2 * 24 * 60 + 5))],
    }),
    [
      message('sup-1-closed', 1, 'USER', ME, 'When is a refund paid out?', iso(-2 * 24 * 60)),
      message('sup-1-closed', 2, 'ADMIN', 1, 'Within five banking days of the refund being sent.', iso(-2 * 24 * 60 + 10)),
      message('sup-1-closed', 3, 'SYSTEM', null, 'Order ord-3005 refunded', iso(-2 * 24 * 60 + 30), { contentType: 'SYSTEM' }),
    ]
  );
}
seedChat();

export const isParticipant = (c: ConversationView, me: number) => c.participants.some(p => p.type === 'USER' && p.id === me);

export function conversationOf(id: string, me: number): ConversationView {
  const c = chatDb.conversations.get(id);
  if (!c) throw reject(404, 'conversation-not-found', `Conversation ${id} does not exist`);
  if (!isParticipant(c, me)) throw reject(403, 'not-participant', `Not a participant of conversation ${id}`);
  return c;
}

/** The inbox: the member's conversations, latest activity first. */
export const inboxOf = (me: number): ConversationView[] =>
  [...chatDb.conversations.values()].filter(c => isParticipant(c, me)).sort((a, b) => ((b.lastMessageAt ?? b.updatedAt) > (a.lastMessageAt ?? a.updatedAt) ? 1 : -1));

/** MessagingDtos.ConversationResponse of a row: the aggregate shape the commands answer. */
export function responseOf(c: ConversationView): ConversationResponse {
  const offer = (o: ConversationView['openOffer']) =>
    o
      ? {
          messageId: o.messageId,
          amountCents: Math.round(o.amount * 100),
          currency: o.currency,
        }
      : null;
  return {
    id: c.id,
    type: c.type,
    announceId: c.announceId,
    buyerId: c.buyerId,
    sellerId: c.sellerId,
    subject: c.subject,
    status: c.status,
    lastSeq: c.lastMessageSeq,
    participants: c.participants.map(p => ({
      type: p.type,
      id: p.id,
      role: p.role,
    })),
    openOffer: offer(c.openOffer),
    acceptedOffer: offer(c.acceptedOffer),
    createdAt: c.createdAt,
    updatedAt: c.updatedAt,
  };
}

/** Opens (or finds) the listing conversation; announce ids starting with `mine-` belong to the caller, `missing-` are unknown. */
export function openListing(announceId: string, me: number): ConversationView {
  if (announceId.startsWith('missing-')) throw reject(404, 'announce-not-found', `Announce ${announceId} does not exist`);
  if (announceId.startsWith('mine-')) throw reject(409, 'buyer-is-seller', 'You cannot open a conversation about your own announce');
  const id = `lst-${announceId}-${me}`;
  let c = chatDb.conversations.get(id);
  if (!c) {
    const now = iso(0);
    c = conversation({
      id,
      type: 'LISTING',
      announceId,
      buyerId: me,
      sellerId: 2,
      status: 'OPEN',
      createdAt: now,
      participants: [participant('USER', me, 'BUYER', 0, now), participant('USER', 2, 'SELLER', 0, now)],
    });
    chatDb.conversations.set(id, c);
    chatDb.messages.set(id, []);
  }
  return c;
}

export function openSupport(subject: string | undefined, me: number): ConversationView {
  const now = iso(0);
  const id = `sup-${me}-${++chatDb.seq}`;
  const c = conversation({
    id,
    type: 'SUPPORT',
    buyerId: me,
    subject: subject ?? null,
    status: 'WAITING',
    createdAt: now,
    participants: [participant('USER', me, 'MEMBER', 0, now)],
  });
  chatDb.conversations.set(id, c);
  chatDb.messages.set(id, []);
  return c;
}

function requireWritable(c: ConversationView) {
  if (c.status === 'CLOSED') throw reject(409, 'conversation-closed', 'The conversation is closed');
  if (c.status === 'BLOCKED') throw reject(409, 'conversation-blocked', 'The conversation is blocked');
}

/** PostMessage by a member: idempotent on clientMsgId; OFFER only by the buyer of a listing. */
export function postMessage(id: string, me: number, body: PostMessageRequest): PostResult {
  const c = conversationOf(id, me);
  requireWritable(c);
  const list = chatDb.messages.get(id) ?? [];
  const dup = body.clientMsgId ? list.find(m => m.clientMsgId === body.clientMsgId) : undefined;
  if (dup)
    return {
      conversationId: id,
      messageId: dup.id,
      seq: dup.seq,
      clientMsgId: dup.clientMsgId ?? body.clientMsgId,
      duplicate: true,
      at: dup.sentAt,
    };
  const type = body.contentType ?? 'TEXT';
  if (type === 'OFFER') {
    if (c.type !== 'LISTING' || c.buyerId !== me) throw reject(409, 'offer-not-allowed', 'Only the buyer of a listing can make an offer');
    if (c.openOffer) throw reject(409, 'offer-not-allowed', 'There is already an open offer');
  }
  const seq = c.lastMessageSeq + 1;
  const m = message(id, seq, 'USER', me, body.content ?? null, iso(0), {
    contentType: type,
    attachmentUrl: body.attachmentUrl ?? null,
    offerAmount: type === 'OFFER' && body.amountCents != null ? body.amountCents / 100 : null,
    offerCurrency: type === 'OFFER' ? (body.currency ?? 'DKK') : null,
    offerStatus: type === 'OFFER' ? 'OPEN' : null,
    clientMsgId: body.clientMsgId ?? null,
  });
  list.push(m);
  chatDb.messages.set(id, list);
  if (type === 'OFFER')
    c.openOffer = {
      messageId: m.id,
      amount: m.offerAmount ?? 0,
      currency: m.offerCurrency ?? 'DKK',
      at: m.sentAt,
    };
  const mine = c.participants.find(p => p.type === 'USER' && p.id === me);
  if (mine) mine.lastReadSeq = seq;
  refreshCounters(c);
  return {
    conversationId: id,
    messageId: m.id,
    seq,
    clientMsgId: m.clientMsgId ?? '',
    duplicate: false,
    at: m.sentAt,
  };
}

/** A line from the other side, as the live topic would deliver it (used by the browser mock's auto-reply). */
export function replyFrom(id: string, senderId: number, content: string): MessageView | null {
  const c = chatDb.conversations.get(id);
  if (!c || c.status === 'CLOSED' || c.status === 'BLOCKED') return null;
  const list = chatDb.messages.get(id) ?? [];
  const m = message(id, c.lastMessageSeq + 1, 'USER', senderId, content, iso(0));
  list.push(m);
  chatDb.messages.set(id, list);
  refreshCounters(c);
  return m;
}

/** Accept or decline the open offer: the seller only. */
export function decideOffer(id: string, me: number, accept: boolean): ConversationView {
  const c = conversationOf(id, me);
  requireWritable(c);
  if (c.sellerId !== me) throw reject(403, 'not-seller', 'Only the seller can answer an offer');
  if (!c.openOffer) throw reject(409, 'no-open-offer', 'There is no open offer');
  const offer = c.openOffer;
  const list = chatDb.messages.get(id) ?? [];
  const msg = list.find(m => m.id === offer.messageId);
  if (msg) msg.offerStatus = accept ? 'ACCEPTED' : 'DECLINED';
  c.openOffer = null;
  if (accept) c.acceptedOffer = offer;
  c.updatedAt = iso(0);
  return c;
}

export function markRead(id: string, me: number, seq: number) {
  const c = conversationOf(id, me);
  const mine = c.participants.find(p => p.type === 'USER' && p.id === me);
  if (mine && seq > mine.lastReadSeq) mine.lastReadSeq = seq;
  refreshCounters(c);
}

export function closeConversation(id: string, me: number): ConversationView {
  const c = conversationOf(id, me);
  requireWritable(c);
  c.status = 'CLOSED' as ConversationStatus;
  c.closedAt = iso(0);
  c.updatedAt = c.closedAt;
  return c;
}
