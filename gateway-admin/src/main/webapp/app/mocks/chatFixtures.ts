import type { BulletinView, ChatPage, ConversationView, MessageView, ParticipantView } from 'app/shared/api/supportApi';
import { iso } from './tradeFixtures';

/**
 * Fixtures for the chat screens in messaging's real shapes (ConversationQueries.ConversationView / MessageView,
 * Bulletins.BulletinView; doc/frontend-edges.md §14). Shared by the MSW handlers (features.mocks) and the page
 * tests. Staff member #1 is the signed-in admin of the mocked session.
 */

/** MessagingDtos.PageResponse: 1-based, size capped at messaging's max page size (200), no total. */
export function chatPageOf<T>(items: T[], url: URL): ChatPage<T> {
  const p = Math.max(1, Number(url.searchParams.get('page') ?? 1) || 1);
  const size = Math.min(200, Math.max(1, Number(url.searchParams.get('size') ?? 20) || 20));
  return { items: items.slice((p - 1) * size, p * size), page: p, size };
}

const member = (id: number, lastReadSeq: number, joinedAt: string): ParticipantView => ({ type: 'USER', id, role: 'MEMBER', lastReadSeq, joinedAt });
const csr = (id: number, lastReadSeq: number, joinedAt: string): ParticipantView => ({ type: 'ADMIN', id, role: 'CSR', lastReadSeq, joinedAt });

function conversation(partial: Partial<ConversationView> & Pick<ConversationView, 'id' | 'status' | 'buyerId' | 'subject' | 'createdAt' | 'participants'>): ConversationView {
  return {
    type: 'SUPPORT',
    announceId: null,
    sellerId: null,
    lastMessageId: null,
    lastMessageSeq: 0,
    lastMessageAt: null,
    messageCount: 0,
    unread: null,
    openOffer: null,
    acceptedOffer: null,
    updatedAt: partial.createdAt,
    closedAt: null,
    blockedReason: null,
    ...partial,
  };
}

export const conversations: ConversationView[] = [
  conversation({ id: 'sup-42-a1', status: 'WAITING', buyerId: 42, subject: 'Payment stuck on pending', createdAt: iso(-45), participants: [member(42, 1, iso(-45))] }),
  conversation({ id: 'sup-07-b2', status: 'WAITING', buyerId: 7, subject: 'Buyer opened a dispute', announceId: 'ann-fixed-9', createdAt: iso(-20), participants: [member(7, 2, iso(-20))] }),
  conversation({ id: 'sup-88-c3', status: 'OPEN', buyerId: 88, subject: 'Cannot change my e-mail', createdAt: iso(-3 * 60), participants: [member(88, 5, iso(-3 * 60)), csr(1, 6, iso(-2 * 60))] }),
  conversation({ id: 'sup-61-d4', status: 'CLOSED', buyerId: 61, subject: 'Refund timing', createdAt: iso(-2 * 24 * 60), closedAt: iso(-2 * 24 * 60 + 30), participants: [member(61, 4, iso(-2 * 24 * 60)), csr(1, 4, iso(-2 * 24 * 60 + 5))] }),
  conversation({ id: 'sup-13-e5', status: 'BLOCKED', buyerId: 13, subject: 'asdfgh', createdAt: iso(-5 * 24 * 60), blockedReason: 'Abusive language', participants: [member(13, 3, iso(-5 * 24 * 60)), csr(2, 3, iso(-5 * 24 * 60 + 10))] }),
];

function message(conversationId: string, seq: number, senderType: MessageView['senderType'], senderId: number | null, content: string, sentAt: string, contentType: MessageView['contentType'] = 'TEXT'): MessageView {
  return { id: `${conversationId}-m${seq}`, conversationId, seq, senderType, senderId, contentType, content, attachmentUrl: null, offerAmount: null, offerCurrency: null, offerStatus: null, clientMsgId: null, sentAt };
}

export const messages: MessageView[] = [
  message('sup-42-a1', 1, 'USER', 42, 'I paid order ord-3004 an hour ago but it still says payment pending.', iso(-45)),
  message('sup-07-b2', 1, 'USER', 7, 'The buyer of my Brompton opened a dispute, what happens now?', iso(-20)),
  message('sup-07-b2', 2, 'USER', 7, 'The bike was fine when I shipped it.', iso(-18)),
  message('sup-88-c3', 1, 'USER', 88, 'I cannot change my e-mail address in the profile.', iso(-3 * 60)),
  message('sup-88-c3', 2, 'SYSTEM', null, 'Order ord-3002 paid', iso(-2 * 60), 'SYSTEM'),
  message('sup-88-c3', 3, 'ADMIN', 1, 'Hi, which address do you want to switch to?', iso(-2 * 60 + 1)),
  message('sup-88-c3', 4, 'USER', 88, 'anna@example.org', iso(-100)),
  message('sup-88-c3', 5, 'ADMIN', 1, 'Done, you will get a confirmation mail.', iso(-90)),
  message('sup-88-c3', 6, 'USER', 88, 'Thanks, that solved it', iso(-10)),
  message('sup-61-d4', 1, 'USER', 61, 'When is a refund paid out?', iso(-2 * 24 * 60)),
  message('sup-61-d4', 2, 'ADMIN', 1, 'Within five banking days of the refund being sent.', iso(-2 * 24 * 60 + 10)),
  message('sup-61-d4', 3, 'USER', 61, 'Perfect, thanks.', iso(-2 * 24 * 60 + 20)),
  message('sup-61-d4', 4, 'SYSTEM', null, 'Order ord-3005 refunded', iso(-2 * 24 * 60 + 30), 'SYSTEM'),
  message('sup-13-e5', 1, 'USER', 13, 'asdfgh', iso(-5 * 24 * 60)),
  message('sup-13-e5', 2, 'USER', 13, '!!!!!', iso(-5 * 24 * 60 + 2)),
];

/** Derives the message counters of each conversation from the messages, as the projection does. */
export function refreshCounters() {
  conversations.forEach((c) => {
    const own = messages.filter((m) => m.conversationId === c.id).sort((a, b) => a.seq - b.seq);
    const last = own[own.length - 1];
    c.messageCount = own.length;
    c.lastMessageSeq = last?.seq ?? 0;
    c.lastMessageId = last?.id ?? null;
    c.lastMessageAt = last?.sentAt ?? null;
  });
}
refreshCounters();

export const bulletins: BulletinView[] = [
  { id: 1, adminId: 1, audience: 'STAFF', clientType: null, content: 'Planned maintenance\nThe marketplace is read-only on Sunday 02:00-03:00 CET.', expiresAt: null, createdAt: iso(-3 * 24 * 60) },
  { id: 2, adminId: 1, audience: 'STAFF', clientType: 'web', content: 'New PostNord labels\nSellers in SE can now buy MyPack Collect labels at checkout.', expiresAt: iso(7 * 24 * 60), createdAt: iso(-24 * 60) },
  { id: 3, adminId: 2, audience: 'MEMBERS', clientType: null, content: 'Welcome to the new marketplace!', expiresAt: null, createdAt: iso(-12 * 60) },
];

/** Bulletins.current: the audience's rows that have not expired, newest first. */
export function currentBulletins(audience: string): BulletinView[] {
  const now = iso(0);
  return bulletins.filter((b) => b.audience === audience.toUpperCase() && (b.expiresAt === null || b.expiresAt > now)).sort((a, b) => b.id - a.id);
}
