import { screen, within } from '@testing-library/react';
import PayoutsPage from './PayoutsPage';
import { renderPage, useMockServer } from 'app/test/testUtils';

useMockServer();

describe('PayoutsPage', () => {
  it('shows the five totals with their order counts and one line per order that holds money', async () => {
    renderPage(<PayoutsPage />, '/payouts');
    expect(await screen.findByText('Brompton M6L 2019')).toBeInTheDocument();
    const tiles = document.querySelectorAll('.stat-tile');
    expect(tiles).toHaveLength(5);
    expect(tiles[1]).toHaveTextContent(/8[,.]?930/); // blocked by dispute: ord-3001's payout
    expect(tiles[1]).toHaveTextContent('1 orders');
    expect(tiles[3]).toHaveTextContent(/366/); // sent: ord-3003
    expect(screen.getByText(/Fees collected/)).toHaveTextContent('34');
    // ord-3004 (PAYMENT_PENDING) and ord-3006 (DECLINED) hold no money
    expect(screen.queryByText('Bosch cordless drill')).not.toBeInTheDocument();
    expect(screen.queryByText('Standing desk 120 cm')).not.toBeInTheDocument();
    const canon = screen.getByText('Canon EOS 500D body').closest('tr')!;
    expect(within(canon).getByText('Pending release')).toBeInTheDocument();
    expect(within(canon).getByText('Shipped')).toBeInTheDocument(); // the order status under the payout status
    expect(within(canon).getByText('Payouts disabled')).toBeInTheDocument(); // seller 12's gate
  });

  it('filters by payout status while the totals stay global', async () => {
    renderPage(<PayoutsPage />, '/payouts', '/payouts?status=SENT');
    expect(await screen.findByText('IKEA Poäng + footstool')).toBeInTheDocument();
    expect(screen.queryByText('Brompton M6L 2019')).not.toBeInTheDocument();
    expect(document.querySelectorAll('.stat-tile')[1]).toHaveTextContent(/8[,.]?930/);
  });
});
