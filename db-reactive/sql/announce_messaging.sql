-- =====================================================================
--  Announce: messaging bounded context (module `messaging`)
--
--  Read models of the Conversation aggregate (/conversation/{id} in
--  EventSourcingDB) plus the staff bulletin board. The tables are written
--  by projections only (at least once, idempotent on the ids and seq
--  numbers the aggregate assigns); the checkpoint of each projection lives
--  in cqrs_readside_progress in this same database.
--
--  Origin: the chat tables of announce_auction_chat.sql (ActionBazaar chat
--  and bulletin, "EJB 3 in Action" ch. 14) with goods_id -> announce_id
--  varchar(36), conversation and message ids chosen by the command side
--  (UUID varchar(36)), per-conversation message sequence numbers.
--
--  Conventions kept from litemall: add_time / update_time, logical delete
--  flag `deleted`, utf8mb4, no FK constraints (indexes instead).
--
--  Run AFTER announce_table.sql. Idempotent (CREATE TABLE IF NOT EXISTS).
-- =====================================================================

SET NAMES utf8mb4;

-- One conversation: LISTING (buyer <-> seller about one announce; the id is
-- derived from announce_id + buyer_id so the pair has exactly one thread) or
-- SUPPORT (member <-> staff; WAITING until a staff member joins).
CREATE TABLE IF NOT EXISTS `announce_conversation` (
  `id`                    varchar(36)   NOT NULL COMMENT 'conversation id (UUID); LISTING: name UUID of announce_id + buyer_id',
  `type`                  varchar(20)   NOT NULL COMMENT 'LISTING (buyer <-> seller about an announce), SUPPORT (member <-> staff)',
  `announce_id`           varchar(36)   DEFAULT NULL COMMENT 'announce the chat is about (LISTING)',
  `buyer_id`              int(11)       DEFAULT NULL COMMENT 'LISTING: the member who opened it (announce_user.id)',
  `seller_id`             int(11)       DEFAULT NULL COMMENT 'LISTING: owner of the announce (announce_user.id)',
  `subject`               varchar(127)  DEFAULT NULL COMMENT 'SUPPORT: what the member asks about',
  `status`                varchar(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'WAITING (no staff assigned yet), OPEN, CLOSED, BLOCKED',
  `created_by_type`       varchar(10)   NOT NULL DEFAULT 'USER' COMMENT 'USER (announce_user) or ADMIN (announce_admin)',
  `created_by`            int(11)       NOT NULL COMMENT 'id of the initiator',
  `last_message_id`       varchar(36)   DEFAULT NULL,
  `last_message_seq`      int(11)       NOT NULL DEFAULT '0' COMMENT 'seq of the last message; message_count == last_message_seq',
  `last_message_time`     datetime      DEFAULT NULL,
  `message_count`         int(11)       NOT NULL DEFAULT '0',
  `open_offer_message_id` varchar(36)   DEFAULT NULL COMMENT 'latest OFFER message still open (LISTING)',
  `open_offer_amount`     decimal(10,2) DEFAULT NULL,
  `open_offer_currency`   char(3)       DEFAULT NULL,
  `accepted_offer_amount` decimal(10,2) DEFAULT NULL COMMENT 'set once the seller accepted an offer (OfferAcceptedV1 published)',
  `accepted_offer_currency` char(3)     DEFAULT NULL,
  `accepted_offer_time`   datetime      DEFAULT NULL,
  `closed_time`           datetime      DEFAULT NULL,
  `blocked_reason`        varchar(255)  DEFAULT NULL,
  `last_event_id`         varchar(64)   DEFAULT NULL COMMENT 'EventSourcingDB id of the last event projected into this row',
  `add_time`              datetime      DEFAULT NULL,
  `update_time`           datetime      DEFAULT NULL,
  `deleted`               tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_listing` (`announce_id`, `buyer_id`),
  KEY `idx_conv_seller` (`seller_id`),
  KEY `idx_conv_status_last` (`status`, `last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat conversations';

-- Who takes part. A LISTING chat has two USER participants (BUYER, SELLER);
-- a SUPPORT chat has one USER (MEMBER) and, once picked from the waiting
-- queue, one ADMIN (CSR). last_read_seq is written by MarkRead directly
-- (a read marker is not an event); unread = messages with seq > last_read_seq.
CREATE TABLE IF NOT EXISTS `announce_conversation_participant` (
  `id`               int(11)     NOT NULL AUTO_INCREMENT,
  `conversation_id`  varchar(36) NOT NULL,
  `participant_type` varchar(10) NOT NULL COMMENT 'USER (announce_user) or ADMIN (announce_admin)',
  `participant_id`   int(11)     NOT NULL,
  `role`             varchar(10) NOT NULL COMMENT 'BUYER, SELLER, MEMBER, CSR',
  `joined_time`      datetime    NOT NULL,
  `left_time`        datetime    DEFAULT NULL,
  `last_read_seq`    int(11)     NOT NULL DEFAULT '0' COMMENT 'seq of the last message this participant has read',
  `muted`            tinyint(1)  NOT NULL DEFAULT '0',
  `add_time`         datetime    DEFAULT NULL,
  `update_time`      datetime    DEFAULT NULL,
  `deleted`          tinyint(1)  DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_participant` (`conversation_id`, `participant_type`, `participant_id`),
  KEY `idx_participant_lookup` (`participant_type`, `participant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Conversation participants';

-- The messages. The aggregate assigns the id (UUID) and the per-conversation
-- seq, so a replayed MessagePosted event upserts the same row. client_msg_id
-- is the idempotency key the WebSocket / REST client sends; the aggregate
-- answers a duplicate with the original message and writes no event.
CREATE TABLE IF NOT EXISTS `announce_chat_message` (
  `id`              varchar(36)   NOT NULL,
  `conversation_id` varchar(36)   NOT NULL,
  `seq`             int(11)       NOT NULL COMMENT '1-based position in the conversation',
  `sender_type`     varchar(10)   NOT NULL COMMENT 'USER, ADMIN, SYSTEM',
  `sender_id`       int(11)       DEFAULT NULL COMMENT 'NULL for SYSTEM',
  `content_type`    varchar(20)   NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT, IMAGE, OFFER (price proposal), SYSTEM',
  `content`         text          NOT NULL,
  `attachment_url`  varchar(255)  DEFAULT NULL COMMENT 'IMAGE: the picture URL (no storage here)',
  `offer_amount`    decimal(10,2) DEFAULT NULL COMMENT 'OFFER: proposed price',
  `offer_currency`  char(3)       DEFAULT NULL,
  `offer_status`    varchar(20)   DEFAULT NULL COMMENT 'OFFER: OPEN, ACCEPTED, DECLINED, SUPERSEDED (a newer offer replaced it)',
  `client_msg_id`   varchar(63)   DEFAULT NULL COMMENT 'idempotency key from the client; SYSTEM messages use the causing event id',
  `sent_time`       datetime      NOT NULL,
  `add_time`        datetime      DEFAULT NULL,
  `update_time`     datetime      DEFAULT NULL,
  `deleted`         tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_seq` (`conversation_id`, `seq`),
  UNIQUE KEY `uk_msg_client` (`conversation_id`, `client_msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat messages';

-- Staff broadcast board (book: BulletinService, CSR dashboard messages
-- fanned out to all connected CSRs, optionally per client type). Not
-- event-sourced: a plain row plus a live record on the messaging.live topic.
CREATE TABLE IF NOT EXISTS `announce_bulletin` (
  `id`          int(11)       NOT NULL AUTO_INCREMENT,
  `admin_id`    int(11)       NOT NULL COMMENT 'author (announce_admin.id)',
  `audience`    varchar(20)   NOT NULL DEFAULT 'STAFF' COMMENT 'STAFF (CSR dashboard) or MEMBERS (site-wide banner)',
  `client_type` varchar(20)   DEFAULT NULL COMMENT 'book: desktop, mobile-web, mobile-native; NULL = all',
  `content`     varchar(1023) NOT NULL,
  `expire_time` datetime      DEFAULT NULL,
  `add_time`    datetime      DEFAULT NULL,
  `update_time` datetime      DEFAULT NULL,
  `deleted`     tinyint(1)    DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_bulletin_audience` (`audience`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Broadcast bulletins';
