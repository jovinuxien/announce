import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import Orders from './Orders';
import { renderPage, useMockServer } from 'app/test/testUtils';
import { tradeDb } from 'app/mocks/tradeData';

useMockServer();

const rows = () => within(screen.getByTestId('orders-table')).getAllByRole('row').slice(1);

describe('Orders', () => {
  it('lists my purchases newest first with the door, the other member, the decimal amount and the status', async () => {
    renderPage(<Orders />, '/orders');
    await screen.findByTestId('orders-table');
    expect(rows().map(r => r.textContent)).toEqual([
      expect.stringMatching(/Trek FX 3 hybrid bike.*Fixed price.*member #2.*1.250.*Proposed/),
      expect.stringMatching(/Toyota Yaris roof box.*Chat offer.*member #2.*800.*Paid/),
      expect.stringMatching(/Yamaha P-45 digital piano.*Auction.*member #2.*2.100.*Settled/),
    ]);
    expect(within(rows()[0]).getByRole('link')).toHaveAttribute('href', '/orders/ord-fixed-1');
    expect(screen.queryByTestId('payouts-card')).not.toBeInTheDocument();
  });

  it('switches to the sales with ?role=seller and offers to set up payouts, which the fake provider enables at once', async () => {
    renderPage(<Orders />, '/orders', '/orders?role=seller');
    await screen.findByTestId('orders-table');
    expect(rows().map(r => r.textContent)).toEqual([
      expect.stringMatching(/Harry Potter box set.*member #3.*450.*Proposed/),
      expect.stringMatching(/Lego Technic crane.*member #4.*300.*Paid/),
    ]);
    const card = screen.getByTestId('payouts-card');
    expect(card).toHaveTextContent('Buyers can only be accepted once you have a payout account');
    fireEvent.click(within(card).getByRole('button', { name: 'Set up payouts' }));
    await waitFor(() => expect(screen.getByTestId('payouts-state')).toHaveTextContent('Payouts enabled'));
    expect(card).toHaveTextContent('fake · VERIFIED');
    expect(card).toHaveTextContent('Account acct_fake_1');
    expect(tradeDb.accounts.get(1)).toMatchObject({ payoutsEnabled: true });
  });

  it('shows the empty hint without orders', async () => {
    tradeDb.orders.clear();
    renderPage(<Orders />, '/orders');
    expect(await screen.findByText('No orders yet.')).toBeInTheDocument();
  });
});
