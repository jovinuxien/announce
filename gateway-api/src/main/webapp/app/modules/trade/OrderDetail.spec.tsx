import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import OrderDetail from './OrderDetail';
import { http, HttpResponse } from 'msw';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { carrierDispatched, onboard, tradeDb } from 'app/mocks/tradeData';

useMockServer();

const status = () => screen.getByTestId('order-status');
const actions = () => screen.getByTestId('order-actions');
const timelineKeys = () => Array.from(screen.getByTestId('timeline').querySelectorAll('strong')).map(el => el.textContent);

describe('OrderDetail', () => {
  it('shows the buyer a PROPOSED order waiting for the seller, with cancel and no payment or shipment yet', async () => {
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-fixed-1');
    await screen.findByTestId('order-status');
    expect(status()).toHaveTextContent('Proposed');
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Trek FX 3 hybrid bike, size M');
    expect(screen.getByText(/Seller #2/)).toBeInTheDocument();
    expect(actions()).toHaveTextContent('Waiting for the seller to accept.');
    expect(within(actions()).getByRole('button', { name: 'Cancel' })).toBeEnabled();
    expect(screen.getByTestId('payment-card')).toHaveTextContent('No payment started.');
    expect(screen.getByTestId('shipment-card')).toHaveTextContent('Not shipped yet.');
    expect(screen.getByTestId('timeline')).toHaveTextContent('Nothing happened yet.');
    fireEvent.click(within(actions()).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(status()).toHaveTextContent('Cancelled'));
    expect(timelineKeys()).toEqual(['Closed']);
  });

  it("refuses the seller's accept with seller-not-payable until the payout account exists, then moves to awaiting payment with the intent", async () => {
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-sell-2');
    await screen.findByTestId('order-status');
    expect(screen.getByText(/Buyer #3/)).toBeInTheDocument();
    expect(screen.queryByText(/you receive/)).not.toBeInTheDocument(); // no fee until the deal is agreed
    fireEvent.click(within(actions()).getByRole('button', { name: 'Accept' }));
    expect(await screen.findByText('The seller has no payout account yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Set up your payout account first.' })).toHaveAttribute('href', '/orders?role=seller');
    onboard(1);
    fireEvent.click(within(actions()).getByRole('button', { name: 'Accept' }));
    await waitFor(() => expect(status()).toHaveTextContent('Awaiting payment'));
    expect(screen.getByText(/you receive .*41[78].*fee .*3[23]/)).toBeInTheDocument();
    expect(actions()).toHaveTextContent("Waiting for the buyer's payment.");
    expect(screen.getByTestId('payment-card')).toHaveTextContent('awaiting payment');
    expect(screen.getByTestId('payment-card')).toHaveTextContent('Reference pi_ord-sell-2');
    expect(timelineKeys()).toEqual(['Agreed']);
  });

  it('lets the seller book the carrier on a PAID order with the four fields and shows the booked shipment', async () => {
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-ship-5');
    await screen.findByTestId('order-status');
    expect(status()).toHaveTextContent('Paid');
    expect(screen.getByTestId('payment-card')).toHaveTextContent('confirmed');
    const form = screen.getByTestId('shipment-form');
    fireEvent.change(within(form).getByLabelText('Service'), { target: { value: 'MyPack Home' } });
    fireEvent.change(within(form).getByLabelText('Parcel size'), { target: { value: 'L' } });
    fireEvent.change(within(form).getByLabelText('From address'), { target: { value: 'Vamdrup 6580' } });
    fireEvent.change(within(form).getByLabelText('To address'), { target: { value: 'København 2100' } });
    fireEvent.click(within(form).getByRole('button', { name: 'Book the carrier' }));
    await waitFor(() => expect(screen.getByTestId('shipment-card')).toHaveTextContent('Booked'));
    expect(screen.queryByTestId('shipment-form')).not.toBeInTheDocument();
    expect(screen.getByTestId('shipment-card')).toHaveTextContent('fake · MyPack Home');
    expect(screen.getByRole('link', { name: 'Shipping label' })).toHaveAttribute('href', 'https://fake-carrier.invalid/labels/ord-ship-5.pdf');
    expect(tradeDb.shipments.get('ord-ship-5')).toMatchObject({ status: 'REQUESTED', service: 'MyPack Home', parcelSize: 'L' });
    expect(status()).toHaveTextContent('Paid'); // the carrier's dispatch moves the order, not the booking
  });

  it('shows the buyer the pay hint while awaiting payment and, once paid, the dispute form but no cancel or receipt button', async () => {
    tradeDb.orders.get('ord-paid-3')!.status = 'PAYMENT_PENDING';
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-paid-3');
    await screen.findByTestId('order-status');
    expect(screen.getByTestId('pay-hint')).toHaveTextContent('Waiting for your payment through fake.');
    expect(screen.queryByLabelText(/Problem with the order/)).not.toBeInTheDocument();
    tradeDb.orders.get('ord-paid-3')!.status = 'PAID'; // the provider webhook landed meanwhile
    fireEvent.click(within(actions()).getByRole('button', { name: 'Cancel' }));
    expect(await screen.findByText(/A paid order cannot be cancelled/)).toBeInTheDocument();
    await waitFor(() => expect(status()).toHaveTextContent('Paid')); // the refusal refetched the row
    expect(screen.getByLabelText(/Problem with the order/)).toBeInTheDocument();
    expect(within(actions()).queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
    expect(within(actions()).queryByRole('button', { name: 'Confirm receipt' })).not.toBeInTheDocument();
  });

  it('lets the buyer confirm receipt of a SHIPPED order, which settles it and pays the seller out', async () => {
    tradeDb.shipments.get('ord-paid-3')!.status = 'REQUESTED';
    carrierDispatched('ord-paid-3');
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-paid-3');
    await screen.findByTestId('order-status');
    expect(status()).toHaveTextContent('Shipped');
    expect(screen.getByTestId('shipment-card')).toHaveTextContent('Dispatched');
    expect(screen.queryByRole('link', { name: 'Shipping label' })).not.toBeInTheDocument(); // buyers do not get the label
    fireEvent.click(within(actions()).getByRole('button', { name: 'Confirm receipt' }));
    await waitFor(() => expect(status()).toHaveTextContent('Settled'));
    expect(timelineKeys()).toEqual(['Agreed', 'Paid', 'Shipped', 'Receipt confirmed', 'Payout released', 'Settled']);
    expect(screen.getByTestId('payment-card')).toHaveTextContent('paid out');
    expect(within(actions()).queryAllByRole('button')).toHaveLength(0);
  });

  it('opens a dispute with the reason and renders a settled order read-only with its full timeline', async () => {
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-settled-4');
    await screen.findByTestId('order-status');
    expect(status()).toHaveTextContent('Settled');
    expect(within(actions()).queryAllByRole('button')).toHaveLength(0);
    expect(timelineKeys()).toEqual(['Agreed', 'Paid', 'Shipped', 'Delivered', 'Receipt confirmed', 'Payout released', 'Settled']);
    expect(screen.getByTestId('timeline')).toHaveTextContent('FK-SETTLED-4');
    expect(screen.getByTestId('shipment-card')).toHaveTextContent('Delivered to parcel shop');
    // a paid order of mine can be disputed
    const paid = tradeDb.orders.get('ord-paid-3')!;
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-paid-3');
    await waitFor(() => expect(screen.getAllByTestId('order-status')[1]).toHaveTextContent('Paid'));
    const form = screen.getByLabelText(/Problem with the order/);
    fireEvent.change(form, { target: { value: 'The parcel never arrived at the shop.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Open a dispute' }));
    await waitFor(() => expect(screen.getAllByTestId('order-status')[1]).toHaveTextContent('Disputed'));
    expect(paid).toMatchObject({ status: 'DISPUTED', disputeReason: 'The parcel never arrived at the shop.' });
    expect(screen.getByText('Dispute open: The parcel never arrived at the shop.')).toBeInTheDocument();
  });

  it('retries a just-created order quietly until the projection has written it', async () => {
    server.use(
      http.get('http://localhost/api/orders/:id', () => HttpResponse.json({ status: 404, title: 'Not Found', detail: 'No order ord-fixed-1' }, { status: 404 }), { once: true })
    );
    renderPage(<OrderDetail />, '/orders/:id', '/orders/ord-fixed-1');
    await new Promise(r => setTimeout(r, 300));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByTestId('order-status')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('order-status')).toHaveTextContent('Proposed'), { timeout: 3000 });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
