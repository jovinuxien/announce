import { orderTimeline } from './orderTimeline';
import { orders, payments, shipments } from 'app/mocks/tradeFixtures';

const byId = (id: string) => orders.find((o) => o.id === id)!;
const paymentOf = (id: string) => payments.find((p) => p.orderId === id) ?? null;
const shipmentOf = (id: string) => shipments.find((s) => s.orderId === id) ?? null;

describe('orderTimeline', () => {
  it('lists every stamped step of a settled order in time order with the other contexts\' references', () => {
    const entries = orderTimeline(byId('ord-3003'), paymentOf('ord-3003'), shipmentOf('ord-3003'));
    expect(entries.map((e) => e.key)).toEqual(['agreed', 'paid', 'shipped', 'delivered', 'receiptConfirmed', 'released', 'settled']);
    expect(entries.map((e) => e.at)).toEqual([...entries.map((e) => e.at)].sort());
    expect(entries.find((e) => e.key === 'paid')?.detail).toBe('pi_fake_3003');
    expect(entries.find((e) => e.key === 'shipped')?.detail).toBe('FK-3003');
    expect(entries.find((e) => e.key === 'released')?.detail).toBe('po_fake_3003');
    expect(entries.find((e) => e.key === 'agreed')?.detail).toBe('OFFER');
  });

  it('shows the dispute with its reason and an in-transit carrier event', () => {
    const disputed = orderTimeline(byId('ord-3001'), paymentOf('ord-3001'), shipmentOf('ord-3001'));
    expect(disputed.map((e) => e.key)).toEqual(['agreed', 'paid', 'shipped', 'delivered', 'disputeOpened']);
    expect(disputed.at(-1)?.detail).toBe('Frame has a crack not shown in the pictures');
    const shipped = orderTimeline(byId('ord-3002'), paymentOf('ord-3002'), shipmentOf('ord-3002'));
    expect(shipped.map((e) => e.key)).toEqual(['agreed', 'paid', 'shipped', 'carrierEvent']);
    expect(shipped.at(-1)?.detail).toBe('Arrived at sorting hub');
  });

  it('works with the order row alone and shows a refund and a close', () => {
    expect(orderTimeline(byId('ord-3004'), null, null).map((e) => e.key)).toEqual(['agreed']);
    const refunded = orderTimeline(byId('ord-3005'), paymentOf('ord-3005'), null);
    expect(refunded.map((e) => e.key)).toEqual(['agreed', 'paid', 'paymentRefunded', 'closed']);
    expect(refunded.find((e) => e.key === 'paymentRefunded')?.detail).toBe('re_fake_3005');
    expect(refunded.find((e) => e.key === 'closed')?.detail).toBe('REFUNDED');
    expect(orderTimeline(byId('ord-3006')).map((e) => e.key)).toEqual(['closed']);
  });
});
