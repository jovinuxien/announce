import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import OrderDetailPage from './OrderDetailPage';
import { renderPage, server, useMockServer } from 'app/test/testUtils';

useMockServer();

describe('OrderDetailPage', () => {
  it('combines the order, payment and shipment rows and derives the timeline', async () => {
    renderPage(<OrderDetailPage />, '/orders/:id', '/orders/ord-3001');
    expect(await screen.findByText('Brompton M6L 2019')).toBeInTheDocument();
    expect((await screen.findAllByText('pi_fake_3001')).length).toBeGreaterThan(0); // payment row (and the timeline's detail)
    expect((await screen.findAllByText('PN123456789DK')).length).toBeGreaterThan(0); // shipment row
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
    expect(screen.getAllByText('Delivered').length).toBeGreaterThan(0);
    const timeline = screen.getByTestId('order-timeline');
    const steps = Array.from(timeline.querySelectorAll('.list-group-item .fw-semibold')).map((el) => el.textContent);
    expect(steps).toEqual(['Agreed', 'Paid', 'Shipped', 'Delivered', 'Dispute opened']);
    expect(screen.getByRole('button', { name: 'Release payout' })).toBeInTheDocument();
  });

  it('treats a missing payment and shipment as none, not as an error', async () => {
    renderPage(<OrderDetailPage />, '/orders/:id', '/orders/ord-3006');
    expect(await screen.findByText('Standing desk 120 cm')).toBeInTheDocument();
    expect(await screen.findByText('No payment started')).toBeInTheDocument();
    expect(await screen.findByText('No shipment requested')).toBeInTheDocument();
    expect(screen.queryByTestId('problem-alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Release payout' })).not.toBeInTheDocument();
  });

  it('resolves a dispute with {resolution, note} and refetches the row', async () => {
    let sent: unknown = null;
    server.use(
      http.post('http://localhost/api/admin/orders/:id/resolve-dispute', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 'ord-3001', status: 'RELEASING' });
      }),
      http.get('http://localhost/api/admin/orders/:id', ({ params }, ...rest) => {
        void rest;
        const base = { id: params.id, announceId: 'ann-fixed-9', sellerId: 7, buyerId: 42, door: 'FIXED_PRICE', title: 'Brompton M6L 2019', agreedPrice: 9500, feeAmount: 570, payoutAmount: 8930, labelPrice: null, currency: 'DKK' };
        return HttpResponse.json(sent ? { ...base, status: 'RELEASING', disputeReason: null } : { ...base, status: 'DISPUTED', disputeReason: 'Crack' });
      }),
    );
    renderPage(<OrderDetailPage />, '/orders/:id', '/orders/ord-3001');
    fireEvent.click(await screen.findByRole('button', { name: 'Release payout' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText(/Note/), { target: { value: 'Buyer withdrew the complaint' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(sent).toEqual({ resolution: 'RELEASE', note: 'Buyer withdrew the complaint' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByText('Releasing payout')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Release payout' })).not.toBeInTheDocument();
  });
});
