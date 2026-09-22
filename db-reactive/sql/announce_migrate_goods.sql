-- =====================================================================
--  One-off migration: announce_goods (mall style, one table) -> announce (shared columns of every
--  kind) + announce_goods_detail, with the goods children re-keyed by announce_id and a `kind`
--  on announce_category. Produced the seed data now in announce_data_en.sql / announce_data.sql.
--
--  Preconditions: the database still holds the old announce_goods table (with or without the
--  seller_id / sale_type / expire_time / status / location columns that announce_auction_chat.sql
--  used to add) and the four new tables from announce_table.sql already exist:
--  announce, announce_goods_detail, announce_job, announce_event.
--
--  Every migrated row becomes kind GOODS, sale type FIXED_PRICE, owned by member 1 (the seeded
--  user), status PUBLISHED when it was on sale and DRAFT otherwise, without location and without
--  expiry. The numeric goods id is kept as the announce id (varchar), so the goods_id columns of
--  the order, cart, auction, chat and marketing tables still match by value.
-- =====================================================================
SET NAMES utf8mb4;

INSERT INTO `announce` (`id`, `kind`, `seller_id`, `category_id`, `title`, `brief`, `detail`, `gallery`, `keywords`,
                        `region_id`, `city`, `postal_code`, `latitude`, `longitude`, `status`, `published_at`, `expires_at`,
                        `last_event_id`, `add_time`, `update_time`, `deleted`)
SELECT CAST(g.`id` AS CHAR), 'GOODS', 1, IFNULL(g.`category_id`, 0), g.`name`, g.`brief`, g.`detail`, g.`gallery`, g.`keywords`,
       NULL, NULL, NULL, NULL, NULL,
       CASE WHEN g.`deleted` = 1 THEN 'WITHDRAWN' WHEN g.`is_on_sale` = 1 THEN 'PUBLISHED' ELSE 'DRAFT' END,
       CASE WHEN g.`is_on_sale` = 1 THEN g.`add_time` ELSE NULL END, NULL,
       NULL, g.`add_time`, g.`update_time`, g.`deleted`
FROM `announce_goods` g;

INSERT INTO `announce_goods_detail` (`announce_id`, `price`, `condition`, `sale_type`, `brand_id`, `unit`)
SELECT CAST(g.`id` AS CHAR), IFNULL(g.`retail_price`, 0), NULL, 'FIXED_PRICE',
       NULLIF(g.`brand_id`, 0), IF(g.`unit` IS NULL OR g.`unit` = '' OR g.`unit` = '件' OR g.`unit` = '’件‘', 'pcs', g.`unit`)
FROM `announce_goods` g;

-- children keyed by the announce id (the int values convert to their decimal string)
ALTER TABLE `announce_goods_product`
  DROP INDEX `goods_id`,
  CHANGE COLUMN `goods_id` `announce_id` varchar(36) NOT NULL COMMENT 'announce.id (kind GOODS)',
  ADD KEY `idx_product_announce` (`announce_id`);
ALTER TABLE `announce_goods_attribute`
  DROP INDEX `goods_id`,
  CHANGE COLUMN `goods_id` `announce_id` varchar(36) NOT NULL COMMENT 'announce.id (kind GOODS)',
  ADD KEY `idx_attribute_announce` (`announce_id`);
ALTER TABLE `announce_goods_specification`
  DROP INDEX `goods_id`,
  CHANGE COLUMN `goods_id` `announce_id` varchar(36) NOT NULL COMMENT 'announce.id (kind GOODS)',
  ADD KEY `idx_specification_announce` (`announce_id`);

-- category trees per kind: everything that exists is goods; add a root and three children for jobs and for events
ALTER TABLE `announce_category`
  ADD COLUMN `kind` varchar(10) NOT NULL DEFAULT 'GOODS' COMMENT 'announce kind this category tree belongs to: GOODS, JOB, EVENT' AFTER `deleted`,
  ADD KEY `idx_category_kind` (`kind`, `pid`);
UPDATE `announce_category` SET `kind` = 'GOODS';
INSERT INTO `announce_category` (`id`, `name`, `keywords`, `desc`, `pid`, `icon_url`, `pic_url`, `level`, `sort_order`, `add_time`, `update_time`, `deleted`, `kind`) VALUES
 (1037000, 'Jobs',              '', 'Find your next job',              0,       '', '', 'L1', 1, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'JOB'),
 (1037001, 'IT and software',   '', 'Developers, ops, data',           1037000, '', '', 'L2', 1, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'JOB'),
 (1037002, 'Healthcare',        '', 'Nurses, doctors, care',           1037000, '', '', 'L2', 2, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'JOB'),
 (1037003, 'Hospitality',       '', 'Restaurants, hotels, tourism',    1037000, '', '', 'L2', 3, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'JOB'),
 (1038000, 'Events',            '', 'What is on near you',             0,       '', '', 'L1', 1, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'EVENT'),
 (1038001, 'Concerts',          '', 'Live music',                      1038000, '', '', 'L2', 1, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'EVENT'),
 (1038002, 'Sports',            '', 'Matches, races, tournaments',     1038000, '', '', 'L2', 2, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'EVENT'),
 (1038003, 'Markets and fairs', '', 'Flea markets, fairs, festivals',  1038000, '', '', 'L2', 3, '2026-09-22 00:00:00', '2026-09-22 00:00:00', 0, 'EVENT');

DROP TABLE `announce_goods`;
