import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import ContactSeller from './ContactSeller';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { AnnounceResponse } from 'app/shared/model/announce';

useMockServer();

const announce = (id: string) =>
  ({
    id,
    sellerId: 2,
    status: 'PUBLISHED',
    kind: 'GOODS',
    title: 'Brompton',
  }) as unknown as AnnounceResponse;

describe('ContactSeller', () => {
  it('opens the listing conversation with {type: LISTING, announceId} and goes to the thread', async () => {
    let sent: unknown = null;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/api/conversations')) sent = await request.clone().json();
    });
    renderPage(<ContactSeller announce={announce('ann-fixed-9')} />, '/announce/ann-fixed-9');
    fireEvent.click(screen.getByRole('button', { name: /Message the seller/ }));
    await screen.findByTestId('elsewhere');
    expect(sent).toEqual({ type: 'LISTING', announceId: 'ann-fixed-9' });
  });

  it("shows messaging's problem detail when the announce is the member's own (409 buyer-is-seller)", async () => {
    renderPage(<ContactSeller announce={announce('mine-3')} />, '/announce/mine-3');
    fireEvent.click(screen.getByRole('button', { name: /Message the seller/ }));
    expect(await screen.findByText('You cannot open a conversation about your own announce')).toBeInTheDocument();
    expect(screen.queryByTestId('elsewhere')).not.toBeInTheDocument();
  });

  it('shows the detail of a 404 announce-not-found from a handler override', async () => {
    server.use(
      http.post('/api/conversations', () =>
        HttpResponse.json(
          {
            status: 404,
            title: 'Not Found',
            code: 'announce-not-found',
            detail: 'Announce x does not exist',
          },
          { status: 404 }
        )
      )
    );
    renderPage(<ContactSeller announce={announce('x')} />, '/announce/x');
    fireEvent.click(screen.getByRole('button', { name: /Message the seller/ }));
    expect(await screen.findByText('Announce x does not exist')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: /Message the seller/ })).toBeEnabled());
  });
});
