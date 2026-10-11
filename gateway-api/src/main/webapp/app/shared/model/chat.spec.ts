import { isMine, isWritable, messageOfFrame, otherMember, staffOf } from './chat';

describe('chat model helpers', () => {
  it('maps a message frame (MessageLive names, cents) to the MessageView the screens render', () => {
    const view = messageOfFrame({
      type: 'message',
      conversationId: 'c1',
      messageId: 'c1-m3',
      seq: 3,
      senderType: 'USER',
      senderId: 3,
      contentType: 'OFFER',
      content: null,
      attachmentUrl: null,
      offerAmountCents: 45000,
      currency: 'DKK',
      clientMsgId: 'x',
      sentAt: '2026-10-11T10:00:00',
    });
    expect(view).toEqual({
      id: 'c1-m3',
      conversationId: 'c1',
      seq: 3,
      senderType: 'USER',
      senderId: 3,
      contentType: 'OFFER',
      content: null,
      attachmentUrl: null,
      offerAmount: 450,
      offerCurrency: 'DKK',
      offerStatus: 'OPEN',
      clientMsgId: 'x',
      sentAt: '2026-10-11T10:00:00',
    });
    expect(
      messageOfFrame({
        type: 'message',
        conversationId: 'c1',
        messageId: 'c1-m4',
        seq: 4,
        senderType: 'ADMIN',
        senderId: 1,
        contentType: 'TEXT',
        content: 'hi',
        attachmentUrl: null,
        offerAmountCents: null,
        currency: null,
        clientMsgId: null,
        sentAt: '2026-10-11T10:00:01',
      })
    ).toMatchObject({ id: 'c1-m4', offerAmount: null, offerStatus: null });
  });

  it('lets a member write while WAITING or OPEN only', () => {
    expect(isWritable('WAITING')).toBe(true);
    expect(isWritable('OPEN')).toBe(true);
    expect(isWritable('CLOSED')).toBe(false);
    expect(isWritable('BLOCKED')).toBe(false);
    expect(isWritable(undefined)).toBe(false);
  });

  it('recognises own messages by USER plus the signed-in id, never staff or system lines', () => {
    expect(isMine({ senderType: 'USER', senderId: 1 }, 1)).toBe(true);
    expect(isMine({ senderType: 'USER', senderId: 2 }, 1)).toBe(false);
    expect(isMine({ senderType: 'ADMIN', senderId: 1 }, 1)).toBe(false);
    expect(isMine({ senderType: 'SYSTEM', senderId: null }, undefined)).toBe(false);
  });

  it('finds the other member of a listing and the staff of a conversation', () => {
    expect(otherMember({ type: 'LISTING', buyerId: 1, sellerId: 2 }, 1)).toBe(2);
    expect(otherMember({ type: 'LISTING', buyerId: 3, sellerId: 1 }, 1)).toBe(3);
    expect(otherMember({ type: 'SUPPORT', buyerId: 1, sellerId: null }, 1)).toBeNull();
    expect(
      staffOf({
        participants: [
          {
            type: 'USER',
            id: 1,
            role: 'MEMBER',
            lastReadSeq: 0,
            joinedAt: null,
          },
          { type: 'ADMIN', id: 7, role: 'CSR', lastReadSeq: 0, joinedAt: null },
        ],
      }).map(p => p.id)
    ).toEqual([7]);
  });
});
