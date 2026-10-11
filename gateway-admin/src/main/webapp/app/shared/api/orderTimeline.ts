import type { OrderRow, PaymentRow, ShipmentRow } from './ordersApi';

/**
 * The order detail screen's timeline, derived from the timestamps the three read-model rows carry. The order
 * module keeps no event timeline table, and the screens never read the event store, so this is the whole story
 * the admin sees: every stamped step of the order row, plus the payment's and shipment's own stamps when they
 * add something (a refund, the carrier's last event). Sorted by time; a step without a timestamp is left out.
 */
export interface TimelineEntry {
  at: string;
  /** Translation key under `trade.timeline`. */
  key: TimelineKey;
  detail: string | null;
}
export type TimelineKey =
  | 'agreed' | 'paid' | 'shipped' | 'delivered' | 'receiptConfirmed' | 'disputeOpened' | 'released' | 'settled'
  | 'closed' | 'paymentRefunded' | 'carrierEvent';

export function orderTimeline(order: OrderRow, payment?: PaymentRow | null, shipment?: ShipmentRow | null): TimelineEntry[] {
  const out: TimelineEntry[] = [];
  const push = (at: string | null | undefined, key: TimelineKey, detail: string | null = null) => {
    if (at) out.push({ at, key, detail });
  };
  push(order.agreedAt, 'agreed', order.door);
  push(order.paidAt, 'paid', payment?.intentRef ?? null);
  push(order.shippedAt, 'shipped', shipment?.trackingNumber ?? null);
  push(order.deliveredAt, 'delivered', shipment?.carrier ?? null);
  push(order.receiptConfirmedAt, 'receiptConfirmed');
  push(order.disputeOpenedAt, 'disputeOpened', order.disputeReason);
  push(order.releasedAt, 'released', payment?.payoutRef ?? null);
  push(order.settledAt, 'settled');
  if (payment?.refundedAt) push(payment.refundedAt, 'paymentRefunded', payment.refundRef);
  if (order.closedAt && !order.settledAt) push(order.closedAt, 'closed', order.status);
  if (shipment?.lastEventAt && shipment.lastCarrierEvent && shipment.lastEventAt !== shipment.deliveredAt && shipment.lastEventAt !== shipment.dispatchedAt) {
    push(shipment.lastEventAt, 'carrierEvent', shipment.lastCarrierEvent);
  }
  return out.sort((a, b) => a.at.localeCompare(b.at));
}
