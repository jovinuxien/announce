import { AuctionView, OrderView, ShipmentView } from 'app/shared/model/trade';

/** In-memory state of the trade mocks (doc/order-context.md §8); reset on reload. The chat mocks live in chatData.ts. */
export const mockDb = {
  me: 1,
  auctions: new Map<string, AuctionView>(),
  orders: new Map<string, OrderView>(),
  shipments: new Map<string, ShipmentView>(),
  seq: 0,
};

const now = () => new Date().toISOString().slice(0, 19);
const plusMinutes = (m: number) => new Date(Date.now() + m * 60000).toISOString().slice(0, 19);

export function auctionFor(announceId: string): AuctionView {
  let a = mockDb.auctions.get(announceId);
  if (!a) {
    a = {
      announceId,
      status: 'OPEN',
      currency: 'DKK',
      startPriceCents: 10000,
      currentBidCents: null,
      minimumNextBidCents: 10000,
      bidCount: 0,
      endsAt: plusMinutes(90),
      snagPriceCents: 50000,
      highestBidderId: null,
      myHighestBidCents: null,
    };
    mockDb.auctions.set(announceId, a);
  }
  return a;
}

export function placeBid(announceId: string, amountCents: number, bidderId: number): AuctionView {
  const a = auctionFor(announceId);
  if (a.status !== 'OPEN') throw { status: 409, detail: 'auction closed' };
  if (amountCents < a.minimumNextBidCents) throw { status: 409, detail: `bid below the minimum of ${a.minimumNextBidCents / 100}` };
  a.currentBidCents = amountCents;
  a.bidCount += 1;
  a.minimumNextBidCents = amountCents + 500;
  a.highestBidderId = bidderId;
  a.myHighestBidCents = amountCents;
  return a;
}

export function newOrder(announceId: string, buyerId: number, amountCents = 25000): OrderView {
  const id = `ord-${mockDb.orders.size + 1}`;
  const order: OrderView = {
    id,
    announceId,
    announceTitle: `Announce ${announceId}`,
    buyerId,
    sellerId: 2,
    status: 'REQUESTED',
    amountCents,
    feeCents: Math.round(amountCents * 0.05) + 500,
    currency: 'DKK',
    createdAt: now(),
    payBy: null,
    shipBy: null,
    timeline: [{ at: now(), status: 'REQUESTED', note: 'purchase requested' }],
  };
  mockDb.orders.set(id, order);
  return order;
}

export function transition(id: string, status: OrderView['status'], note?: string): OrderView {
  const o = mockDb.orders.get(id);
  if (!o) throw { status: 404, detail: 'order not found' };
  o.status = status;
  if (status === 'AGREED') o.payBy = plusMinutes(48 * 60);
  if (status === 'PAID') o.shipBy = plusMinutes(5 * 24 * 60);
  o.timeline = [...o.timeline, { at: now(), status, note }];
  return o;
}
