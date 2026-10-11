import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import SupportThreadPage from './SupportThreadPage';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { FakeWebSocket, useFakeWebSocket } from 'app/test/fakeWebSocket';
import { conversations } from 'app/mocks/chatFixtures';

useMockServer();
useFakeWebSocket();

const messagesOf = () => Array.from(screen.getByTestId('thread-messages').querySelectorAll('[data-sender]'));

describe('SupportThreadPage', () => {
  it('shows the history and the participants, attaches to the conversation socket and appends pushed messages', async () => {
    renderPage(<SupportThreadPage />, '/support/:id', '/support/sup-88-c3');
    expect(await screen.findByText('Thanks, that solved it')).toBeInTheDocument();
    expect(messagesOf().map((el) => el.getAttribute('data-sender'))).toEqual(['USER', 'SYSTEM', 'ADMIN', 'USER', 'ADMIN', 'USER']);
    expect(screen.getByRole('heading', { name: 'Cannot change my e-mail' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '#88' })).toHaveAttribute('href', '/users/88');
    expect(screen.getAllByText('You').length).toBe(2); // staff #1 is the signed-in admin

    const ws = FakeWebSocket.last();
    expect(ws.url).toBe('ws://localhost/ws/conversations/sup-88-c3?access_token=t');
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('Live'));
    await waitFor(() => expect(ws.sentFrames()).toContainEqual({ type: 'read', seq: 6 })); // read marker once joined

    act(() => {
      ws.push({ type: 'message', conversationId: 'sup-88-c3', messageId: 'sup-88-c3-m7', seq: 7, senderType: 'USER', senderId: 88, contentType: 'TEXT', content: 'One more thing', attachmentUrl: null, offerAmountCents: null, currency: null, clientMsgId: null, sentAt: '2026-10-11T10:00:00' });
      ws.push({ type: 'message', conversationId: 'sup-88-c3', messageId: 'sup-88-c3-m7', seq: 7, senderType: 'USER', senderId: 88, contentType: 'TEXT', content: 'One more thing', attachmentUrl: null, offerAmountCents: null, currency: null, clientMsgId: null, sentAt: '2026-10-11T10:00:00' });
    });
    expect(screen.getAllByText('One more thing')).toHaveLength(1); // de-duplicated on the message id
    expect(messagesOf()).toHaveLength(7);
  });

  it('posts a reply through the socket when it is open', async () => {
    renderPage(<SupportThreadPage />, '/support/:id', '/support/sup-88-c3');
    await screen.findByText('Thanks, that solved it');
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('Live'));
    fireEvent.change(screen.getByLabelText('Reply'), { target: { value: 'Glad to hear it' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    const post = FakeWebSocket.last().sentFrames().find((f) => f.type === 'post');
    expect(post).toMatchObject({ type: 'post', contentType: 'TEXT', content: 'Glad to hear it' });
    expect(typeof post?.clientMsgId).toBe('string');
    expect(screen.getByLabelText('Reply')).toHaveValue('');
  });

  it('falls back to the REST route when the socket never opens', async () => {
    FakeWebSocket.autoOpen = false;
    renderPage(<SupportThreadPage />, '/support/:id', '/support/sup-88-c3');
    await screen.findByText('Thanks, that solved it');
    fireEvent.change(screen.getByLabelText('Reply'), { target: { value: 'Posted over REST' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(await screen.findByText('Posted over REST')).toBeInTheDocument();
    expect(FakeWebSocket.last().sent).toHaveLength(0);
  });

  it('a status frame refetches the row; close ends the conversation', async () => {
    renderPage(<SupportThreadPage />, '/support/:id', '/support/sup-07-b2');
    expect(await screen.findByText('The bike was fine when I shipped it.')).toBeInTheDocument();
    expect(screen.getByText('Join the conversation to reply.')).toBeInTheDocument(); // staff #1 has not joined
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('Live'));

    const row = conversations.find((c) => c.id === 'sup-07-b2')!;
    server.use(http.get('http://localhost/api/conversations/:id', () => HttpResponse.json({ ...row, status: 'OPEN', participants: [...row.participants, { type: 'ADMIN', id: 2, role: 'CSR', lastReadSeq: 0, joinedAt: '2026-10-11T10:00:00' }], unread: null })));
    act(() => {
      FakeWebSocket.last().push({ type: 'status', conversationId: 'sup-07-b2', change: 'JOINED', status: 'OPEN', participantType: 'ADMIN', participantId: 2, at: '2026-10-11T10:00:00' });
    });
    expect(await screen.findByText('Staff #2')).toBeInTheDocument();
    server.resetHandlers();

    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(await screen.findByText('This conversation is Closed.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Reply')).not.toBeInTheDocument();
  });

  it('join from the conversation enables the composer', async () => {
    renderPage(<SupportThreadPage />, '/support/:id', '/support/sup-42-a1');
    expect(await screen.findByText(/I paid order ord-3004/)).toBeInTheDocument();
    expect(screen.getByText('Join the conversation to reply.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Reply')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Join' }));
    expect(await screen.findByLabelText('Reply')).toBeInTheDocument();
    expect(screen.getByText('Staff #1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Join' })).not.toBeInTheDocument();
  });

  it('block takes a reason and the conversation becomes read-only', async () => {
    renderPage(<SupportThreadPage />, '/support/:id', '/support/sup-42-a1'); // OPEN and joined since the previous test
    expect(await screen.findByLabelText('Reply')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Block' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Reason'), { target: { value: 'Spam' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Block' }));
    expect(await screen.findByText('This conversation is Blocked.')).toBeInTheDocument();
    expect(screen.getByText(/Blocked: Spam/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Reply')).not.toBeInTheDocument();
  });

  it('a blocked conversation offers neither join nor the staff actions', async () => {
    renderPage(<SupportThreadPage />, '/support/:id', '/support/sup-13-e5');
    expect(await screen.findByText('This conversation is Blocked.')).toBeInTheDocument();
    expect(screen.getByText(/Blocked: Abusive language/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Join' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
  });
});
