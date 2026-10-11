import { fireEvent, screen } from '@testing-library/react';
import BuyBox from './BuyBox';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { AnnounceResponse } from 'app/shared/model/announce';
import { tradeDb } from 'app/mocks/tradeData';

useMockServer();

const announce = (id: string, saleType = 'FIXED_PRICE') =>
  ({ id, sellerId: 2, status: 'PUBLISHED', kind: 'GOODS', title: 'x', goods: { saleType } }) as unknown as AnnounceResponse;

describe('BuyBox', () => {
  it('requests the purchase with {announceId} and goes to the new PROPOSED order', async () => {
    let sent: unknown = null;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/api/orders')) sent = await request.clone().json();
    });
    renderPage(<BuyBox announce={announce('ann-fixed-9')} />, '/announce/ann-fixed-9');
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }));
    await screen.findByTestId('elsewhere');
    expect(sent).toEqual({ announceId: 'ann-fixed-9' });
    const created = [...tradeDb.orders.values()].find(o => o.announceId === 'ann-fixed-9' && o.id !== 'ord-fixed-1')!;
    expect(created).toMatchObject({ status: 'PROPOSED', buyerId: 1, sellerId: 2 });
  });

  it('labels a giveaway "Request it" and shows the 409 own-announce detail', async () => {
    renderPage(<BuyBox announce={announce('mine-3', 'GIVEAWAY')} />, '/announce/mine-3');
    fireEvent.click(screen.getByRole('button', { name: 'Request it' }));
    expect(await screen.findByText('A member cannot buy their own announce')).toBeInTheDocument();
    expect(screen.queryByTestId('elsewhere')).not.toBeInTheDocument();
  });
});
