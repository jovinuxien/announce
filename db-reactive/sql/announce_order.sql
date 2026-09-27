-- =====================================================================
--  Announce: read models of the order module (doc/order-context.md §5)
--
--  Four bounded contexts, one package root each in the `order` module:
--  auction, order (the deal), payment, shipping. Every table here is a
--  read model fed by a projection from the EventSourcingDB event store;
--  the projections are at-least-once and idempotent (upsert by id,
--  `last_event_id` records the event applied). A table can be dropped and
--  rebuilt at any time with ProjectionRebuilder.
--
--  Ids are the aggregate ids of the command side (UUID strings, varchar(36)):
--  an auction is keyed by its announce id (/auction/{announceId}), a payment
--  and a shipment by their order id (/payment/{orderId}, /shipment/{orderId}).
--  Members keep their int ids from announce_user. No FK constraints, indexes
--  instead (the litemall convention).
--
--  Run AFTER announce_table.sql and announce_auction_chat.sql; replaces the
--  litemall announce_order / announce_order_goods / announce_cart tables and
--  the int-keyed announce_auction* tables of the first auction draft.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. Auction context (/auction/{announceId})
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `announce_auction`;
CREATE TABLE `announce_auction` (
  `id`              varchar(36)   NOT NULL COMMENT 'aggregate id = announce id',
  `announce_id`     varchar(36)   NOT NULL,
  `seller_id`       int(11)       NOT NULL COMMENT 'announce_user.id of the member who posted the announce',
  `title`           varchar(127)  NOT NULL DEFAULT '' COMMENT 'snapshot from AnnouncePublishedV1',
  `main_picture`    varchar(255)  NOT NULL DEFAULT '',
  `currency`        char(3)       NOT NULL DEFAULT 'DKK',
  `initial_price`   decimal(10,2) NOT NULL,
  `reserve_price`   decimal(10,2) DEFAULT NULL COMMENT 'NULL = no reserve',
  `snag_it_price`   decimal(10,2) DEFAULT NULL COMMENT 'buy-now price, valid only while no bid exists',
  `bid_increment`   decimal(10,2) NOT NULL DEFAULT '1.00',
  `current_price`   decimal(10,2) NOT NULL COMMENT 'denormalised: initial_price or highest active bid',
  `bid_count`       int(11)       NOT NULL DEFAULT '0',
  `start_time`      datetime      NOT NULL,
  `end_time`        datetime      NOT NULL COMMENT 'moved forward by anti-sniping extensions',
  `auto_extend_sec` int(11)       NOT NULL DEFAULT '0' COMMENT 'anti-sniping window; 0 = off',
  `status`          varchar(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN, CLOSED_WON, CLOSED_UNSOLD, CLOSED_SNAGGED, CANCELLED',
  `winning_bid_id`  varchar(36)   DEFAULT NULL COMMENT 'announce_bid.id',
  `winner_id`       int(11)       DEFAULT NULL COMMENT 'announce_user.id of the winner or snagger',
  `closed_time`     datetime      DEFAULT NULL,
  `last_event_id`   varchar(64)   DEFAULT NULL,
  `add_time`        datetime      DEFAULT NULL,
  `update_time`     datetime      DEFAULT NULL,
  `deleted`         tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auction_announce` (`announce_id`),
  KEY `idx_auction_seller` (`seller_id`),
  KEY `idx_auction_status_end` (`status`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Timed auction attached to an announce (read model)';

DROP TABLE IF EXISTS `announce_bid`;
CREATE TABLE `announce_bid` (
  `id`            varchar(36)   NOT NULL COMMENT 'bid id chosen by the command side',
  `auction_id`    varchar(36)   NOT NULL COMMENT 'announce_auction.id',
  `announce_id`   varchar(36)   NOT NULL COMMENT 'denormalised',
  `bidder_id`     int(11)       NOT NULL COMMENT 'announce_user.id',
  `amount`        decimal(10,2) NOT NULL,
  `max_amount`    decimal(10,2) DEFAULT NULL COMMENT 'proxy bidding ceiling; stored, not yet acted on',
  `bid_time`      datetime      NOT NULL,
  `status`        varchar(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE (current highest), OUTBID, WON, CANCELLED (by staff), RETRACTED (by bidder)',
  `cancel_reason` varchar(255)  DEFAULT NULL,
  `cancelled_by`  int(11)       DEFAULT NULL COMMENT 'announce_admin.id',
  `add_time`      datetime      DEFAULT NULL,
  `update_time`   datetime      DEFAULT NULL,
  `deleted`       tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_bid_auction_amount` (`auction_id`, `amount`),
  KEY `idx_bid_bidder` (`bidder_id`),
  KEY `idx_bid_announce` (`announce_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bids placed on auctions (read model)';

DROP TABLE IF EXISTS `announce_auction_event`;
CREATE TABLE `announce_auction_event` (
  `id`              int(11)      NOT NULL AUTO_INCREMENT,
  `auction_id`      varchar(36)  NOT NULL,
  `bid_id`          varchar(36)  DEFAULT NULL,
  `event_type`      varchar(30)  NOT NULL COMMENT 'OPENED, BID_PLACED, OUTBID, EXTENDED, SNAGGED, BID_RETRACTED, BID_CANCELLED, CLOSED_WON, CLOSED_UNSOLD, CANCELLED',
  `actor_id`        int(11)      DEFAULT NULL,
  `actor_type`      varchar(10)  DEFAULT NULL COMMENT 'USER, ADMIN, SYSTEM',
  `payload`         text         COMMENT 'JSON of the internal event',
  `event_time`      datetime     NOT NULL,
  `source_event_id` varchar(64)  NOT NULL COMMENT 'EventSourcingDB id; makes the append idempotent',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auction_event_source` (`source_event_id`),
  KEY `idx_auction_event_auction` (`auction_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Auction event log (read model, feeds the live feed)';

DROP TABLE IF EXISTS `announce_auction_seller`;
CREATE TABLE `announce_auction_seller` (
  `id`              int(11)     NOT NULL COMMENT 'announce_user.id',
  `payouts_enabled` tinyint(1)  NOT NULL DEFAULT '0' COMMENT 'from SellerAccountUpdatedV1 (payment.v1); OpenAuction is refused while 0',
  `account_status`  varchar(20) DEFAULT NULL COMMENT 'PENDING, VERIFIED, RESTRICTED',
  `last_event_id`   varchar(64) DEFAULT NULL,
  `update_time`     datetime    DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='The auction context''s copy of who can be paid';

-- ---------------------------------------------------------------------
-- 2. Order context (/order/{orderId}): one deal = one announce, one buyer, one seller
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `announce_order`;
CREATE TABLE `announce_order` (
  `id`                varchar(36)   NOT NULL,
  `announce_id`       varchar(36)   NOT NULL,
  `seller_id`         int(11)       NOT NULL,
  `buyer_id`          int(11)       NOT NULL,
  `door`              varchar(16)   NOT NULL COMMENT 'AUCTION, FIXED_PRICE, OFFER',
  `auction_id`        varchar(36)   DEFAULT NULL,
  `conversation_id`   varchar(36)   DEFAULT NULL COMMENT 'messaging conversation of an OFFER deal',
  `title`             varchar(127)  NOT NULL DEFAULT '' COMMENT 'announce snapshot at agreement',
  `main_picture`      varchar(255)  NOT NULL DEFAULT '',
  `agreed_price`      decimal(10,2) NOT NULL,
  `fee_amount`        decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'platform fee, fixed at agreement by FeeSchedule',
  `payout_amount`     decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'agreed_price - fee_amount',
  `label_price`       decimal(10,2) DEFAULT NULL COMMENT 'shipping label bought through us; passed through, never part of the payout',
  `currency`          char(3)       NOT NULL DEFAULT 'DKK',
  `status`            varchar(24)   NOT NULL COMMENT 'PROPOSED, AGREED, PAYMENT_PENDING, PAID, SHIPPED, DELIVERED, RELEASING, SETTLED, DISPUTED, REFUNDING, REFUNDED, CANCELLED, DECLINED',
  `payment_id`        varchar(36)   DEFAULT NULL,
  `shipment_id`       varchar(36)   DEFAULT NULL,
  `agreed_at`         datetime      DEFAULT NULL,
  `paid_at`           datetime      DEFAULT NULL,
  `shipped_at`        datetime      DEFAULT NULL,
  `delivered_at`      datetime      DEFAULT NULL,
  `receipt_confirmed_at` datetime   DEFAULT NULL,
  `released_at`       datetime      DEFAULT NULL,
  `settled_at`        datetime      DEFAULT NULL,
  `closed_at`         datetime      DEFAULT NULL COMMENT 'cancelled, declined or refunded',
  `dispute_reason`    varchar(255)  DEFAULT NULL,
  `dispute_opened_at` datetime      DEFAULT NULL,
  `last_event_id`     varchar(64)   DEFAULT NULL,
  `add_time`          datetime      DEFAULT NULL,
  `update_time`       datetime      DEFAULT NULL,
  `deleted`           tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order_buyer` (`buyer_id`, `status`),
  KEY `idx_order_seller` (`seller_id`, `status`),
  KEY `idx_order_announce` (`announce_id`),
  KEY `idx_order_status_time` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A deal between a buyer and a seller on one announce (read model)';

DROP TABLE IF EXISTS `announce_order_snapshot`;
CREATE TABLE `announce_order_snapshot` (
  `id`            varchar(36)   NOT NULL COMMENT 'announce id',
  `announce_id`   varchar(36)   NOT NULL,
  `kind`          varchar(10)   NOT NULL COMMENT 'GOODS, JOB, EVENT',
  `seller_id`     int(11)       NOT NULL,
  `title`         varchar(127)  NOT NULL DEFAULT '',
  `main_picture`  varchar(255)  NOT NULL DEFAULT '',
  `price`         decimal(10,2) NOT NULL DEFAULT '0.00',
  `currency`      char(3)       NOT NULL DEFAULT 'DKK',
  `sale_type`     varchar(16)   DEFAULT NULL COMMENT 'FIXED_PRICE, AUCTION, GIVEAWAY; NULL for jobs and events',
  `category_id`   int(11)       DEFAULT NULL,
  `status`        varchar(16)   NOT NULL COMMENT 'PUBLISHED, SOLD, WITHDRAWN, EXPIRED',
  `published_at`  datetime      DEFAULT NULL,
  `expires_at`    datetime      DEFAULT NULL,
  `last_event_id` varchar(64)   DEFAULT NULL,
  `update_time`   datetime      DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_snapshot_announce` (`announce_id`),
  KEY `idx_order_snapshot_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='What the order context knows about an announce, from announce.v1 (the ACL)';

DROP TABLE IF EXISTS `announce_order_seller`;
CREATE TABLE `announce_order_seller` (
  `id`              int(11)     NOT NULL COMMENT 'announce_user.id',
  `payouts_enabled` tinyint(1)  NOT NULL DEFAULT '0' COMMENT 'from SellerAccountUpdatedV1 (payment.v1); AcceptPurchase is refused while 0',
  `account_status`  varchar(20) DEFAULT NULL,
  `last_event_id`   varchar(64) DEFAULT NULL,
  `update_time`     datetime    DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='The order context''s copy of who can be paid';

-- ---------------------------------------------------------------------
-- 3. Payment context (/payment/{orderId}): the marketplace payment provider
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `announce_payment`;
CREATE TABLE `announce_payment` (
  `id`            varchar(36)   NOT NULL COMMENT 'aggregate id = order id',
  `order_id`      varchar(36)   NOT NULL,
  `announce_id`   varchar(36)   DEFAULT NULL,
  `buyer_id`      int(11)       NOT NULL,
  `seller_id`     int(11)       NOT NULL,
  `provider`      varchar(20)   NOT NULL COMMENT 'FAKE, STRIPE',
  `intent_ref`    varchar(128)  DEFAULT NULL COMMENT 'provider payment intent id',
  `client_secret` varchar(255)  DEFAULT NULL COMMENT 'handed to the buyer''s client to complete the payment',
  `status`        varchar(20)   NOT NULL COMMENT 'INTENT_CREATED, CONFIRMED, FAILED, RELEASING, PAID_OUT, REFUNDING, REFUNDED, CANCELLED',
  `amount`        decimal(10,2) NOT NULL,
  `fee_amount`    decimal(10,2) NOT NULL DEFAULT '0.00',
  `payout_amount` decimal(10,2) DEFAULT NULL,
  `refund_amount` decimal(10,2) DEFAULT NULL,
  `currency`      char(3)       NOT NULL DEFAULT 'DKK',
  `payout_ref`    varchar(128)  DEFAULT NULL COMMENT 'provider transfer id',
  `refund_ref`    varchar(128)  DEFAULT NULL COMMENT 'provider refund id',
  `failure_reason` varchar(255) DEFAULT NULL,
  `confirmed_at`  datetime      DEFAULT NULL,
  `released_at`   datetime      DEFAULT NULL COMMENT 'payout sent',
  `refunded_at`   datetime      DEFAULT NULL,
  `last_event_id` varchar(64)   DEFAULT NULL,
  `add_time`      datetime      DEFAULT NULL,
  `update_time`   datetime      DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_order` (`order_id`),
  KEY `idx_payment_intent` (`provider`, `intent_ref`),
  KEY `idx_payment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Provider-side state of a deal''s payment (read model)';

DROP TABLE IF EXISTS `announce_seller_account`;
CREATE TABLE `announce_seller_account` (
  `id`              int(11)      NOT NULL COMMENT 'announce_user.id',
  `user_id`         int(11)      NOT NULL,
  `provider`        varchar(20)  NOT NULL COMMENT 'FAKE, STRIPE',
  `account_ref`     varchar(128) DEFAULT NULL COMMENT 'provider connected account id',
  `status`          varchar(20)  NOT NULL COMMENT 'PENDING, VERIFIED, RESTRICTED',
  `payouts_enabled` tinyint(1)   NOT NULL DEFAULT '0',
  `onboarding_url`  varchar(512) DEFAULT NULL COMMENT 'where the seller finishes KYC at the provider',
  `last_event_id`   varchar(64)  DEFAULT NULL,
  `add_time`        datetime     DEFAULT NULL,
  `update_time`     datetime     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seller_account_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Connected payout account of a seller at the payment provider (read model)';

-- ---------------------------------------------------------------------
-- 4. Shipping context (/shipment/{orderId}): the carrier integration
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS `announce_shipment`;
CREATE TABLE `announce_shipment` (
  `id`                    varchar(36)  NOT NULL COMMENT 'aggregate id = order id',
  `order_id`              varchar(36)  NOT NULL,
  `seller_id`             int(11)      NOT NULL,
  `buyer_id`              int(11)      NOT NULL,
  `carrier`               varchar(20)  DEFAULT NULL COMMENT 'FAKE, POSTNORD',
  `service`               varchar(40)  DEFAULT NULL COMMENT 'carrier product, e.g. MyPack Home',
  `parcel_size`           varchar(10)  DEFAULT NULL COMMENT 'S, M, L, XL',
  `tracking_number`       varchar(64)  DEFAULT NULL,
  `label_url`             varchar(512) DEFAULT NULL,
  `status`                varchar(20)  NOT NULL COMMENT 'OPEN (paid, not booked), REQUESTED, DISPATCHED, IN_TRANSIT, DELIVERED, FAILED, CANCELLED',
  `last_carrier_event`    varchar(40)  DEFAULT NULL,
  `last_carrier_event_id` varchar(128) DEFAULT NULL,
  `last_event_at`         datetime     DEFAULT NULL COMMENT 'time of the last carrier event',
  `last_polled_at`        datetime     DEFAULT NULL COMMENT 'ShipmentTrackingJob',
  `requested_at`          datetime     DEFAULT NULL,
  `dispatched_at`         datetime     DEFAULT NULL,
  `delivered_at`          datetime     DEFAULT NULL,
  `last_event_id`         varchar(64)  DEFAULT NULL,
  `add_time`              datetime     DEFAULT NULL,
  `update_time`           datetime     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_order` (`order_id`),
  KEY `idx_shipment_tracking` (`carrier`, `tracking_number`),
  KEY `idx_shipment_status_polled` (`status`, `last_polled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Carrier-side state of a deal''s shipment (read model)';
