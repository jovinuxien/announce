# Auction and chat schema

Source: *EJB 3 in Action, 2nd edition* (Manning, 2014), the ActionBazaar case study.
The book models an eBay-like site; this note maps its model onto our litemall-derived
schema for a Blocket / DBA / Tradera style classifieds application.
DDL: `db-reactive/sql/announce_auction_chat.sql` (run after `litemall_table.sql`).

## 1. What the book says

### Domain model (chapter 9, figure 9.3)

| Entity | Relationships | Notable fields in the book |
|---|---|---|
| User (abstract) | Seller and Bidder extend it; one BillingInfo; many phone numbers and shipping addresses | userId, username, email, picture, userType (SELLER, BIDDER, CSR, ADMIN), status (gold/silver by numTrades) |
| Seller | sells 0..* Items | commissionRate, creditWorth |
| Bidder | places 0..* Bids; places 0..* Orders | bidFrequency |
| Item | sold by 1 Seller; has 0..* Bids; in 1..* Categories | title, description, picture, initialPrice, postDate, starRating |
| Bid | placed on 1 Item by 1 Bidder | amount, timestamp |
| Category | tree (parent / subcategories); many-to-many with Item | name, rating |
| Order | placed by 1 Bidder; contains 1..* Items | shipping, billing, total with shipping and handling |

### Auction principle (chapters 2, 3, 6)

1. A seller posts an item with an initial price into a category.
2. Bidders place bids. `BidService.addBid`, `getBids(item)`, `cancelBid` (staff may remove bids).
3. The auction is timed. When it ends the highest bidder wins.
4. The winner orders the item. The order is confirmed immediately; billing runs asynchronously; on success the seller ships and both sides get email.
5. **Snag-it** (chapter 6): buy the item at a set price *only while no bid exists*. Validate card, charge, close bidding, all in one transaction.
6. **Featured item** (chapter 3): one spotlighted item per day, reloaded at midnight.
7. Shipping requests go to the carrier as messages carrying item, address, method and insurance amount (chapter 4).
8. Timers (chapter 7) drive scheduled work: newsletters, flyers, and by extension auction closing.

### Placement

"Placement" in the book is two things: *placing a bid* (`BidService`) and *placing an order* for the item won (`OrderProcessor`). Both persist a row and both are the only user-triggered write paths in the core scenario.

### Chat (chapter 14)

* A member opens a WebSocket chat; a customer service representative (CSR) picks the member from a waiting queue.
* `ChatServer` (singleton) holds client sessions, CSR sessions and `SupportConversation` objects. Messages are `{type, user, message}` in JSON.
* A separate **bulletin** endpoint lets CSRs broadcast to all connected CSRs, optionally per client type (desktop, mobile-web, mobile-native).
* WebSocket sessions are runtime state and are not persisted.

## 2. Mapping to our schema

### Already covered by existing tables

| Book | Ours | Notes |
|---|---|---|
| Item | `announce_goods` | An announce. Gains `seller_id`, `sale_type`, `expire_time`. |
| Category | `announce_category` | Tree via `pid`. We keep one category per announce, not many-to-many. |
| User / Bidder / Seller | `announce_user` | Single table; every member may buy and sell. Gains `trade_tier`, `num_trades`, `commission_rate`, `seller_verified`. |
| CSR / Admin | `announce_admin` with roles | Staff stays in its own table. |
| Order, order lines | `announce_order`, `announce_order_goods` | Gains `auction_id`, `seller_id`. Shipping columns already exist (`ship_sn`, `ship_channel`, `ship_time`). |
| Shipping addresses | `announce_address` | Book's Address element collection. |
| Watch list | `announce_collect` | Book has none; Tradera has it. |
| Reviews on an item | `announce_comment` | Book's starRating. |
| Notifications | `announce_notice`, `announce_notice_admin` | Book's newsletter and flyer. |

### New tables

| Table | Purpose | From the book |
|---|---|---|
| `announce_auction` | One row per auctioned announce: initial, reserve and snag-it price, bid increment, start and end, status, denormalised current price and bid count, winning bid, order, optimistic-lock `version` | Item.initialPrice, timed auction, snag-it |
| `announce_bid` | Every bid with amount, optional proxy `max_amount`, status ACTIVE / OUTBID / WON / CANCELLED / RETRACTED, staff cancel reason | Bid, `cancelBid` |
| `announce_auction_event` | Append-only log: OPENED, BID_PLACED, OUTBID, EXTENDED, SNAGGED, CLOSED_* | Feeds the live feed and CQRS projections; replaces the container timer state |
| `announce_featured_item` | Scheduled spotlight windows | Featured item singleton |
| `announce_trade_rating` | Buyer and seller rate each other per order | User.status tiers, Tradera feedback |
| `announce_billing_info` | Tokenised payment method per member (provider token only) | BillingInfo one-to-one |
| `announce_conversation` | LISTING chats (buyer and seller about an announce) and SUPPORT chats (member and staff), with WAITING status for the CSR queue | SupportConversation, waiting clients |
| `announce_conversation_participant` | Members or staff in a conversation, role, last read message | client and CSR sessions |
| `announce_chat_message` | Text, image or price OFFER messages, idempotency key from the client | ChatMessage |
| `announce_bulletin` | Staff broadcasts per audience and client type | BulletinService |

### Deliberately not added

* No table for WebSocket sessions or timers. Sessions live in memory per node; auction closing is a scheduler that selects `status = 'OPEN' AND end_time <= now()`.
* No card numbers. `announce_billing_info` stores the payment provider token only.
* No item-to-category join table. One category per announce is enough for a classifieds site and matches the existing category tree.

## 3. Rules the application must enforce

* A bid is accepted only if the auction is OPEN, `now` is inside the window, the bidder is not the seller, and `amount >= current_price + bid_increment`. Accepting a bid updates `current_price`, `bid_count`, marks the previous ACTIVE bid OUTBID and bumps `version`. Do it in one transaction with the optimistic lock.
* Snag-it is allowed only while `bid_count = 0`, then the auction closes with `CLOSED_SNAGGED` and an order is created.
* When `end_time` passes: if the highest ACTIVE bid meets `reserve_price`, close `CLOSED_WON`, set `winning_bid_id`, mark the bid WON and create the order. Otherwise `CLOSED_UNSOLD`.
* With `auto_extend_sec > 0`, a bid landing inside that window pushes `end_time` forward and logs EXTENDED.
* A SUPPORT conversation starts WAITING with one USER participant; a CSR joining as ADMIN participant moves it to OPEN.

## 4. Bounded contexts

* **Auction** context: `announce_auction`, `announce_bid`, `announce_auction_event`, `announce_featured_item`, `announce_trade_rating`.
* **Messaging** context: `announce_conversation`, `announce_conversation_participant`, `announce_chat_message`, `announce_bulletin`.
* **Member** context owns `announce_user` and `announce_billing_info`; **Goods** owns `announce_goods`; **Order** owns `announce_order`.
