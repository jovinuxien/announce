import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import AuctionsPage from './AuctionsPage';
import { renderPage, useMockServer } from 'app/test/testUtils';

useMockServer();

describe('AuctionsPage', () => {
  it('lists the auction rows with decimal prices, the real status names and the bids of the selected one', async () => {
    renderPage(<AuctionsPage />, '/auctions');
    expect(await screen.findByText('Vintage Ercol armchair')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Closed, bought now')).toBeInTheDocument(); // CLOSED_SNAGGED
    const row = screen.getByText('Vintage Ercol armchair').closest('tr')!;
    expect(within(row).getByText(/580/)).toBeInTheDocument(); // 580.00 DKK, not 58000 cents
    fireEvent.click(row);
    expect(await screen.findByText('Bids on “Vintage Ercol armchair”')).toBeInTheDocument();
    await screen.findByText('Leading');
    expect(screen.getAllByText('Outbid')).toHaveLength(2);
    expect(screen.getByText(/Increment/)).toHaveTextContent('10');
  });

  it('filters by status through the URL and shows the empty state when nothing matches', async () => {
    renderPage(<AuctionsPage />, '/auctions', '/auctions?status=CANCELLED');
    expect(await screen.findByTestId('empty-state')).toBeInTheDocument();
  });

  it('cancels a bid with a reason and refetches the auction', async () => {
    renderPage(<AuctionsPage />, '/auctions');
    fireEvent.click(await screen.findByText('Vintage Ercol armchair'));
    const leading = await screen.findByText('Leading');
    const bidRow = leading.closest('tr')!;
    fireEvent.click(within(bidRow).getByRole('button', { name: 'Cancel bid' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Reason'), { target: { value: 'Shill bidding' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel bid' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await screen.findByText(/by staff #1/)).toBeInTheDocument();
    expect(within(screen.getByText(/by staff #1/).closest('tr')!).getByText('Cancelled')).toBeInTheDocument();
  });
});
