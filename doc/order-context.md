# The order context: auction, deal, payment and shipping

Status: implemented 2026-09-26 in the `order` module (worktree `order-reactive`); deviations
from the agreed design are recorded in §11. The `messaging` module (chat) is its own worktree.
See the matching blocks in `CLAUDE.md`.
Builds on `doc/announce-management-context.md` (context map, rules) and
`doc/auction-chat-schema.md` (ActionBazaar mapping, `announce_auction*` tables).

## 1. What the context is for

A buyer wants an announced item. They reach agreement with the seller through one of
three doors, and from then on every deal follows one secure path: the buyer pays the
platform, the seller ships through a shipping provider, the platform confirms delivery
with the carrier, and only then pays the seller the agreed price minus the platform fee.
This is the Tradera "köp" / Blocket "säker betalning" flow.

The three doors:

| Door | Trigger | Price |
|---|---|---|
| Auction | the auction closes with a winner (`AuctionClosedWon`) | winning bid |
| Fixed price | the buyer clicks Buy on a non-auction announce; the seller accepts (or has instant accept on) | announce price |
| Chat offer | an OFFER message is accepted in a listing conversation (`OfferAcceptedV1` from messaging) | negotiated amount |

## 2. Decisions

1. **One reactive Maven module `order`**, built like `announce-management`: cqrs-reactive
   aggregates on EventSourcingDB, db-reactive read models in MySQL, projections, public
   events over Kafka, layout `domain / application / infrastructure / interfaces` under
   each package root. The legacy litemall order sources (cart, coupon, groupon, integral,
   Boot 2) are deleted, not ported. None of those concepts exist in a classifieds deal.
2. **Four package roots, each a bounded context**: `auction`, `order`, `payment`,
   `shipping`, under `org.linlinjava.announce.<root>`. They share nothing but events.
   No root imports another root's classes. `auction` is the first candidate to become
   its own service later (hottest write path).
3. **Messaging is a peer context, not a child of order.** Chat exists before any deal
   (questions, support, bulletins). It is its own module `messaging` and its own
   worktree; the only coupling is the public event `OfferAcceptedV1` that `order` consumes.
4. **The platform never holds money itself.** Buyer funds and seller payouts go through a
   marketplace payment provider (Stripe Connect first; Adyen for Platforms is the
   alternative). The platform is the merchant of record, the provider holds the balance,
   runs seller KYC and does the payout. Holding funds ourselves would need a payment
   institution licence under PSD2.
5. **Facts come from providers, not from clicks.** PAID is set only from the payment
   provider's webhook. SHIPPED and DELIVERED are set only from the carrier's tracking
   events. The seller's "I shipped" is a request, not a fact.
6. **Payout is released on carrier delivery**, then a short dispute window. Release on
   dispatch is a configuration option (`order.release.on=DELIVERY|DISPATCH`), default
   DELIVERY. There is no in-person pickup path for paid deals: every paid deal ships
   through a carrier.
7. **Timers are jobs over read models that send commands**, the `AnnounceExpiryJob`
   pattern. No scheduler state in the event store.

## 3. The release rule

The payout to the seller is triggered when all three are true on the order:

- `PaymentConfirmed` was received from the payment provider webhook.
- `ShipmentDelivered` was received from the carrier (or `ShipmentDispatched` plus
  `order.release.dispatch-grace` when the carrier never reports delivery, or when
  `order.release.on=DISPATCH`).
- No dispute is open.

Then the dispute window (`order.release.delivery-grace`, default 3 days) elapses, or the
buyer confirms receipt earlier, and `order` emits `PayoutReleaseRequested`; `payment`
transfers the payout and the order settles.

Windows (all configurable, `order.window.*`):

| Window | Default | Allowed | On expiry |
|---|---|---|---|
| pay after AGREED | 48 h | 12 h .. 7 d | order CANCELLED, announce stays PUBLISHED |
| ship after PAID | 7 d | 3 d .. 7 d | order REFUNDED (payment.Refund), announce back to PUBLISHED |
| release after DELIVERED | 3 d | 0 .. 14 d | PayoutReleaseRequested |
| release after DISPATCHED with no delivery event | 14 d | 7 .. 30 d | PayoutReleaseRequested |

## 4. Aggregates, commands and events

Subjects follow the framework convention (`doc/cqrs-reactive.md`). Event classes are
top-level (EventSourcingDB rejects `$` in type names). Ids are UUID strings.

### `auction` — `/auction/{announceId}`

Opened from `AnnouncePublishedV1` when `saleType == AUCTION`. Rules from
`doc/auction-chat-schema.md` §3 live here: bidder is not the seller, `amount >=
currentPrice + increment`, window open, snag-it only while no bid, anti-sniping extension.

| Command | Event(s) |
|---|---|
| OpenAuction(initialPrice, reservePrice?, snagItPrice?, increment, start, end, autoExtend) | AuctionOpened |
| PlaceBid(bidderId, amount, maxAmount?) | BidPlaced, BidderOutbid (previous), AuctionExtended (if inside auto-extend window) |
| SnagIt(buyerId) | AuctionSnagged |
| CloseAuction (job, `end_time <= now`) | AuctionClosedWon(winner, price) or AuctionClosedUnsold |
| CancelAuction(reason) (seller before first bid, or staff) | AuctionCancelled |
| RetractBid / CancelBid (staff) | BidRetracted / BidCancelled |

Public: `AuctionOpenedV1`, `BidPlacedV1`, `AuctionClosedWonV1`, `AuctionClosedUnsoldV1`,
`AuctionSnaggedV1` (topic `auction.v1`). `order` consumes the last three.

### `order` — `/order/{orderId}`

One order = one announce, one buyer, one seller. It never talks to a provider; it reacts
to `payment` and `shipping` events and owns the release decision.

| Command | From | Event |
|---|---|---|
| RequestPurchase(announceId, buyerId) | fixed-price door | OrderProposed |
| AcceptPurchase / DeclinePurchase (seller) | fixed-price door | OrderAgreed / OrderDeclined |
| CreateFromAuction(announceId, buyerId, price) | AuctionClosedWonV1 / AuctionSnaggedV1 | OrderAgreed |
| CreateFromOffer(announceId, buyerId, sellerId, amount, conversationId) | OfferAcceptedV1 | OrderAgreed |
| RecordPaymentConfirmed(paymentId) | PaymentConfirmed | OrderPaid |
| RecordPaymentFailed | PaymentFailed | OrderPaymentFailed (stays PAYMENT_PENDING) |
| RecordShipmentDispatched(shipmentId) | ShipmentDispatched | OrderShipped |
| RecordShipmentDelivered | ShipmentDelivered | OrderDelivered |
| ConfirmReceipt (buyer) | REST | ReceiptConfirmed |
| OpenDispute(reason) (buyer, after PAID) | REST | DisputeOpened |
| ResolveDispute(RELEASE or REFUND) (staff) | REST | DisputeResolved |
| ReleasePayout (job or ConfirmReceipt) | release rule | PayoutReleaseRequested |
| RecordPayoutSent | PayoutSent | OrderSettled |
| Cancel(reason) (buyer/seller before PAID, job on pay window) | REST / job | OrderCancelled |
| RecordRefunded | RefundSent | OrderRefunded |

States: PROPOSED -> AGREED -> PAYMENT_PENDING -> PAID -> SHIPPED -> DELIVERED ->
RELEASING -> SETTLED. Exits: CANCELLED (before PAID), DISPUTED (after PAID; blocks
release; resolves to RELEASING or REFUNDING), REFUNDED.

`OrderAgreed` carries the price, the fee and the payout: the fee is computed once, at
agreement, by the `FeeSchedule` policy (percentage + fixed, per category; seed values in
config). The shipping label price, when bought through us, is a separate line paid by the
buyer and passed through to the carrier, never part of the seller payout.

Public: `OrderAgreedV1`, `OrderPaidV1`, `OrderSettledV1`, `OrderCancelledV1`,
`OrderRefundedV1` (topic `order.v1`). announce-management consumes `OrderPaidV1`
(MarkSold) and `OrderCancelledV1` / `OrderRefundedV1` (announce back to PUBLISHED when
it was marked sold).

### `payment` — `/payment/{orderId}`

The payment provider integration. `PaymentProvider` port: `createIntent(order, amount,
fee, buyer)`, `release(payment, payoutAmount, sellerAccount)`, `refund(payment,
amount)`. Adapters: `FakePaymentProvider` (tests, local dev; a REST hook simulates
webhooks) and `StripeConnectPaymentProvider` (separate charges and transfers,
`application_fee_amount`, Express accounts for sellers). Webhooks are verified by
signature and de-duplicated on the provider event id.

| Command | Event |
|---|---|
| CreateIntent (from OrderAgreedV1) | PaymentIntentCreated(clientSecret) |
| RecordProviderEvent(providerEventId, kind) | PaymentConfirmed / PaymentFailed / PayoutSent / RefundSent |
| Release(payoutAmount) (from PayoutReleaseRequested) | PayoutRequested |
| Refund(amount) (from OrderRefund paths) | RefundRequested |

Seller onboarding (connected account, KYC) is a `payment` use case with its own read
table `announce_seller_account`; a seller without a verified account can list but cannot
be paid, and `order` refuses AcceptPurchase / OpenAuction until it exists.

### `shipping` — `/shipment/{orderId}`

The carrier integration. `Carrier` port: `book(shipment) -> label or drop-off code`,
`track(trackingNumber) -> events`. Adapters: `FakeCarrier` (tests, local dev, REST hook
simulates tracking events) and one real carrier first (PostNord for DK/SE; Bring and DHL
later). Carriers that push webhooks are consumed like payment webhooks; the others are
polled by `ShipmentTrackingJob` over open shipments.

| Command | Event |
|---|---|
| RequestShipment(carrier, service, parcel size, from/to address) (seller, after PAID) | ShipmentRequested(label, trackingNumber) |
| RecordCarrierEvent(carrierEventId, kind, at) | ShipmentDispatched / ShipmentInTransit / ShipmentDelivered / ShipmentFailed |
| CancelShipment | ShipmentCancelled |

## 5. Read models (`db-reactive/sql/announce_order.sql`)

- `announce_order`: reshaped. `id` varchar(36), `announce_id`, `seller_id`, `buyer_id`,
  `door` (AUCTION, FIXED_PRICE, OFFER), `auction_id`, `conversation_id`, `agreed_price`,
  `fee_amount`, `payout_amount`, `label_price`, `currency`, `status`, `payment_id`,
  `shipment_id`, `agreed_at`, `paid_at`, `shipped_at`, `delivered_at`, `released_at`,
  `settled_at`, `dispute_reason`, `last_event_id`, `add_time`, `update_time`, `deleted`.
  The litemall columns (coupon, groupon, integral, freight, aftersale) are dropped;
  `announce_order_goods` and `announce_cart` are dropped.
- `announce_order_snapshot`: what the order needs to know about an announce (title,
  price, sale type, seller, main picture, status), fed from `announce.v1`. The ACL:
  `order` never calls the announce API for these.
- `announce_auction`, `announce_bid`, `announce_auction_event`: kept from
  `announce_auction_chat.sql` with `goods_id` -> `announce_id` varchar(36) and
  `winning_bid_id`/`order_id` as varchar ids.
- `announce_payment`: `id`, `order_id`, `provider`, `intent_ref`, `status`, `amount`,
  `fee_amount`, `payout_ref`, `refund_ref`, `confirmed_at`, `released_at`, `refunded_at`.
- `announce_seller_account`: `user_id`, `provider`, `account_ref`, `status`
  (PENDING, VERIFIED, RESTRICTED), `payouts_enabled`.
- `announce_shipment`: `id`, `order_id`, `carrier`, `service`, `tracking_number`,
  `label_url`, `status`, `last_carrier_event`, `last_event_at`, `last_polled_at`,
  `delivered_at`.
- `announce_billing_info` stays (tokenised buyer payment methods).
- Progress checkpoints via `R2dbcProgressTracker`, one per projection.

## 6. Flows

**Auction to settlement**

1. announce-management publishes `AnnouncePublishedV1` (saleType AUCTION). `auction`
   sends OpenAuction; the snapshot projection stores the announce.
2. Bids arrive through `POST /api/auctions/{announceId}/bids`; each is a command on the
   aggregate, so a snag-it and a bid on the same auction are serialised by the subject.
3. `AuctionClosingJob` sends CloseAuction when `end_time` passes. `AuctionClosedWonV1`
   is published; `order` handles it with CreateFromAuction -> AGREED -> PAYMENT_PENDING.
4. `payment` handles `OrderAgreedV1` with CreateIntent. The buyer completes payment on
   the client with the intent's client secret.
5. The provider webhook lands on `POST /api/payments/webhook/{provider}`. `payment`
   emits PaymentConfirmed; `order` becomes PAID; the seller is notified "paid, ship
   within 7 days". No payout.
6. The seller books through `POST /api/orders/{id}/shipment`; `shipping` emits
   ShipmentRequested and hands back the label.
7. Carrier events (webhook or `ShipmentTrackingJob`) drive ShipmentDispatched ->
   `order` SHIPPED, ShipmentDelivered -> `order` DELIVERED.
8. After the delivery grace (or ConfirmReceipt), `OrderReleaseJob` sends ReleasePayout;
   the release rule passes; `payment` transfers the payout minus the fee; PayoutSent ->
   `order` SETTLED; `OrderSettledV1` published.

**Fixed price**: steps 3 become RequestPurchase (buyer) then AcceptPurchase (seller, or
instant accept). **Chat offer**: `messaging` publishes `OfferAcceptedV1`; step 3 becomes
CreateFromOffer.

**Failure paths**: pay window expires -> Cancel -> OrderCancelledV1. Ship window expires
-> Refund -> RefundSent -> OrderRefunded -> OrderRefundedV1 (announce back to
PUBLISHED). Dispute after PAID -> DISPUTED, release blocked; staff resolve to release or
refund. ShipmentFailed (lost, returned) -> staff dispute path.

## 7. Integration with the other contexts

| Direction | Topic | Events | Consumer action |
|---|---|---|---|
| announce-management -> order | `announce.v1` | AnnouncePublishedV1, AnnounceEditedV1, AnnounceWithdrawnV1, AnnounceExpiredV1 | snapshot upsert; OpenAuction / CancelAuction |
| messaging -> order | `messaging.v1` | OfferAcceptedV1 | CreateFromOffer |
| order -> announce-management | `order.v1` | OrderPaidV1, OrderCancelledV1, OrderRefundedV1 | MarkSold / republish |
| order -> messaging | `order.v1` | OrderAgreedV1, OrderPaidV1, OrderShippedV1, OrderSettledV1 | SYSTEM messages in the listing conversation |
| auction -> order (same module, still via topic) | `auction.v1` | AuctionClosedWonV1, AuctionSnaggedV1, AuctionClosedUnsoldV1 | CreateFromAuction |

Prerequisite: announce-management does not publish public events yet (its
`infrastructure.messaging` packages are empty and there is no `announce-management-api`
module). Step 0 of `order-reactive` adds them. Public contracts (`*V1` records,
`@HttpExchange` interfaces) live in `<service>-api` modules, per `doc/cqrs-reactive.md`.

## 8. REST (`interfaces.rest`, owner header `X-User-Id`)

- `POST /api/auctions/{announceId}/bids`, `POST .../snag`, `GET /api/auctions/{announceId}`
- `POST /api/orders` (RequestPurchase), `POST /api/orders/{id}/accept|decline|cancel`,
  `POST /api/orders/{id}/confirm-receipt`, `POST /api/orders/{id}/dispute`,
  `GET /api/orders?role=buyer|seller`
- `POST /api/orders/{id}/shipment` (RequestShipment), `GET /api/orders/{id}/shipment`
- `POST /api/payments/webhook/{provider}`, `POST /api/shipping/webhook/{carrier}`
- `POST /api/sellers/me/account` (onboarding link), `GET /api/sellers/me/account`
- Admin: `POST /api/admin/orders/{id}/resolve-dispute`, `POST /api/admin/bids/{id}/cancel`
- Errors map to WebFlux problem details through the framework's error mapping.

## 9. Messaging module (worktree `messaging`)

Status: implemented 2026-09-26 in modules `messaging` and `messaging-api` (details in
`doc/messaging-context.md`). What was built, and where it deviates from the sketch above:

- Aggregate `Conversation` on `/conversation/{id}`, package
  `org.linlinjava.announce.messaging`. Commands OpenListingConversation,
  OpenSupportConversation, JoinConversation (staff), PostMessage (TEXT, IMAGE, OFFER, and
  SYSTEM for the platform; idempotent on `clientMsgId`), AcceptOffer / DeclineOffer
  (seller, latest open offer), CloseConversation, BlockConversation (staff). Events
  ConversationOpened, ParticipantJoined, MessagePosted, OfferAccepted, OfferDeclined,
  ConversationClosed, ConversationBlocked.
- **Deviation: MarkRead is not a command.** A read marker is not a domain fact; it is a
  read-model write of `last_read_seq` on the participant row (`POST
  /api/conversations/{id}/read`, WebSocket frame `read`). Messages carry a per-conversation
  `seq`; unread = `last_message_seq - last_read_seq`.
- **Deviation: deterministic listing id.** `id = nameUUID("listing:" + announceId + ":" +
  buyerId)`; opening the same pair twice returns the existing conversation. The `order.v1`
  subscriber therefore opens-or-finds the thread for any deal (auction, fixed price) and
  posts the SYSTEM message there, without a lookup.
- The seller of an announce is resolved through an `AnnounceLookup` port over the
  announce-management API (`GET /api/announces/{id}`); switch to a snapshot from
  `announce.v1` once `announce-management-api` is merged.
- Read models: `db-reactive/sql/announce_messaging.sql` (the four chat tables moved out of
  `announce_auction_chat.sql`; conversation and message ids varchar(36), `announce_id`
  varchar(36), `seq`, offer columns, `last_event_id`). Member and staff ids stay `Integer`
  (`announce_user.id`, `announce_admin.id`), so `OfferAcceptedV1.buyerId` / `sellerId` are
  integers.
- Public events (`messaging-api`, topic `messaging.v1`): `OfferAcceptedV1(conversationId,
  announceId, buyerId, sellerId, amountCents, currency, acceptedAt)` with type
  `messaging.offer-accepted`, and `ConversationOpenedV1` (`messaging.conversation-opened`).
- Consumes `order.v1` (`OrderAgreedV1`, `OrderPaidV1`, `OrderShippedV1`, `OrderSettledV1`,
  types `order.agreed|paid|shipped|settled`) as local stubs under
  `infrastructure.messaging.order`; replace them by the `order-api` dependency when that
  module lands. Field names match `order-api`'s `OrderPaidV1`. Each SYSTEM message uses
  the Kafka event id as `clientMsgId`, so redelivery posts once.
- Delivery: `/ws/conversations/{id}` (frames `post`, `offer`, `accept-offer`,
  `decline-offer`, `read`, `close`, `ping`; answers `ack` / `error`; pushes `message` /
  `status`) and `/ws/staff?clientType=` (pushes `bulletin`). Fan-out across nodes is a
  second relay group `messaging-live` on topic `messaging.live` keyed by the conversation
  subject; every node consumes it with its own consumer group. Handshake auth by
  `X-User-Id` / `X-Staff-Id` (4401 without, 4403 when not a participant).
- Bulletins are plain `announce_bulletin` rows plus a live record; not event-sourced.

## 10. Out of scope for the first iteration

Real Stripe and PostNord adapters (ports plus fakes are in scope; adapters follow behind
the same ports), buyer-to-buyer resale, partial refunds, multi-item orders, invoices and
VAT reporting, seller subscriptions, promoted announces.

## 11. Implementation notes and deviations (2026-09-26)

Module `order` (`org.linlinjava.announce.{auction,order,payment,shipping}`, Boot app and cross-cutting
config in `org.linlinjava.announce.orderservice`), contracts in `order-api` (and `messaging-api` for the offer door), schema in
`db-reactive/sql/announce_order.sql`. `ContextBoundariesTest` (ArchUnit) proves that no root depends on
another root, on `orderservice`, and that the slices are free of cycles; `order.api` is the one package
under the `order` prefix that every root may import.

| Design (§2 to §9) | What was built | Why |
|---|---|---|
| Topics `announce.v1`, `auction.v1`, `order.v1`, `messaging.v1` | plus `payment.v1` and `shipping.v1`; `order.v1` also carries `OrderShippedV1`, `OrderDeliveredV1`, `PayoutReleaseRequestedV1`, `RefundRequestedV1` | "cross-context wiring only through the topics" holds inside the module too: `order` never imports `payment` or `shipping` classes, so their facts travel as public events |
| `RequestShipment` by the seller after PAID | the shipment is opened by `OrderPaidV1` (`OpenShipment`, status OPEN), then the seller books it (`RequestShipment`) | shipping learns who may book without reading order's tables |
| "order refuses AcceptPurchase / OpenAuction until the seller account exists" | payment publishes `SellerAccountUpdatedV1`; `order` and `auction` each keep a one-row-per-seller table (`announce_order_seller`, `announce_auction_seller`) and refuse with `seller-not-payable`; `order.seller-account.required` / `auction.seller-account.required` (default true) | the gate is data each context owns; the fake provider verifies at onboarding so local flows work at once |
| `AnnouncePublishedV1` built by a relay | `AnnouncePublished` (internal) carries seller, content and details; currency comes from `announce.currency` (default DKK) | `Money` has no currency and the relay maps synchronously without a read-model lookup |
| announce "back to PUBLISHED" on cancel / refund | new system command `RelistAnnounce` in announce-management (no-op unless SOLD; keeps the expiry when still ahead) | `AnnounceStatus.canBePublished()` excludes SOLD on purpose for owners |
| auction settings on the announce | an auction opened from `AnnouncePublishedV1` uses the announce price as start price and the `auction.*` defaults (increment, anti-sniping window, duration capped by the announce expiry); no reserve or snag-it price yet | the announce carries no auction fields; a later `EditAnnounce` extension can supply them |
| proxy bidding (`maxAmount`) | stored on the bid, not acted on | out of the first iteration; the column is there |
| `announce_order` money columns | `decimal(10,2)` in the read models, `long` minor units in commands, events and public records | one conversion at the projection |
| `PayoutReleaseRequested` on delivery grace | the release rule in `OrderRules.checkRelease`: DISPUTED blocks (`dispute-open`); DELIVERED releases after `delivery-grace` or on `ConfirmReceipt`; SHIPPED with `release.on=DELIVERY` is refused (`not-delivered`) until `dispatch-grace` elapses; SHIPPED with `release.on=DISPATCH` releases after `delivery-grace` counted from dispatch or on `ConfirmReceipt` | matches §3; the graces are validated against the allowed ranges at start-up |
| `ShipmentFailed` -> staff dispute path | the shipping fact opens a SYSTEM dispute on the order (`carrier: <reason>`); staff resolve it as RELEASE or REFUND | keeps the payout blocked without a new state |
| ids | orders from the auction door are `UUID.nameUUIDFromBytes("auction:" + announceId)`, from the offer door `"offer:" + conversationId`; a redelivered closing or offer event maps to the same subject and is skipped | idempotent doors without a dedup table |
| webhooks | `POST /api/payments/webhook/fake` and `POST /api/shipping/webhook/fake`, HMAC-SHA256 hex of the raw body in `X-Fake-Signature` with `payment.fake.secret` / `shipping.fake.secret`; a provider event id already applied is acknowledged and ignored; the `stripe` and `postnord` hooks answer 501 | the real adapters are stubs behind the ports (`StripeConnectPaymentProvider`, `PostNordCarrier`) |
| `announce_order_snapshot` | keyed by announce id, fed by `announce.v1` (`orderAnnounceEvents`); `RequestPurchase` needs it PUBLISHED, GOODS and not AUCTION | the ACL of §5 |
| `OfferAcceptedV1` | the record of `messaging-api` (type `messaging.offer-accepted` v1, fields as in §9); `order` depends on `messaging-api` | one contract, published by messaging and consumed by order |

Jobs (`@Scheduled`, `*.jobs.enabled`, one `runOnce()` each for tests and operators): `AuctionClosingJob`,
`OrderPayWindowJob`, `OrderShipWindowJob`, `OrderReleaseJob`, `ShipmentTrackingJob`. Admin endpoints:
`POST /api/admin/orders/{id}/resolve-dispute`, `POST /api/admin/orders/{id}/release`,
`POST /api/admin/bids/{id}/cancel`, `POST /api/admin/auctions/{announceId}/cancel`,
`POST /api/admin/auctions/close-due`. Staff come from `X-Admin-Id`, members from `X-User-Id`.

Tests: `AuctionTest`, `OrderTest`, `PaymentTest`, `ShipmentTest` (CommandFixture), `ContextBoundariesTest`
(ArchUnit) and `OrderModuleEndToEndTest` (EventSourcingDB, Kafka and MySQL containers, the fakes, a mutable clock,
a test producer standing in for announce-management and messaging, a probe on `order.v1`). announce-management's
`AnnounceKindsEndToEndTest` covers the other direction (SOLD on `OrderPaidV1`, relist on `OrderRefundedV1`).

### Fixes after the first full-stack run (2026-09-27)

- The processor groups read `/` and stopped at start on announce-management's internal event types
  (`EventTypeResolutionException`, all eight groups). Now `auction-*`, `order-*` and `shipping-*` fetch
  their own root (`/auction`, `/order`, `/shipment`); `payment-*` keeps `/` because it owns `/payment` and
  `/seller-account`, and the framework's `TolerantEventTypeResolver` skips foreign types (`doc/cqrs-reactive.md` §2).
- announce-management's relay `announce-public` failed with a null pointer on the first `AnnouncePublished`
  written before the event carried seller, content and details, and retried forever, so `announce.v1` stayed
  empty and no snapshot or auction ever appeared. Such legacy events are now skipped with a warning
  (`AnnounceMessaging.toPublished` / `toEdited`); announces published before 2026-09-26 are unknown to the
  order module until they are edited or republished.
