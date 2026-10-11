import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import Thread from './Thread';
import { http, HttpResponse } from 'msw';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { FakeWebSocket, useFakeWebSocket } from 'app/test/fakeWebSocket';
import { chatDb, closeConversation } from 'app/mocks/chatData';

useMockServer();
useFakeWebSocket();

const bubbles = () => Array.from(screen.getByTestId('thread-messages').querySelectorAll<HTMLElement>('[data-sender]'));
const composer = () => screen.getByLabelText('Message') as HTMLInputElement;
const liveFrame = (conversationId: string, seq: number, senderId: number, content: string, clientMsgId: string | null = null) => ({
  type: 'message',
  conversationId,
  messageId: `${conversationId}-m${seq}`,
  seq,
  senderType: 'USER',
  senderId,
  contentType: 'TEXT',
  content,
  attachmentUrl: null,
  offerAmountCents: null,
  currency: null,
  clientMsgId,
  sentAt: '2026-10-11T12:00:00',
});

describe('Thread', () => {
  it('loads the history, opens the socket on this edge, renders own bubbles by USER id and appends live frames', async () => {
    renderPage(<Thread />, '/messages/:id', '/messages/lst-ann-fixed-9-1');
    await waitFor(() => expect(bubbles()).toHaveLength(2));
    expect(bubbles()[0]).toHaveTextContent('Is the bike still available?');
    expect(bubbles()[0].className).toContain('mine');
    expect(bubbles()[1].className).toContain('theirs');
    expect(screen.getByText('Conversation about ad ann-fixed-9')).toHaveAttribute('href', '/announce/ann-fixed-9');
    expect(screen.getByText('with member #2')).toBeInTheDocument();
    const ws = FakeWebSocket.last();
    expect(ws.url).toBe('ws://localhost/ws/conversations/lst-ann-fixed-9-1?access_token=t');
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('live'));
    await waitFor(() => expect(ws.sentFrames()).toContainEqual({ type: 'read', seq: 2 }));
    act(() => ws.push(liveFrame('lst-ann-fixed-9-1', 3, 2, 'Still here?')));
    expect(bubbles()).toHaveLength(3);
    expect(bubbles()[2]).toHaveTextContent('Still here?');
    act(() => ws.push(liveFrame('lst-ann-fixed-9-1', 3, 2, 'Still here?'))); // the same frame again (reconnect replay)
    expect(bubbles()).toHaveLength(3);
    await waitFor(() => expect(ws.sentFrames()).toContainEqual({ type: 'read', seq: 3 }));
  });

  it('sends a text as a post frame and shows it once when the server echoes it with the client message id', async () => {
    renderPage(<Thread />, '/messages/:id', '/messages/lst-ann-fixed-9-1');
    await waitFor(() => expect(bubbles()).toHaveLength(2));
    const ws = FakeWebSocket.last();
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('live'));
    fireEvent.change(composer(), {
      target: { value: 'Can I come by tonight?' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    const post = ws.sentFrames().find(f => f.type === 'post')!;
    expect(post).toMatchObject({
      type: 'post',
      contentType: 'TEXT',
      content: 'Can I come by tonight?',
    });
    expect(typeof post.clientMsgId).toBe('string');
    expect(composer().value).toBe('');
    act(() =>
      ws.push({
        type: 'ack',
        action: 'post',
        conversationId: 'lst-ann-fixed-9-1',
        messageId: 'lst-ann-fixed-9-1-m3',
        seq: 3,
        clientMsgId: post.clientMsgId,
        duplicate: false,
        at: '2026-10-11T12:00:00',
      })
    );
    act(() => ws.push(liveFrame('lst-ann-fixed-9-1', 3, 1, 'Can I come by tonight?', post.clientMsgId as string)));
    await waitFor(() => expect(bubbles()).toHaveLength(3));
    expect(bubbles()[2].className).toContain('mine');
    expect(chatDb.messages.get('lst-ann-fixed-9-1')).toHaveLength(2); // nothing went over REST
  });

  it('falls back to REST while the socket is not open', async () => {
    FakeWebSocket.autoOpen = false;
    renderPage(<Thread />, '/messages/:id', '/messages/lst-ann-fixed-9-1');
    await waitFor(() => expect(bubbles()).toHaveLength(2));
    expect(screen.getByTestId('socket-state')).toHaveTextContent('connecting');
    fireEvent.change(composer(), { target: { value: 'Sent over REST' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    await waitFor(() => expect(bubbles()).toHaveLength(3));
    expect(bubbles()[2]).toHaveTextContent('Sent over REST');
    expect(FakeWebSocket.last().sentFrames()).toEqual([]);
    const stored = chatDb.messages.get('lst-ann-fixed-9-1')!;
    expect(stored).toHaveLength(3);
    expect(stored[2]).toMatchObject({
      senderType: 'USER',
      senderId: 1,
      contentType: 'TEXT',
      content: 'Sent over REST',
    });
  });

  it('lets the buyer of a listing send an offer frame in cents', async () => {
    renderPage(<Thread />, '/messages/:id', '/messages/lst-ann-fixed-9-1');
    await waitFor(() => expect(bubbles()).toHaveLength(2));
    const ws = FakeWebSocket.last();
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('live'));
    fireEvent.change(screen.getByLabelText('Offer'), {
      target: { value: '300' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send offer' }));
    expect(ws.sentFrames().find(f => f.type === 'offer')).toMatchObject({
      type: 'offer',
      amountCents: 30000,
      currency: 'DKK',
    });
    chatDb.conversations.get('lst-ann-fixed-9-1')!.openOffer = { messageId: 'lst-ann-fixed-9-1-m3', amount: 300, currency: 'DKK', at: '2026-10-11T12:00:00' };
    act(() =>
      ws.push({
        type: 'message',
        conversationId: 'lst-ann-fixed-9-1',
        messageId: 'lst-ann-fixed-9-1-m3',
        seq: 3,
        senderType: 'USER',
        senderId: 1,
        contentType: 'OFFER',
        content: null,
        attachmentUrl: null,
        offerAmountCents: 30000,
        currency: 'DKK',
        clientMsgId: null,
        sentAt: '2026-10-11T12:00:00',
      })
    );
    expect(bubbles()[2]).toHaveTextContent(/Offer: .*300/);
    expect(bubbles()[2].className).toContain('mine');
    // the frame also refetches the row: with the offer open, the buyer sees it pending and the offer composer goes away
    await waitFor(() => expect(screen.getByText(/Your offer of .*300.* is waiting for the seller\./)).toBeInTheDocument());
    expect(screen.queryByLabelText('Offer')).not.toBeInTheDocument();
  });

  it('shows the seller the open offer, accepts it over REST and marks the offer line', async () => {
    renderPage(<Thread />, '/messages/:id', '/messages/lst-ann-goods-4-3');
    await waitFor(() => expect(bubbles()).toHaveLength(2));
    expect(screen.getByText('with member #3')).toBeInTheDocument();
    expect(screen.queryByLabelText('Offer')).not.toBeInTheDocument(); // the seller makes no offers
    const box = screen.getByTestId('open-offer');
    expect(box).toHaveTextContent(/Open offer of .*450/);
    fireEvent.click(within(box).getByRole('button', { name: 'Accept' }));
    await waitFor(() => expect(screen.queryByTestId('open-offer')).not.toBeInTheDocument());
    expect(bubbles()[1]).toHaveTextContent('(accepted)');
    expect(chatDb.conversations.get('lst-ann-goods-4-3')!.acceptedOffer).toMatchObject({ amount: 450, currency: 'DKK' });
    expect(chatDb.messages.get('lst-ann-goods-4-3')).toHaveLength(2); // accepting posts no message
  });

  it('refetches on a status frame: a CLOSED conversation disables the composer', async () => {
    renderPage(<Thread />, '/messages/:id', '/messages/lst-ann-fixed-9-1');
    await waitFor(() => expect(bubbles()).toHaveLength(2));
    const ws = FakeWebSocket.last();
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('live'));
    expect(composer()).toBeEnabled();
    closeConversation('lst-ann-fixed-9-1', 2);
    act(() =>
      ws.push({
        type: 'status',
        conversationId: 'lst-ann-fixed-9-1',
        change: 'CLOSED',
        status: 'CLOSED',
        messageId: null,
        amountCents: null,
        currency: null,
        participantType: 'USER',
        participantId: 2,
        at: '2026-10-11T12:00:00',
      })
    );
    await waitFor(() => expect(screen.getByTestId('conversation-status')).toHaveTextContent('closed'));
    expect(composer()).toBeDisabled();
    expect(screen.getByText('The conversation was closed.')).toBeInTheDocument();
    expect(screen.getByText('This conversation is closed.')).toBeInTheDocument();
  });

  it('lets the member write in a WAITING support conversation and shows the agent once a JOINED frame arrives', async () => {
    renderPage(<Thread />, '/messages/:id', '/messages/sup-1-waiting');
    await waitFor(() => expect(bubbles()).toHaveLength(1));
    expect(screen.getByText('Payment stuck on pending')).toBeInTheDocument();
    expect(screen.getByTestId('conversation-status')).toHaveTextContent('waiting for support');
    expect(screen.getByText('Waiting for a support agent to join. You can already write.')).toBeInTheDocument();
    expect(composer()).toBeEnabled();
    expect(screen.queryByLabelText('Offer')).not.toBeInTheDocument();
    const ws = FakeWebSocket.last();
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('live'));
    const row = chatDb.conversations.get('sup-1-waiting')!;
    row.status = 'OPEN';
    row.participants.push({
      type: 'ADMIN',
      id: 5,
      role: 'CSR',
      lastReadSeq: 0,
      joinedAt: '2026-10-11T12:00:00',
    });
    act(() =>
      ws.push({
        type: 'status',
        conversationId: 'sup-1-waiting',
        change: 'JOINED',
        status: 'OPEN',
        messageId: null,
        amountCents: null,
        currency: null,
        participantType: 'ADMIN',
        participantId: 5,
        at: '2026-10-11T12:00:00',
      })
    );
    await waitFor(() => expect(screen.getByText('Support agent #5')).toBeInTheDocument());
    expect(screen.getByText('A support agent joined the conversation.')).toBeInTheDocument();
    act(() =>
      ws.push({
        ...liveFrame('sup-1-waiting', 2, 5, 'Hello, looking at it now.'),
        senderType: 'ADMIN',
      })
    );
    expect(bubbles()[1]).toHaveTextContent('Support');
    expect(bubbles()[1].className).toContain('theirs');
  });

  it('renders a closed support conversation read-only with the staff and system lines', async () => {
    renderPage(<Thread />, '/messages/:id', '/messages/sup-1-closed');
    await waitFor(() => expect(bubbles()).toHaveLength(3));
    expect(bubbles().map(b => b.dataset.sender)).toEqual(['USER', 'ADMIN', 'SYSTEM']);
    expect(bubbles()[2].className).toContain('system');
    expect(screen.getByText('Support agent #1')).toBeInTheDocument();
    expect(composer()).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
    expect(screen.getByText('This conversation is closed.')).toBeInTheDocument();
  });

  it('retries the row quietly when it is not projected yet (just-opened conversation) and enables the composer once it is', async () => {
    server.use(
      http.get(
        'http://localhost/api/conversations/:id',
        () => HttpResponse.json({ status: 404, title: 'Not Found', code: 'conversation-not-found', detail: 'Conversation x does not exist' }, { status: 404 }),
        { once: true }
      )
    );
    renderPage(<Thread />, '/messages/:id', '/messages/lst-ann-fixed-9-1');
    await waitFor(() => expect(bubbles()).toHaveLength(2));
    expect(composer()).toBeDisabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await waitFor(() => expect(composer()).toBeEnabled(), { timeout: 3000 });
    expect(screen.getByTestId('conversation-status')).toHaveTextContent('open');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
