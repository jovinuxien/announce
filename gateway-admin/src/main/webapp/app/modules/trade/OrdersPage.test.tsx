import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import OrdersPage from './OrdersPage';
import { renderTradePage, useTradeServer } from './testUtils';

useTradeServer();

describe('OrdersPage', () => {
  it('lists the order rows by title with decimal amounts, the dispute reason and the DECLINED status', async () => {
    renderTradePage(<OrdersPage />, '/orders');
    expect(await screen.findByText('Brompton M6L 2019')).toBeInTheDocument();
    const row = screen.getByText('Brompton M6L 2019').closest('tr')!;
    expect(within(row).getByText('Disputed')).toBeInTheDocument();
    expect(within(row).getByText('Frame has a crack not shown in the pictures')).toBeInTheDocument();
    expect(within(row).getByText(/9[,.]?500/)).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Declined')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Brompton M6L 2019' })).toHaveAttribute('href', '/orders/ord-3001');
  });

  it('applies the seller, buyer and title filters on submit', async () => {
    renderTradePage(<OrdersPage />, '/orders');
    await screen.findByText('Brompton M6L 2019');
    fireEvent.change(screen.getByLabelText('Buyer id'), { target: { value: '42' } });
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'bike' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(screen.queryByText('Brompton M6L 2019')).not.toBeInTheDocument());
    expect(screen.getByText('Kids bike 20"')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
    expect(await screen.findByText('Brompton M6L 2019')).toBeInTheDocument();
  });
});
