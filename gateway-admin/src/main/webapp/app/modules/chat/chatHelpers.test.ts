import { bulletinOfFrame, memberOf, messageOfFrame, socketUrl, staffOf, type ConversationView } from 'app/shared/api/supportApi';
import { splitBulletin, toLocalDateTime } from './BulletinsPage';
import { conversations } from 'app/mocks/chatFixtures';

describe('chat helpers', () => {
  it('turns a message frame (cents) into the MessageView the screens render', () => {
    const view = messageOfFrame({ type: 'message', conversationId: 'c1', messageId: 'c1-m3', seq: 3, senderType: 'USER', senderId: 42, contentType: 'OFFER', content: null, attachmentUrl: null, offerAmountCents: 12550, currency: 'DKK', clientMsgId: 'x', sentAt: '2026-10-11T10:00:00' });
    expect(view).toMatchObject({ id: 'c1-m3', seq: 3, senderType: 'USER', senderId: 42, contentType: 'OFFER', offerAmount: 125.5, offerCurrency: 'DKK', clientMsgId: 'x' });
  });

  it('turns a bulletin frame into the BulletinView of the list', () => {
    expect(bulletinOfFrame({ type: 'bulletin', id: 9, adminId: 1, audience: 'STAFF', clientType: null, content: 'Hi', expiresAt: null, at: '2026-10-11T10:00:00' }))
      .toEqual({ id: 9, adminId: 1, audience: 'STAFF', clientType: null, content: 'Hi', expiresAt: null, createdAt: '2026-10-11T10:00:00' });
  });

  it('builds the socket URL on this edge with the token as a query parameter', () => {
    expect(socketUrl('/ws/staff', 'tok', { clientType: 'web' })).toBe('ws://localhost/ws/staff?clientType=web&access_token=tok');
    expect(socketUrl('/ws/conversations/a%2Fb', 'tok')).toBe('ws://localhost/ws/conversations/a%2Fb?access_token=tok');
  });

  it('reads the member and the staff of a conversation from its participants', () => {
    const open = conversations.find((c) => c.id === 'sup-88-c3') as ConversationView;
    expect(memberOf(open)).toBe(88);
    expect(staffOf(open).map((p) => p.id)).toEqual([1]);
    expect(memberOf({ ...open, buyerId: null, participants: [{ type: 'USER', id: 5, role: 'MEMBER', lastReadSeq: 0, joinedAt: null }] })).toBe(5);
  });

  it('splits a bulletin into heading and body and maps the expiry input to a LocalDateTime', () => {
    expect(splitBulletin('Heading\nline 1\nline 2')).toEqual({ heading: 'Heading', body: 'line 1\nline 2' });
    expect(splitBulletin('Only a heading')).toEqual({ heading: 'Only a heading', body: '' });
    expect(toLocalDateTime('')).toBeNull();
    expect(toLocalDateTime('2026-10-12T18:00')).toBe('2026-10-12T18:00:00');
    expect(toLocalDateTime('2026-10-12T18:00:30')).toBe('2026-10-12T18:00:30');
  });
});
