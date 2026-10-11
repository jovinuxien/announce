import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import BulletinsPage from './BulletinsPage';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { FakeWebSocket, useFakeWebSocket } from 'app/test/fakeWebSocket';

useMockServer();
useFakeWebSocket();

const headings = () => Array.from(screen.getByTestId('bulletin-list').querySelectorAll('.fw-semibold')).map((el) => el.textContent);

describe('BulletinsPage', () => {
  it('lists the current staff bulletins newest first and switches audience', async () => {
    renderPage(<BulletinsPage />, '/bulletins');
    await screen.findByTestId('bulletin-list');
    expect(headings()).toEqual(['New PostNord labels', 'Planned maintenance']);
    expect(screen.getByText('Sellers in SE can now buy MyPack Collect labels at checkout.')).toBeInTheDocument();
    expect(within(screen.getByTestId('bulletin-list')).getByText(/^Expires /)).toBeInTheDocument();
    expect(screen.queryByText('Welcome to the new marketplace!')).not.toBeInTheDocument();
    const listCard = screen.getByTestId('bulletin-list').closest('.card') as HTMLElement;
    fireEvent.change(within(listCard).getByLabelText('Audience'), { target: { value: 'MEMBERS' } }); // the list's audience, not the form's
    expect(await screen.findByText('Welcome to the new marketplace!')).toBeInTheDocument();
    expect(headings()).toEqual(['Welcome to the new marketplace!']);
  });

  it('posts {audience, clientType, content, expiresAt} and the new bulletin shows up', async () => {
    let sent: unknown = null;
    server.use(
      http.post('http://localhost/api/admin/bulletins', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 50, adminId: 1, audience: 'STAFF', clientType: 'mobile', content: 'Fraud wave\nWatch out for prepaid-card requests.', expiresAt: '2026-10-12T18:00:00', createdAt: '2026-10-11T09:00:00' }, { status: 201 });
      }),
      http.get('http://localhost/api/admin/bulletins', () =>
        HttpResponse.json(sent ? [{ id: 50, adminId: 1, audience: 'STAFF', clientType: 'mobile', content: 'Fraud wave\nWatch out for prepaid-card requests.', expiresAt: '2026-10-12T18:00:00', createdAt: '2026-10-11T09:00:00' }] : []),
      ),
    );
    renderPage(<BulletinsPage />, '/bulletins');
    expect(await screen.findByText('Nothing to show')).toBeInTheDocument();
    const form = screen.getByRole('button', { name: 'Post' }).closest('form')!;
    fireEvent.change(within(form).getByLabelText('Content'), { target: { value: 'Fraud wave\nWatch out for prepaid-card requests.' } });
    fireEvent.change(within(form).getByLabelText('Client type'), { target: { value: 'mobile' } });
    fireEvent.change(within(form).getByLabelText('Expires (optional)'), { target: { value: '2026-10-12T18:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Post' }));
    await waitFor(() => expect(sent).toEqual({ audience: 'STAFF', clientType: 'mobile', content: 'Fraud wave\nWatch out for prepaid-card requests.', expiresAt: '2026-10-12T18:00:00' }));
    expect(await screen.findByText('Bulletin posted.')).toBeInTheDocument();
    await waitFor(() => expect(headings()).toEqual(['Fraud wave']));
    expect(within(form).getByLabelText('Content')).toHaveValue('');
  });

  it('listens on the staff channel as the web client and prepends a bulletin that arrives live', async () => {
    renderPage(<BulletinsPage />, '/bulletins');
    await screen.findByTestId('bulletin-list');
    const ws = FakeWebSocket.last();
    expect(ws.url).toBe('ws://localhost/ws/staff?clientType=web&access_token=t');
    await waitFor(() => expect(screen.getByTestId('socket-state')).toHaveTextContent('Live'));
    act(() => {
      ws.push({ type: 'bulletin', id: 99, adminId: 3, audience: 'STAFF', clientType: 'web', content: 'Live one\nPushed while the page was open.', expiresAt: null, at: '2026-10-11T10:00:00' });
      ws.push({ type: 'bulletin', id: 98, adminId: 3, audience: 'MEMBERS', clientType: null, content: 'Members only', expiresAt: null, at: '2026-10-11T10:00:00' });
    });
    expect(headings()).toEqual(['Live one', 'New PostNord labels', 'Planned maintenance']);
    expect(screen.queryByText('Members only')).not.toBeInTheDocument();
  });
});
