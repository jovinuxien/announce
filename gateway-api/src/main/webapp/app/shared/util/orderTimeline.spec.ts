import { orderTimeline } from './orderTimeline';
import { tradeDb } from 'app/mocks/tradeData';

describe('orderTimeline', () => {
  it("derives the steps from the three rows' timestamps, sorted, with the details that add something", () => {
    const o = tradeDb.orders.get('ord-settled-4')!;
    const entries = orderTimeline(o, tradeDb.payments.get(o.id), tradeDb.shipments.get(o.id));
    expect(entries.map(e => e.key)).toEqual(['agreed', 'paid', 'shipped', 'delivered', 'receiptConfirmed', 'released', 'settled']);
    expect(entries.map(e => e.detail)).toEqual(['AUCTION', 'pi_ord-settled-4', 'FK-SETTLED-4', 'fake', null, 'po_ord-settled-4', null]);
  });

  it('shows a cancelled order as closed and leaves unstamped steps out', () => {
    const o = { ...tradeDb.orders.get('ord-fixed-1')!, status: 'CANCELLED' as const, closedAt: '2026-10-11T12:00:00' };
    expect(orderTimeline(o, null, null)).toEqual([{ at: '2026-10-11T12:00:00', key: 'closed', detail: 'CANCELLED' }]);
    expect(orderTimeline(tradeDb.orders.get('ord-fixed-1')!, null, null)).toEqual([]);
  });
});
