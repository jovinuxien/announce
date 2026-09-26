-- =====================================================================
--  Announce: auction and chat extension (announce_* tables) of the litemall base schema
--
--  Derived from the ActionBazaar domain model in "EJB 3 in Action, 2nd ed."
--  (Manning 2014): Seller/Bidder/User, Item, Bid, Category, Order, snag-it
--  ordering, featured item, shipping request, CSR chat and bulletin.
--  Mapped onto our existing tables (announce = an announce/listing of any kind,
--  announce_user = member, announce_admin = staff/CSR, announce_order).
--
--  Conventions kept from litemall: int(11) AUTO_INCREMENT ids, add_time /
--  update_time, logical delete flag `deleted`, utf8mb4, no FK constraints
--  (the application enforces references; indexes are declared instead).
--
--  Run AFTER announce_table.sql and BEFORE announce_order.sql. Idempotent for the CREATE TABLE part;
--  the ALTER TABLE part is guarded with a stored procedure (see bottom).
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. Auction bounded context: the auction, bid and auction-event tables moved to
--    announce_order.sql (read models of the order module, keyed by announce id).
--    Kept here: featured item, trade rating, billing info.
-- ---------------------------------------------------------------------

-- Daily spotlight (book: featured item singleton reloaded at midnight).
CREATE TABLE IF NOT EXISTS `announce_featured_item` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT,
  `goods_id`    int(11)      NOT NULL,
  `start_time`  datetime     NOT NULL,
  `end_time`    datetime     NOT NULL,
  `position`    smallint(6)  NOT NULL DEFAULT '0' COMMENT 'ordering on the home page',
  `admin_id`    int(11)      DEFAULT NULL COMMENT 'staff who scheduled it',
  `add_time`    datetime     DEFAULT NULL,
  `update_time` datetime     DEFAULT NULL,
  `deleted`     tinyint(1)   DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_featured_window` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Featured announces';

-- Post-trade rating between the two parties of an order (book: Seller.status
-- gold/silver tiers derived from successful trades; Tradera-style feedback).
CREATE TABLE IF NOT EXISTS `announce_trade_rating` (
  `id`           int(11)       NOT NULL AUTO_INCREMENT,
  `order_id`     int(11)       NOT NULL,
  `auction_id`   int(11)       DEFAULT NULL,
  `from_user_id` int(11)       NOT NULL,
  `to_user_id`   int(11)       NOT NULL,
  `role`         varchar(10)   NOT NULL COMMENT 'role of from_user in the trade: BUYER or SELLER',
  `score`        tinyint(3)    NOT NULL COMMENT '1..5',
  `comment`      varchar(511)  DEFAULT NULL,
  `add_time`     datetime      DEFAULT NULL,
  `update_time`  datetime      DEFAULT NULL,
  `deleted`      tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rating_order_from` (`order_id`, `from_user_id`),
  KEY `idx_rating_to_user` (`to_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Buyer/seller ratings per trade';

-- Tokenised payment method (book: BillingInfo one-to-one with User). Never
-- store card numbers; keep the PSP token and display data only.
CREATE TABLE IF NOT EXISTS `announce_billing_info` (
  `id`             int(11)      NOT NULL AUTO_INCREMENT,
  `user_id`        int(11)      NOT NULL,
  `provider`       varchar(31)  NOT NULL COMMENT 'STRIPE, SWISH, MOBILEPAY, VIPPS, ...',
  `provider_token` varchar(255) NOT NULL COMMENT 'token / customer id at the provider',
  `method_type`    varchar(20)  NOT NULL COMMENT 'CARD, BANK, WALLET',
  `display_name`   varchar(63)  DEFAULT NULL COMMENT 'e.g. VISA **** 4242',
  `is_default`     tinyint(1)   NOT NULL DEFAULT '0',
  `add_time`       datetime     DEFAULT NULL,
  `update_time`    datetime     DEFAULT NULL,
  `deleted`        tinyint(1)   DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_billing_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Member payment methods (tokenised)';

-- ---------------------------------------------------------------------
-- 2. Messaging bounded context (chat between members, and member <-> staff)
--    Book: ChatServer keeps client sessions, CSR sessions and
--    SupportConversation objects; CSRs broadcast on a bulletin board.
--    WebSocket sessions stay in memory; only conversations, participants,
--    messages and bulletins are persisted.
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `announce_conversation` (
  `id`                int(11)      NOT NULL AUTO_INCREMENT,
  `type`              varchar(20)  NOT NULL COMMENT 'LISTING (buyer <-> seller about an announce), SUPPORT (member <-> staff)',
  `goods_id`          int(11)      DEFAULT NULL COMMENT 'announce the chat is about (LISTING)',
  `auction_id`        int(11)      DEFAULT NULL COMMENT 'set when the announce is an auction',
  `subject`           varchar(127) DEFAULT NULL,
  `status`            varchar(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'WAITING (no staff assigned yet), OPEN, CLOSED, BLOCKED',
  `created_by`        int(11)      NOT NULL COMMENT 'announce_user.id of the initiator',
  `last_message_id`   int(11)      DEFAULT NULL,
  `last_message_time` datetime     DEFAULT NULL,
  `message_count`     int(11)      NOT NULL DEFAULT '0',
  `closed_time`       datetime     DEFAULT NULL,
  `add_time`          datetime     DEFAULT NULL,
  `update_time`       datetime     DEFAULT NULL,
  `deleted`           tinyint(1)   DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_conv_goods` (`goods_id`),
  KEY `idx_conv_status_last` (`status`, `last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat conversations';

-- Who takes part. A LISTING chat has two USER participants; a SUPPORT chat
-- has one USER and, once picked from the waiting queue, one ADMIN (CSR).
CREATE TABLE IF NOT EXISTS `announce_conversation_participant` (
  `id`                   int(11)     NOT NULL AUTO_INCREMENT,
  `conversation_id`      int(11)     NOT NULL,
  `participant_type`     varchar(10) NOT NULL COMMENT 'USER (announce_user) or ADMIN (announce_admin)',
  `participant_id`       int(11)     NOT NULL,
  `role`                 varchar(10) NOT NULL COMMENT 'BUYER, SELLER, MEMBER, CSR',
  `joined_time`          datetime    NOT NULL,
  `left_time`            datetime    DEFAULT NULL,
  `last_read_message_id` int(11)     DEFAULT NULL COMMENT 'unread = messages with id > this',
  `muted`                tinyint(1)  NOT NULL DEFAULT '0',
  `add_time`             datetime    DEFAULT NULL,
  `update_time`          datetime    DEFAULT NULL,
  `deleted`              tinyint(1)  DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_participant` (`conversation_id`, `participant_type`, `participant_id`),
  KEY `idx_participant_lookup` (`participant_type`, `participant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Conversation participants';

-- The messages themselves (book: ChatMessage {type, user, message}).
CREATE TABLE IF NOT EXISTS `announce_chat_message` (
  `id`              int(11)       NOT NULL AUTO_INCREMENT,
  `conversation_id` int(11)       NOT NULL,
  `sender_type`     varchar(10)   NOT NULL COMMENT 'USER, ADMIN, SYSTEM',
  `sender_id`       int(11)       DEFAULT NULL COMMENT 'NULL for SYSTEM',
  `content_type`    varchar(20)   NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT, IMAGE, OFFER (price proposal), SYSTEM',
  `content`         text          NOT NULL,
  `attachment_url`  varchar(255)  DEFAULT NULL,
  `offer_amount`    decimal(10,2) DEFAULT NULL COMMENT 'for OFFER messages',
  `client_msg_id`   varchar(63)   DEFAULT NULL COMMENT 'idempotency key from the WebSocket client',
  `sent_time`       datetime      NOT NULL,
  `edited_time`     datetime      DEFAULT NULL,
  `add_time`        datetime      DEFAULT NULL,
  `update_time`     datetime      DEFAULT NULL,
  `deleted`         tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_conversation` (`conversation_id`, `id`),
  UNIQUE KEY `uk_msg_client` (`conversation_id`, `client_msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat messages';

-- Staff broadcast board (book: BulletinService, CSR dashboard messages
-- fanned out to all connected CSRs, optionally per client type).
CREATE TABLE IF NOT EXISTS `announce_bulletin` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT,
  `admin_id`    int(11)      NOT NULL COMMENT 'author (announce_admin.id)',
  `audience`    varchar(20)  NOT NULL DEFAULT 'STAFF' COMMENT 'STAFF (CSR dashboard) or MEMBERS (site-wide banner)',
  `client_type` varchar(20)  DEFAULT NULL COMMENT 'book: desktop, mobile-web, mobile-native; NULL = all',
  `content`     varchar(1023) NOT NULL,
  `expire_time` datetime     DEFAULT NULL,
  `add_time`    datetime     DEFAULT NULL,
  `update_time` datetime     DEFAULT NULL,
  `deleted`     tinyint(1)   DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_bulletin_audience` (`audience`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Broadcast bulletins';

-- ---------------------------------------------------------------------
-- 3. Columns added to existing tables (guarded so the script can be re-run)
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS announce_add_column;
DELIMITER $$
CREATE PROCEDURE announce_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- Member trading profile (book: User.status gold/silver, numTrades; Seller.commissionRate).
CALL announce_add_column('announce_user', 'trade_tier',      '`trade_tier` varchar(10) NOT NULL DEFAULT ''BRONZE'' COMMENT ''BRONZE, SILVER, GOLD, PLATINUM; recomputed from num_trades'' AFTER `user_level`');
CALL announce_add_column('announce_user', 'num_trades',      '`num_trades` int(11) NOT NULL DEFAULT ''0'' AFTER `trade_tier`');
CALL announce_add_column('announce_user', 'commission_rate', '`commission_rate` decimal(5,2) DEFAULT NULL COMMENT ''seller commission in percent; NULL = platform default'' AFTER `num_trades`');
CALL announce_add_column('announce_user', 'seller_verified', '`seller_verified` tinyint(1) NOT NULL DEFAULT ''0'' AFTER `commission_rate`');

-- The order table is defined in announce_order.sql with auction_id and seller_id built in.

-- Note: seller_id, sale_type, expire_time, status and the location columns of the former announce_goods table
-- now live in `announce` and `announce_goods_detail` (announce_table.sql); nothing is altered on goods here.

DROP PROCEDURE IF EXISTS announce_add_index;
DELIMITER $$
CREATE PROCEDURE announce_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` ', p_ddl);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
-- (no index to add: the order table's indexes are in announce_order.sql)
DROP PROCEDURE IF EXISTS announce_add_index;

DROP PROCEDURE IF EXISTS announce_add_column;
