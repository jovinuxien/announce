import {
  AuctionAnswer,
  AuctionRow,
  BidRow,
  OrderAnswer,
  OrderRow,
  OrderStatus,
  PaymentRow,
  SellerAccountAnswer,
  SellerAccountRow,
  ShipmentAnswer,
  ShipmentRequest,
  ShipmentRow,
  minimumNextBid,
} from 'app/shared/model/trade';
import { iso, ME, reject } from './chatData';

/**
 * In-memory order module for `features.mocks` and the page tests, in its real shapes and with its rules
 * (doc/frontend-edges.md §16): read-model rows with decimal amounts, commands in cents answering the aggregate,
 * 409 codes (`own-announce`, `not-proposed`, `already-paid`, `seller-not-payable`, `bid-below-minimum`,
 * `seller-cannot-bid`, `auction-not-open`, `not-paid`), 403 (`not-seller`, `not-buyer`, `not-party`), 404 for a
 * payment or shipment that does not exist yet. The signed-in member is #1 (chatData.ME).
 */
export const tradeDb = {
  auctions: new Map<string, AuctionRow>(),
  bids: new Map<string, BidRow[]>(),
  orders: new Map<string, OrderRow>(),
  payments: new Map<string, PaymentRow>(),
  shipments: new Map<string, ShipmentRow>(),
  accounts: new Map<number, SellerAccountRow>(),
  seq: 0,
  /** In the browser the fake carrier dispatches 1.5 s after booking; the tests switch it off. */
  autoCarrier: true,
};

const money = (n: number) => Math.round(n * 100) / 100;

/** The fixtures' fee schedule (5 % plus 10), computed when the deal is agreed: a PROPOSED order carries none, as in the module. */
export const feeOf = (price: number) => money(price * 0.05 + 10);
function order(partial: Partial<OrderRow> & Pick<OrderRow, 'id' | 'announceId' | 'sellerId' | 'buyerId' | 'status' | 'agreedPrice'>): OrderRow {
  const fee = partial.status === 'PROPOSED' ? 0 : feeOf(partial.agreedPrice);
  return {
    door: 'FIXED_PRICE',
    auctionId: null,
    conversationId: null,
    title: null,
    mainPicture: null,
    feeAmount: fee,
    payoutAmount: money(partial.agreedPrice - fee),
    labelPrice: null,
    currency: 'DKK',
    paymentId: null,
    shipmentId: null,
    agreedAt: null,
    paidAt: null,
    shippedAt: null,
    deliveredAt: null,
    receiptConfirmedAt: null,
    releasedAt: null,
    settledAt: null,
    closedAt: null,
    disputeReason: null,
    disputeOpenedAt: null,
    addTime: iso(-60),
    updateTime: iso(-60),
    ...partial,
  };
}
function payment(o: OrderRow, status: PaymentRow['status'], extra: Partial<PaymentRow> = {}): PaymentRow {
  return {
    id: `pay-${o.id}`,
    orderId: o.id,
    announceId: o.announceId,
    buyerId: o.buyerId,
    sellerId: o.sellerId,
    provider: 'fake',
    intentRef: `pi_${o.id}`,
    clientSecret: `pi_${o.id}_secret`,
    status,
    amount: o.agreedPrice,
    feeAmount: o.feeAmount,
    payoutAmount: o.payoutAmount,
    refundAmount: null,
    currency: o.currency,
    payoutRef: null,
    refundRef: null,
    failureReason: null,
    confirmedAt: null,
    releasedAt: null,
    refundedAt: null,
    ...extra,
  };
}
function shipment(o: OrderRow, status: ShipmentRow['status'], extra: Partial<ShipmentRow> = {}): ShipmentRow {
  return {
    id: `shp-${o.id}`,
    orderId: o.id,
    sellerId: o.sellerId,
    buyerId: o.buyerId,
    carrier: 'fake',
    service: null,
    parcelSize: null,
    trackingNumber: null,
    labelUrl: null,
    status,
    lastCarrierEvent: null,
    lastCarrierEventId: null,
    lastEventAt: null,
    lastPolledAt: null,
    requestedAt: null,
    dispatchedAt: null,
    deliveredAt: null,
    ...extra,
  };
}

export function seedTrade() {
  tradeDb.auctions.clear();
  tradeDb.bids.clear();
  tradeDb.orders.clear();
  tradeDb.payments.clear();
  tradeDb.shipments.clear();
  tradeDb.accounts.clear();
  tradeDb.seq = 0;
  // an open auction of member #2 with one bid by #3
  tradeDb.auctions.set('ann-auction-7', {
    id: 'auc-7',
    announceId: 'ann-auction-7',
    sellerId: 2,
    title: 'Vintage Leica M3',
    mainPicture: null,
    currency: 'DKK',
    initialPrice: 100,
    reservePrice: null,
    snagItPrice: null,
    bidIncrement: 10,
    currentPrice: 120,
    bidCount: 1,
    startTime: iso(-24 * 60),
    endTime: iso(2 * 24 * 60),
    autoExtendSec: 120,
    status: 'OPEN',
    winningBidId: 'bid-1',
    winnerId: null,
    closedTime: null,
  });
  tradeDb.bids.set('ann-auction-7', [
    {
      id: 'bid-1',
      auctionId: 'auc-7',
      announceId: 'ann-auction-7',
      bidderId: 3,
      amount: 120,
      maxAmount: null,
      bidTime: iso(-3 * 60),
      status: 'ACTIVE',
      cancelReason: null,
      cancelledBy: null,
    },
  ]);
  // an auction scheduled by the seller that has not started yet (OPEN row, window ahead, no bids)
  tradeDb.auctions.set('ann-auction-9', {
    id: 'auc-9',
    announceId: 'ann-auction-9',
    sellerId: 2,
    title: 'Hasselblad 500C/M',
    mainPicture: null,
    currency: 'DKK',
    initialPrice: 800,
    reservePrice: null,
    snagItPrice: null,
    bidIncrement: 50,
    currentPrice: 800,
    bidCount: 0,
    startTime: iso(24 * 60),
    endTime: iso(4 * 24 * 60),
    autoExtendSec: 120,
    status: 'OPEN',
    winningBidId: null,
    winnerId: null,
    closedTime: null,
  });
  // a closed auction won by me
  tradeDb.auctions.set('ann-auction-8', {
    id: 'auc-8',
    announceId: 'ann-auction-8',
    sellerId: 2,
    title: 'Brompton M6L',
    mainPicture: null,
    currency: 'DKK',
    initialPrice: 3000,
    reservePrice: null,
    snagItPrice: null,
    bidIncrement: 100,
    currentPrice: 3200,
    bidCount: 2,
    startTime: iso(-5 * 24 * 60),
    endTime: iso(-24 * 60),
    autoExtendSec: 120,
    status: 'CLOSED_WON',
    winningBidId: 'bid-3',
    winnerId: ME,
    closedTime: iso(-24 * 60),
  });
  tradeDb.bids.set('ann-auction-8', [
    {
      id: 'bid-2',
      auctionId: 'auc-8',
      announceId: 'ann-auction-8',
      bidderId: 3,
      amount: 3100,
      maxAmount: null,
      bidTime: iso(-2 * 24 * 60),
      status: 'OUTBID',
      cancelReason: null,
      cancelledBy: null,
    },
    {
      id: 'bid-3',
      auctionId: 'auc-8',
      announceId: 'ann-auction-8',
      bidderId: ME,
      amount: 3200,
      maxAmount: null,
      bidTime: iso(-30 * 60),
      status: 'WON',
      cancelReason: null,
      cancelledBy: null,
    },
  ]);
  const put = (o: OrderRow, p?: PaymentRow, s?: ShipmentRow) => {
    tradeDb.orders.set(o.id, o);
    if (p) tradeDb.payments.set(o.id, p);
    if (s) tradeDb.shipments.set(o.id, s);
  };
  // me buying: proposed, waiting for the seller
  put(
    order({
      id: 'ord-fixed-1',
      announceId: 'ann-fixed-9',
      sellerId: 2,
      buyerId: ME,
      status: 'PROPOSED',
      agreedPrice: 1250,
      title: 'Trek FX 3 hybrid bike, size M',
      addTime: iso(-50),
    })
  );
  // me selling: proposed, my call
  put(
    order({
      id: 'ord-sell-2',
      announceId: 'ann-goods-4',
      sellerId: ME,
      buyerId: 3,
      status: 'PROPOSED',
      agreedPrice: 450,
      title: 'Harry Potter box set, hardcover',
      addTime: iso(-40),
    })
  );
  // me buying: paid, the seller has not booked the carrier yet
  const paid = order({
    id: 'ord-paid-3',
    announceId: 'ann-fixed-5',
    sellerId: 2,
    buyerId: ME,
    status: 'PAID',
    agreedPrice: 800,
    title: 'Toyota Yaris roof box',
    door: 'OFFER',
    agreedAt: iso(-3 * 24 * 60),
    paidAt: iso(-2 * 24 * 60),
    paymentId: 'pay-ord-paid-3',
    shipmentId: 'shp-ord-paid-3',
    addTime: iso(-3 * 24 * 60),
  });
  put(paid, payment(paid, 'CONFIRMED', { confirmedAt: paid.paidAt }), shipment(paid, 'OPEN'));
  // me selling: paid, waiting for me to book
  const toShip = order({
    id: 'ord-ship-5',
    announceId: 'ann-goods-6',
    sellerId: ME,
    buyerId: 4,
    status: 'PAID',
    agreedPrice: 300,
    title: 'Lego Technic crane',
    agreedAt: iso(-2 * 24 * 60),
    paidAt: iso(-24 * 60),
    paymentId: 'pay-ord-ship-5',
    shipmentId: 'shp-ord-ship-5',
    addTime: iso(-2 * 24 * 60),
  });
  put(toShip, payment(toShip, 'CONFIRMED', { confirmedAt: toShip.paidAt }), shipment(toShip, 'OPEN'));
  // me buying: settled
  const settled = order({
    id: 'ord-settled-4',
    announceId: 'ann-fixed-2',
    sellerId: 2,
    buyerId: ME,
    status: 'SETTLED',
    agreedPrice: 2100,
    title: 'Yamaha P-45 digital piano',
    door: 'AUCTION',
    auctionId: 'auc-old',
    agreedAt: iso(-12 * 24 * 60),
    paidAt: iso(-11 * 24 * 60),
    shippedAt: iso(-10 * 24 * 60),
    deliveredAt: iso(-8 * 24 * 60),
    receiptConfirmedAt: iso(-8 * 24 * 60 + 30),
    releasedAt: iso(-8 * 24 * 60 + 31),
    settledAt: iso(-8 * 24 * 60 + 32),
    paymentId: 'pay-ord-settled-4',
    shipmentId: 'shp-ord-settled-4',
    addTime: iso(-12 * 24 * 60),
  });
  put(
    settled,
    payment(settled, 'PAID_OUT', { confirmedAt: settled.paidAt, releasedAt: settled.releasedAt, payoutRef: 'po_ord-settled-4' }),
    shipment(settled, 'DELIVERED', {
      service: 'MyPack Collect',
      parcelSize: 'L',
      trackingNumber: 'FK-SETTLED-4',
      labelUrl: 'https://fake-carrier.invalid/labels/FK-SETTLED-4.pdf',
      lastCarrierEvent: 'Delivered to parcel shop',
      lastEventAt: settled.deliveredAt,
      requestedAt: iso(-10 * 24 * 60 - 60),
      dispatchedAt: settled.shippedAt,
      deliveredAt: settled.deliveredAt,
    })
  );
  // seller #2 has a payout account; me not yet
  tradeDb.accounts.set(2, {
    id: 'acc-2',
    userId: 2,
    provider: 'fake',
    accountRef: 'acct_fake_2',
    status: 'VERIFIED',
    payoutsEnabled: true,
    onboardingUrl: 'https://fake-provider.invalid/onboarding/acct_fake_2',
  });
}
seedTrade();

const party = (o: OrderRow, me: number) => o.buyerId === me || o.sellerId === me;
export function orderOf(id: string, me: number): OrderRow {
  const o = tradeDb.orders.get(id);
  if (!o || !party(o, me)) throw reject(404, 'not-found', `No order ${id}`);
  return o;
}
const answerOf = (o: OrderRow): OrderAnswer => ({ id: o.id, status: o.status });
const auctionAnswer = (a: AuctionRow): AuctionAnswer => ({ announceId: a.announceId, status: a.status, currentPriceCents: Math.round(a.currentPrice * 100), bidCount: a.bidCount });

export const ordersOf = (me: number, role: string, page: number, size: number): OrderRow[] =>
  [...tradeDb.orders.values()]
    .filter(o => (role === 'seller' ? o.sellerId === me : o.buyerId === me))
    .sort((a, b) => ((b.addTime ?? '') > (a.addTime ?? '') ? 1 : -1))
    .slice(page * size, (page + 1) * size);

/** RequestPurchase: `mine-*` announces belong to the caller, `missing-*` are unknown to the snapshot. */
export function requestPurchase(announceId: string, me: number): OrderAnswer {
  if (announceId.startsWith('missing-')) throw reject(404, 'announce-unknown', `Announce ${announceId} is not known to the order module`);
  if (announceId.startsWith('mine-')) throw reject(409, 'own-announce', 'A member cannot buy their own announce');
  const id = `ord-${++tradeDb.seq + 100}`;
  const o = order({ id, announceId, sellerId: 2, buyerId: me, status: 'PROPOSED', agreedPrice: 1250, title: `Announce ${announceId}`, addTime: iso(0), updateTime: iso(0) });
  tradeDb.orders.set(id, o);
  return answerOf(o);
}
function requireSeller(o: OrderRow, me: number) {
  if (o.sellerId !== me) throw reject(403, 'not-seller', 'Only the seller may do this');
}
function requireBuyer(o: OrderRow, me: number) {
  if (o.buyerId !== me) throw reject(403, 'not-buyer', 'Only the buyer may do this');
}
export function accept(id: string, me: number): OrderAnswer {
  const o = orderOf(id, me);
  requireSeller(o, me);
  if (o.status !== 'PROPOSED') throw reject(409, 'not-proposed', `Only a proposed purchase can be accepted or declined (status ${o.status})`);
  const acc = tradeDb.accounts.get(me);
  if (!acc?.payoutsEnabled) throw reject(409, 'seller-not-payable', 'The seller has no payout account yet');
  o.status = 'PAYMENT_PENDING';
  o.agreedAt = iso(0);
  o.feeAmount = feeOf(o.agreedPrice);
  o.payoutAmount = money(o.agreedPrice - o.feeAmount);
  o.paymentId = `pay-${o.id}`;
  tradeDb.payments.set(o.id, payment(o, 'INTENT_CREATED'));
  return answerOf(o);
}
export function decline(id: string, me: number): OrderAnswer {
  const o = orderOf(id, me);
  requireSeller(o, me);
  if (o.status !== 'PROPOSED') throw reject(409, 'not-proposed', `Only a proposed purchase can be accepted or declined (status ${o.status})`);
  o.status = 'DECLINED';
  o.closedAt = iso(0);
  return answerOf(o);
}
export function cancel(id: string, me: number): OrderAnswer {
  const o = tradeDb.orders.get(id);
  if (!o) throw reject(404, 'not-found', `No order ${id}`);
  if (!party(o, me)) throw reject(403, 'not-party', 'Only the buyer or the seller can cancel');
  if (!['PROPOSED', 'AGREED', 'PAYMENT_PENDING'].includes(o.status)) throw reject(409, 'already-paid', `A paid order cannot be cancelled; open a dispute (status ${o.status})`);
  o.status = 'CANCELLED';
  o.closedAt = iso(0);
  return answerOf(o);
}
export function confirmReceipt(id: string, me: number): OrderAnswer {
  const o = orderOf(id, me);
  requireBuyer(o, me);
  if (o.status !== 'SHIPPED' && o.status !== 'DELIVERED') throw reject(409, 'not-shipped', `Receipt can be confirmed once shipped (status ${o.status})`);
  o.receiptConfirmedAt = iso(0);
  o.releasedAt = iso(0);
  o.settledAt = iso(0);
  o.status = 'SETTLED';
  const p = tradeDb.payments.get(o.id);
  if (p) Object.assign(p, { status: 'PAID_OUT', releasedAt: o.releasedAt, payoutRef: `po_${o.id}` });
  return answerOf(o);
}
export function dispute(id: string, me: number, reason: string | undefined): OrderAnswer {
  const o = orderOf(id, me);
  requireBuyer(o, me);
  if (!['PAID', 'SHIPPED', 'DELIVERED'].includes(o.status)) throw reject(409, 'not-disputable', `A dispute needs a paid, open order (status ${o.status})`);
  if (!reason || reason.trim().length < 10) throw reject(400, 'reason-required', 'The reason must say what is wrong');
  o.status = 'DISPUTED';
  o.disputeReason = reason;
  o.disputeOpenedAt = iso(0);
  return answerOf(o);
}
export function paymentOf(id: string, me: number): PaymentRow {
  orderOf(id, me);
  const p = tradeDb.payments.get(id);
  if (!p) throw reject(404, 'not-found', `No payment for order ${id}`);
  return p;
}
export function shipmentOf(id: string, me: number): ShipmentRow {
  orderOf(id, me);
  const s = tradeDb.shipments.get(id);
  if (!s) throw reject(404, 'not-found', `No shipment for order ${id}`);
  return s;
}
export function requestShipment(id: string, me: number, body: ShipmentRequest): ShipmentAnswer {
  const o = orderOf(id, me);
  requireSeller(o, me);
  if (o.status !== 'PAID') throw reject(409, 'not-paid', `Only a paid order can be shipped (status ${o.status})`);
  const s = tradeDb.shipments.get(id) ?? shipment(o, 'OPEN');
  Object.assign(s, {
    status: 'REQUESTED',
    service: body.service,
    parcelSize: body.parcelSize,
    trackingNumber: `FK${Date.now()}`,
    labelUrl: `https://fake-carrier.invalid/labels/${id}.pdf`,
    requestedAt: iso(0),
  });
  tradeDb.shipments.set(id, s);
  return { orderId: id, status: 'REQUESTED', trackingNumber: s.trackingNumber };
}
/** What the carrier's dispatched event does (the browser mock fires it 1.5 s after booking). */
export function carrierDispatched(id: string) {
  const o = tradeDb.orders.get(id);
  const s = tradeDb.shipments.get(id);
  if (!o || !s || s.status !== 'REQUESTED') return;
  Object.assign(s, { status: 'DISPATCHED', lastCarrierEvent: 'Parcel handed in', lastEventAt: iso(0), dispatchedAt: iso(0) });
  o.status = 'SHIPPED' as OrderStatus;
  o.shippedAt = iso(0);
}
export function sellerAccount(me: number): SellerAccountRow {
  const a = tradeDb.accounts.get(me);
  if (!a) throw reject(404, 'not-found', `Seller ${me} has no payout account yet`);
  return a;
}
export function onboard(me: number): SellerAccountAnswer {
  const a: SellerAccountRow = tradeDb.accounts.get(me) ?? {
    id: `acc-${me}`,
    userId: me,
    provider: 'fake',
    accountRef: `acct_fake_${me}`,
    status: 'VERIFIED',
    payoutsEnabled: true,
    onboardingUrl: `https://fake-provider.invalid/onboarding/acct_fake_${me}`,
  };
  tradeDb.accounts.set(me, a);
  return { userId: me, provider: a.provider, accountRef: a.accountRef, onboardingUrl: a.onboardingUrl, status: a.status, payoutsEnabled: a.payoutsEnabled };
}

// auction
export function auctionOf(announceId: string) {
  const a = tradeDb.auctions.get(announceId);
  if (!a) throw reject(404, 'not-found', `No auction for announce ${announceId}`);
  return { auction: a, bids: tradeDb.bids.get(announceId) ?? [] };
}
function requireOpen(a: AuctionRow) {
  if (a.status !== 'OPEN') throw reject(409, 'auction-not-open', `The auction is ${a.status}`);
  if (new Date(a.endTime).getTime() <= Date.now()) throw reject(409, 'auction-ended', `The auction ended at ${a.endTime}`);
}
export function placeBid(announceId: string, me: number, amountCents: number): AuctionAnswer {
  const { auction: a, bids } = auctionOf(announceId);
  requireOpen(a);
  if (a.sellerId === me) throw reject(409, 'seller-cannot-bid', 'The seller cannot bid on their own auction');
  const minimum = minimumNextBid(a);
  if (amountCents < Math.round(minimum * 100))
    throw reject(
      409,
      'bid-below-minimum',
      `The bid must be at least ${Math.round(minimum * 100)} minor units (current price ${Math.round(a.currentPrice * 100)}${a.bidCount ? ` + increment ${Math.round(a.bidIncrement * 100)}` : ''})`
    );
  bids.forEach(b => {
    if (b.status === 'ACTIVE') b.status = 'OUTBID';
  });
  const bid: BidRow = {
    id: `bid-${++tradeDb.seq + 10}`,
    auctionId: a.id,
    announceId,
    bidderId: me,
    amount: amountCents / 100,
    maxAmount: null,
    bidTime: iso(0),
    status: 'ACTIVE',
    cancelReason: null,
    cancelledBy: null,
  };
  bids.push(bid);
  tradeDb.bids.set(announceId, bids);
  a.currentPrice = bid.amount;
  a.bidCount += 1;
  a.winningBidId = bid.id;
  return auctionAnswer(a);
}
export function snag(announceId: string, me: number): AuctionAnswer {
  const { auction: a } = auctionOf(announceId);
  requireOpen(a);
  if (a.sellerId === me) throw reject(409, 'seller-cannot-bid', 'The seller cannot buy their own auction');
  if (a.snagItPrice == null) throw reject(409, 'no-snag-price', 'This auction has no buy-now price');
  a.status = 'CLOSED_SNAGGED';
  a.currentPrice = a.snagItPrice;
  a.winnerId = me;
  a.closedTime = iso(0);
  return auctionAnswer(a);
}
export function retractBid(announceId: string, bidId: string, me: number): AuctionAnswer {
  const { auction: a, bids } = auctionOf(announceId);
  requireOpen(a);
  const b = bids.find(x => x.id === bidId);
  if (!b) throw reject(404, 'not-found', `No bid ${bidId}`);
  if (b.bidderId !== me) throw reject(403, 'not-bidder', 'Only the bidder can retract a bid');
  if (b.status !== 'ACTIVE' && b.status !== 'OUTBID') throw reject(409, 'bid-not-standing', `Bid ${bidId} is ${b.status}`);
  b.status = 'RETRACTED';
  const standing = bids.filter(x => x.status === 'ACTIVE' || x.status === 'OUTBID').sort((x, y) => y.amount - x.amount);
  if (standing[0]) standing[0].status = 'ACTIVE';
  a.currentPrice = standing[0]?.amount ?? a.initialPrice;
  a.bidCount = standing.length;
  a.winningBidId = standing[0]?.id ?? null;
  return auctionAnswer(a);
}
