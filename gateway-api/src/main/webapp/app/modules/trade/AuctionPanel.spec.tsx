import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import AuctionPanel from './AuctionPanel';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { AnnounceResponse } from 'app/shared/model/announce';
import { tradeDb } from 'app/mocks/tradeData';

useMockServer();

const announce = (id: string) => ({ id, sellerId: 2, status: 'PUBLISHED', kind: 'GOODS', title: 'x', goods: { saleType: 'AUCTION' } }) as unknown as AnnounceResponse;
const panel = () => screen.getByTestId('auction-panel');

describe('AuctionPanel', () => {
  it('shows the auction row: price, bids, countdown, minimum next bid from the increment', async () => {
    renderPage(<AuctionPanel announce={announce('ann-auction-7')} mine={false} />, '/announce/ann-auction-7');
    await screen.findByTestId('auction-price');
    expect(screen.getByTestId('auction-price')).toHaveTextContent(/120/);
    expect(screen.getByTestId('auction-status')).toHaveTextContent('open');
    expect(panel()).toHaveTextContent('1 bid');
    expect(panel()).toHaveTextContent(/ends in \d+d/);
    expect(panel()).toHaveTextContent(/Minimum .*130.*increment .*10/);
    expect(screen.queryByTestId('my-bid')).not.toBeInTheDocument();
  });

  it('refuses a bid below the minimum with the backend detail and accepts one at the minimum (cents on the wire, row refetched)', async () => {
    renderPage(<AuctionPanel announce={announce('ann-auction-7')} mine={false} />, '/announce/ann-auction-7');
    await screen.findByTestId('auction-price');
    fireEvent.change(screen.getByLabelText('Bid amount'), { target: { value: '125' } });
    fireEvent.submit(screen.getByLabelText('Bid amount').closest('form')!); // the input's own min would stop a click in a browser
    expect(await screen.findByText(/The bid must be at least 13000 minor units/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Bid amount'), { target: { value: '130' } });
    fireEvent.click(screen.getByRole('button', { name: 'Bid' }));
    await waitFor(() => expect(screen.getByTestId('auction-price')).toHaveTextContent(/130/));
    expect(screen.getByTestId('my-bid')).toHaveTextContent(/Your bid: .*130.*leading/);
    expect(panel()).toHaveTextContent('2 bids');
    expect(tradeDb.bids.get('ann-auction-7')!.map(b => `${b.bidderId}:${b.amount}:${b.status}`)).toEqual(['3:120:OUTBID', '1:130:ACTIVE']);
    fireEvent.click(screen.getByRole('button', { name: 'Retract my bid' }));
    await waitFor(() => expect(screen.getByTestId('auction-price')).toHaveTextContent(/120/));
    expect(screen.queryByTestId('my-bid')).not.toBeInTheDocument();
  });

  it('shows the owner the panel without a bid form', async () => {
    renderPage(<AuctionPanel announce={announce('ann-auction-7')} mine />, '/announce/ann-auction-7');
    await screen.findByTestId('auction-price');
    expect(screen.queryByLabelText('Bid amount')).not.toBeInTheDocument();
    expect(panel()).toHaveTextContent('This is your auction.');
  });

  it('says the auction is not open yet when the order module has no row (seller not payable)', async () => {
    renderPage(<AuctionPanel announce={announce('ann-no-auction')} mine={false} />, '/announce/ann-no-auction');
    expect(await screen.findByText('The auction is not open yet.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows a closed auction with the outcome and no form', async () => {
    renderPage(<AuctionPanel announce={announce('ann-auction-8')} mine={false} />, '/announce/ann-auction-8');
    await screen.findByTestId('auction-price');
    expect(screen.getByTestId('auction-status')).toHaveTextContent('sold');
    expect(within(panel()).getByText('You won this auction.')).toBeInTheDocument();
    expect(screen.getByTestId('my-bid')).toHaveTextContent(/3.200.*won/);
    expect(screen.queryByLabelText('Bid amount')).not.toBeInTheDocument();
  });

  it('asks a visitor to log in instead of calling the auction route, which needs a member token', async () => {
    let calls = 0;
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/api/auctions/')) calls += 1;
    });
    renderPage(<AuctionPanel announce={announce('ann-auction-7')} mine={false} />, '/announce/ann-auction-7', '/announce/ann-auction-7', { anonymous: true });
    expect(await screen.findByText('Log in to bid.')).toBeInTheDocument();
    expect(screen.queryByTestId('auction-price')).not.toBeInTheDocument();
    expect(calls).toBe(0);
  });
});
