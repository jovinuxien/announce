import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import Inbox from './Inbox';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { chatDb } from 'app/mocks/chatData';

useMockServer();

describe('Inbox', () => {
  it('lists my conversations latest first with status, the other member and the unread count', async () => {
    renderPage(<Inbox />, '/messages');
    const list = await screen.findByTestId('inbox-list');
    const rows = within(list).getAllByRole('link');
    expect(rows.map(r => r.textContent)).toEqual([
      expect.stringContaining('Payment stuck on pending'),
      expect.stringContaining('Ad ann-goods-4'),
      expect.stringContaining('Ad ann-fixed-9'),
      expect.stringContaining('Refund timing'),
    ]);
    expect(rows[0]).toHaveTextContent('waiting for support');
    expect(rows[1]).toHaveTextContent('with member #3');
    expect(rows[1]).toHaveTextContent('Open offer');
    expect(within(rows[1]).getByTestId('unread')).toHaveTextContent('2');
    expect(rows[2]).toHaveTextContent('with member #2');
    expect(within(rows[2]).getByTestId('unread')).toHaveTextContent('1');
    expect(rows[3]).toHaveTextContent('closed');
    expect(within(rows[3]).queryByTestId('unread')).not.toBeInTheDocument();
    expect(rows[2]).toHaveAttribute('href', '/messages/lst-ann-fixed-9-1');
  });

  it('opens a SUPPORT conversation with the subject, posts the first message and goes to the thread', async () => {
    const posts: Array<{ url: string; body: unknown }> = [];
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST')
        posts.push({
          url: new URL(request.url).pathname,
          body: await request.clone().json(),
        });
    });
    renderPage(<Inbox />, '/messages');
    await screen.findByTestId('inbox-list');
    const start = screen.getByRole('button', { name: 'Send to support' });
    expect(start).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Subject'), {
      target: { value: 'Cannot change my e-mail' },
    });
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'The profile page rejects my new address.' },
    });
    expect(start).toBeEnabled();
    fireEvent.click(start);
    await screen.findByTestId('elsewhere');
    expect(posts[0]).toEqual({
      url: '/api/conversations',
      body: { type: 'SUPPORT', subject: 'Cannot change my e-mail' },
    });
    expect(posts[1].url).toMatch(/^\/api\/conversations\/sup-1-\d+\/messages$/);
    expect(posts[1].body).toMatchObject({
      contentType: 'TEXT',
      content: 'The profile page rejects my new address.',
    });
    const opened = [...chatDb.conversations.values()].find(c => c.subject === 'Cannot change my e-mail')!;
    expect(opened.status).toBe('WAITING');
    expect(chatDb.messages.get(opened.id)).toHaveLength(1);
  });

  it('shows the empty hint when the member has no conversation', async () => {
    chatDb.conversations.clear();
    renderPage(<Inbox />, '/messages');
    expect(await screen.findByText('No conversations yet.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByTestId('inbox-list')).not.toBeInTheDocument());
  });
});
