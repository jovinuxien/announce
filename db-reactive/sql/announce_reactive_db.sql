-- =====================================================================
--  announce_reactive_db : bootstrap script for the Announce application
--
--  Creates the database and its user, then the full schema:
--    1. the base tables inherited from litemall, renamed announce_* (29 tables, from announce_table.sql)
--    2. the auction and chat extension (10 tables + added columns,
--       from announce_auction_chat.sql)
--
--  Run as a MySQL administrator:
--      mysql -uroot -p < db-reactive/sql/announce_reactive_db.sql
--  Optional seed data for development (reference data, demo goods):
--      mysql -uannounce -p announce_reactive_db < db-reactive/sql/announce_data_en.sql
--
--  The server must run with character_set_server=utf8mb4 for emoji support.
--  Change the password before any deployment outside a developer machine.
-- =====================================================================

DROP DATABASE IF EXISTS `announce_reactive_db`;
CREATE DATABASE `announce_reactive_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'announce'@'localhost' IDENTIFIED BY 'Calliste_1006';
CREATE USER IF NOT EXISTS 'announce'@'%'         IDENTIFIED BY 'Calliste_1006';
GRANT ALL PRIVILEGES ON `announce_reactive_db`.* TO 'announce'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON `announce_reactive_db`.* TO 'announce'@'%'         WITH GRANT OPTION;
FLUSH PRIVILEGES;

USE `announce_reactive_db`;

-- ---------------------------------------------------------------------
-- 1. Base tables (generated from announce_table.sql; regenerate this file
--    with: sql/build_announce_reactive_db.sh)
-- ---------------------------------------------------------------------
-- MySQL dump 10.13  Distrib 5.7.17, for Win64 (x86_64)
--
-- ------------------------------------------------------
-- Server version	5.7.21-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `announce_ad`
--

DROP TABLE IF EXISTS `announce_ad`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_ad` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(63) NOT NULL DEFAULT '' COMMENT '广告标题',
  `link` varchar(255) NOT NULL DEFAULT '' COMMENT '所广告的商品页面或者活动页面链接地址',
  `url` varchar(255) NOT NULL COMMENT '广告宣传图片',
  `position` tinyint(3) DEFAULT '1' COMMENT '广告位置：1则是首页',
  `content` varchar(255) DEFAULT '' COMMENT '活动内容',
  `start_time` datetime DEFAULT NULL COMMENT '广告开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '广告结束时间',
  `enabled` tinyint(1) DEFAULT '0' COMMENT '是否启动',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='广告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_address`
--

DROP TABLE IF EXISTS `announce_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_address` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(63) NOT NULL DEFAULT '' COMMENT '收货人名称',
  `user_id` int(11) NOT NULL DEFAULT '0' COMMENT '用户表的用户ID',
  `province` varchar(63) NOT NULL COMMENT '行政区域表的省ID',
  `city` varchar(63) NOT NULL COMMENT '行政区域表的市ID',
  `county` varchar(63) NOT NULL COMMENT '行政区域表的区县ID',
  `address_detail` varchar(127) NOT NULL DEFAULT '' COMMENT '详细收货地址',
  `area_code` char(6) DEFAULT NULL COMMENT '地区编码',
  `postal_code` char(6) DEFAULT NULL COMMENT '邮政编码',
  `tel` varchar(20) NOT NULL DEFAULT '' COMMENT '手机号码',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否默认地址',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_admin`
--

DROP TABLE IF EXISTS `announce_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_admin` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(63) NOT NULL DEFAULT '' COMMENT '管理员名称',
  `password` varchar(63) NOT NULL DEFAULT '' COMMENT '管理员密码',
  `last_login_ip` varchar(63) DEFAULT '' COMMENT '最近一次登录IP地址',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近一次登录时间',
  `avatar` varchar(255) DEFAULT '''' COMMENT '头像图片',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `role_ids` varchar(127) DEFAULT '[]' COMMENT '角色列表',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_brand`
--

DROP TABLE IF EXISTS `announce_brand`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_brand` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '' COMMENT '品牌商名称',
  `desc` varchar(255) NOT NULL DEFAULT '' COMMENT '品牌商简介',
  `pic_url` varchar(255) NOT NULL DEFAULT '' COMMENT '品牌商页的品牌商图片',
  `sort_order` tinyint(3) DEFAULT '50',
  `floor_price` decimal(10,2) DEFAULT '0.00' COMMENT '品牌商的商品低价，仅用于页面展示',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1046003 DEFAULT CHARSET=utf8mb4 COMMENT='品牌商表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_category`
--

DROP TABLE IF EXISTS `announce_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_category` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(63) NOT NULL DEFAULT '' COMMENT '类目名称',
  `keywords` varchar(1023) NOT NULL DEFAULT '' COMMENT '类目关键字，以JSON数组格式',
  `desc` varchar(255) DEFAULT '' COMMENT '类目广告语介绍',
  `pid` int(11) NOT NULL DEFAULT '0' COMMENT '父类目ID',
  `icon_url` varchar(255) DEFAULT '' COMMENT '类目图标',
  `pic_url` varchar(255) DEFAULT '' COMMENT '类目图片',
  `level` varchar(255) DEFAULT 'L1',
  `sort_order` tinyint(3) DEFAULT '50' COMMENT '排序',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `kind` varchar(10) NOT NULL DEFAULT 'GOODS' COMMENT 'announce kind this category tree belongs to: GOODS, JOB, EVENT',
  PRIMARY KEY (`id`),
  KEY `parent_id` (`pid`),
  KEY `idx_category_kind` (`kind`, `pid`)
) ENGINE=InnoDB AUTO_INCREMENT=1036007 DEFAULT CHARSET=utf8mb4 COMMENT='类目表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_collect`
--

DROP TABLE IF EXISTS `announce_collect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_collect` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT '0' COMMENT '用户表的用户ID',
  `value_id` int(11) NOT NULL DEFAULT '0' COMMENT '如果type=0，则是商品ID；如果type=1，则是专题ID',
  `type` tinyint(3) NOT NULL DEFAULT '0' COMMENT '收藏类型，如果type=0，则是商品ID；如果type=1，则是专题ID',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `goods_id` (`value_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_comment`
--

DROP TABLE IF EXISTS `announce_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_comment` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `value_id` int(11) NOT NULL DEFAULT '0' COMMENT '如果type=0，则是商品评论；如果是type=1，则是专题评论。',
  `type` tinyint(3) NOT NULL DEFAULT '0' COMMENT '评论类型，如果type=0，则是商品评论；如果是type=1，则是专题评论；',
  `content` varchar(1023) DEFAULT '' COMMENT '评论内容',
  `admin_content` varchar(511) DEFAULT '' COMMENT '管理员回复内容',
  `user_id` int(11) NOT NULL DEFAULT '0' COMMENT '用户表的用户ID',
  `has_picture` tinyint(1) DEFAULT '0' COMMENT '是否含有图片',
  `pic_urls` varchar(1023) DEFAULT NULL COMMENT '图片地址列表，采用JSON数组格式',
  `star` smallint(6) DEFAULT '1' COMMENT '评分， 1-5',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `id_value` (`value_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1012 DEFAULT CHARSET=utf8mb4 COMMENT='评论表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_feedback`
--

DROP TABLE IF EXISTS `announce_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_feedback` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT '0' COMMENT '用户表的用户ID',
  `username` varchar(63) NOT NULL DEFAULT '' COMMENT '用户名称',
  `mobile` varchar(20) NOT NULL DEFAULT '' COMMENT '手机号',
  `feed_type` varchar(63) NOT NULL DEFAULT '' COMMENT '反馈类型',
  `content` varchar(1023) NOT NULL COMMENT '反馈内容',
  `status` int(3) NOT NULL DEFAULT '0' COMMENT '状态',
  `has_picture` tinyint(1) DEFAULT '0' COMMENT '是否含有图片',
  `pic_urls` varchar(1023) DEFAULT NULL COMMENT '图片地址列表，采用JSON数组格式',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `id_value` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_footprint`
--

DROP TABLE IF EXISTS `announce_footprint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_footprint` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL DEFAULT '0' COMMENT '用户表的用户ID',
  `goods_id` int(11) NOT NULL DEFAULT '0' COMMENT '浏览商品ID',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览足迹表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce` (read model of the Announce aggregate, fed by the announce-read-model projection)
-- The id is the aggregate id chosen on the command side (subject /announce/{id}); the seed rows keep their old numeric ids.
--

DROP TABLE IF EXISTS `announce`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce` (
  `id` varchar(36) NOT NULL COMMENT 'aggregate id (subject /announce/{id})',
  `kind` varchar(10) NOT NULL COMMENT 'GOODS, JOB, EVENT',
  `seller_id` int(11) NOT NULL COMMENT 'member who posted the announce (announce_user.id)',
  `category_id` int(11) NOT NULL COMMENT 'announce_category.id (a category of the same kind)',
  `title` varchar(127) NOT NULL DEFAULT '',
  `brief` varchar(255) DEFAULT '',
  `detail` text COMMENT 'description, rich text',
  `gallery` varchar(1023) DEFAULT NULL COMMENT 'picture urls, JSON array',
  `keywords` varchar(255) DEFAULT '' COMMENT 'comma separated',
  `region_id` int(11) DEFAULT NULL COMMENT 'announce_region.id of the municipality / area',
  `city` varchar(63) DEFAULT NULL,
  `postal_code` varchar(16) DEFAULT NULL,
  `latitude` decimal(9,6) DEFAULT NULL,
  `longitude` decimal(9,6) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, PUBLISHED, SOLD, WITHDRAWN, EXPIRED',
  `published_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL COMMENT 'hidden after this time (scheduler expires it)',
  `moderated_by` int(11) DEFAULT NULL COMMENT 'announce_admin.id when staff withdrew or expired the announce',
  `moderation_reason` varchar(255) DEFAULT NULL COMMENT 'reason given by staff (null for seller / scheduler actions)',
  `last_event_id` varchar(64) DEFAULT NULL COMMENT 'EventSourcingDB id of the last event applied (projection idempotency)',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_announce_kind_status` (`kind`, `status`, `expires_at`),
  KEY `idx_announce_seller` (`seller_id`),
  KEY `idx_announce_category` (`category_id`),
  KEY `idx_announce_region` (`region_id`),
  KEY `idx_announce_expires` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Announce of any kind: shared columns';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_goods_detail` (kind GOODS: the only biddable kind)
--

DROP TABLE IF EXISTS `announce_goods_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_goods_detail` (
  `announce_id` varchar(36) NOT NULL,
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'asking price; 0 for a giveaway or an auction',
  `condition` varchar(20) DEFAULT NULL COMMENT 'NEW, LIKE_NEW, GOOD, FAIR, FOR_PARTS',
  `sale_type` varchar(20) NOT NULL DEFAULT 'FIXED_PRICE' COMMENT 'FIXED_PRICE, AUCTION, GIVEAWAY',
  `brand_id` int(11) DEFAULT NULL COMMENT 'announce_brand.id, optional',
  `unit` varchar(31) DEFAULT 'pcs',
  PRIMARY KEY (`announce_id`),
  KEY `idx_goods_detail_price` (`price`),
  KEY `idx_goods_detail_brand` (`brand_id`),
  KEY `idx_goods_detail_sale_type` (`sale_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Goods-specific columns of an announce';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_job` (kind JOB: never biddable)
--

DROP TABLE IF EXISTS `announce_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_job` (
  `announce_id` varchar(36) NOT NULL,
  `employer` varchar(127) NOT NULL DEFAULT '',
  `contract_type` varchar(20) NOT NULL DEFAULT 'FULL_TIME' COMMENT 'FULL_TIME, PART_TIME, TEMPORARY, INTERNSHIP, FREELANCE',
  `salary_min` decimal(12,2) DEFAULT NULL,
  `salary_max` decimal(12,2) DEFAULT NULL,
  `currency` varchar(3) DEFAULT NULL COMMENT 'ISO 4217, set when a salary range is given',
  `application_deadline` datetime DEFAULT NULL,
  `apply_url_or_email` varchar(255) NOT NULL DEFAULT '' COMMENT 'how to apply',
  PRIMARY KEY (`announce_id`),
  KEY `idx_job_deadline` (`application_deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Job-specific columns of an announce';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_event` (kind EVENT: never biddable)
--

DROP TABLE IF EXISTS `announce_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_event` (
  `announce_id` varchar(36) NOT NULL,
  `starts_at` datetime NOT NULL,
  `ends_at` datetime NOT NULL,
  `venue` varchar(255) DEFAULT NULL COMMENT 'free text; the location columns of announce give region / city / coordinates',
  `organizer` varchar(127) DEFAULT NULL,
  `ticketed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0 = free, 1 = ticketed',
  `ticket_price` decimal(10,2) DEFAULT NULL COMMENT 'set when ticketed',
  `capacity` int(11) DEFAULT NULL,
  PRIMARY KEY (`announce_id`),
  KEY `idx_event_starts` (`starts_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Event-specific columns of an announce';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_goods_attribute`
--

DROP TABLE IF EXISTS `announce_goods_attribute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_goods_attribute` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `announce_id` varchar(36) NOT NULL COMMENT 'announce.id (kind GOODS)',
  `attribute` varchar(255) NOT NULL COMMENT '商品参数名称',
  `value` varchar(255) NOT NULL COMMENT '商品参数值',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_attribute_announce` (`announce_id`)
) ENGINE=InnoDB AUTO_INCREMENT=877 DEFAULT CHARSET=utf8mb4 COMMENT='商品参数表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_goods_product`
--

DROP TABLE IF EXISTS `announce_goods_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_goods_product` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `announce_id` varchar(36) NOT NULL COMMENT 'announce.id (kind GOODS)',
  `specifications` varchar(1023) NOT NULL COMMENT '商品规格值列表，采用JSON数组格式',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品货品价格',
  `number` int(11) NOT NULL DEFAULT '0' COMMENT '商品货品数量',
  `url` varchar(125) DEFAULT NULL COMMENT '商品货品图片',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_announce` (`announce_id`)
) ENGINE=InnoDB AUTO_INCREMENT=251 DEFAULT CHARSET=utf8mb4 COMMENT='商品货品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_goods_specification`
--

DROP TABLE IF EXISTS `announce_goods_specification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_goods_specification` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `announce_id` varchar(36) NOT NULL COMMENT 'announce.id (kind GOODS)',
  `specification` varchar(255) NOT NULL DEFAULT '' COMMENT '商品规格名称',
  `value` varchar(255) NOT NULL DEFAULT '' COMMENT '商品规格值',
  `pic_url` varchar(255) NOT NULL DEFAULT '' COMMENT '商品规格图片',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_specification_announce` (`announce_id`)
) ENGINE=InnoDB AUTO_INCREMENT=250 DEFAULT CHARSET=utf8mb4 COMMENT='商品规格表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_issue`
--

DROP TABLE IF EXISTS `announce_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_issue` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `question` varchar(255) DEFAULT NULL COMMENT '问题标题',
  `answer` varchar(255) DEFAULT NULL COMMENT '问题答案',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COMMENT='常见问题表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_keyword`
--

DROP TABLE IF EXISTS `announce_keyword`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_keyword` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `keyword` varchar(127) NOT NULL DEFAULT '' COMMENT '关键字',
  `url` varchar(255) NOT NULL DEFAULT '' COMMENT '关键字的跳转链接',
  `is_hot` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否是热门关键字',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否是默认关键字',
  `sort_order` int(11) NOT NULL DEFAULT '100' COMMENT '排序',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COMMENT='关键字表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_log`
--

DROP TABLE IF EXISTS `announce_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `admin` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '管理员',
  `ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '管理员地址',
  `type` int(11) DEFAULT NULL COMMENT '操作分类',
  `action` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作动作',
  `status` tinyint(1) DEFAULT NULL COMMENT '操作状态',
  `result` varchar(127) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作结果，或者成功消息，或者失败消息',
  `comment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '补充信息',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_notice`
--

DROP TABLE IF EXISTS `announce_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_notice` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(63) DEFAULT NULL COMMENT '通知标题',
  `content` varchar(511) DEFAULT NULL COMMENT '通知内容',
  `admin_id` int(11) DEFAULT '0' COMMENT '创建通知的管理员ID，如果是系统内置通知则是0.',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COMMENT='通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_notice_admin`
--

DROP TABLE IF EXISTS `announce_notice_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_notice_admin` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `notice_id` int(11) DEFAULT NULL COMMENT '通知ID',
  `notice_title` varchar(63) DEFAULT NULL COMMENT '通知标题',
  `admin_id` int(11) DEFAULT NULL COMMENT '接收通知的管理员ID',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间，如果是NULL则是未读状态',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COMMENT='通知管理员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_permission`
--

DROP TABLE IF EXISTS `announce_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_permission` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `role_id` int(11) DEFAULT NULL COMMENT '角色ID',
  `permission` varchar(63) DEFAULT NULL COMMENT '权限',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_region`
--

DROP TABLE IF EXISTS `announce_region`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_region` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `pid` int(11) NOT NULL DEFAULT '0' COMMENT '行政区域父ID，例如区县的pid指向市，市的pid指向省，省的pid则是0',
  `name` varchar(120) NOT NULL DEFAULT '' COMMENT '行政区域名称',
  `type` tinyint(3) NOT NULL DEFAULT '0' COMMENT '行政区域类型，如如1则是省， 如果是2则是市，如果是3则是区县',
  `code` int(11) NOT NULL DEFAULT '0' COMMENT '行政区域编码',
  PRIMARY KEY (`id`),
  KEY `parent_id` (`pid`),
  KEY `region_type` (`type`),
  KEY `agency_id` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3232 DEFAULT CHARSET=utf8mb4 COMMENT='行政区域表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_role`
--

DROP TABLE IF EXISTS `announce_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_role` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(63) NOT NULL COMMENT '角色名称',
  `desc` varchar(1023) DEFAULT NULL COMMENT '角色描述',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name_UNIQUE` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_search_history`
--

DROP TABLE IF EXISTS `announce_search_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_search_history` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT '用户表的用户ID',
  `keyword` varchar(63) NOT NULL COMMENT '搜索关键字',
  `from` varchar(63) NOT NULL DEFAULT '' COMMENT '搜索来源，如pc、wx、app',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_storage`
--

DROP TABLE IF EXISTS `announce_storage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_storage` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `key` varchar(63) NOT NULL COMMENT '文件的唯一索引',
  `name` varchar(255) NOT NULL COMMENT '文件名',
  `type` varchar(20) NOT NULL COMMENT '文件类型',
  `size` int(11) NOT NULL COMMENT '文件大小',
  `url` varchar(255) DEFAULT NULL COMMENT '文件访问链接',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存储表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_system`
--

DROP TABLE IF EXISTS `announce_system`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_system` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `key_name` varchar(255) NOT NULL COMMENT '系统配置名',
  `key_value` varchar(255) NOT NULL COMMENT '系统配置值',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_topic`
--

DROP TABLE IF EXISTS `announce_topic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_topic` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL DEFAULT '''' COMMENT '专题标题',
  `subtitle` varchar(255) DEFAULT '''' COMMENT '专题子标题',
  `content` text COMMENT '专题内容，富文本格式',
  `price` decimal(10,2) DEFAULT '0.00' COMMENT '专题相关商品最低价',
  `read_count` varchar(255) DEFAULT '1k' COMMENT '专题阅读量',
  `pic_url` varchar(255) DEFAULT '' COMMENT '专题图片',
  `sort_order` int(11) DEFAULT '100' COMMENT '排序',
  `goods` varchar(1023) DEFAULT '' COMMENT '专题相关商品，采用JSON数组格式',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `topic_id` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=319 DEFAULT CHARSET=utf8mb4 COMMENT='专题表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_user`
--

DROP TABLE IF EXISTS `announce_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(63) NOT NULL COMMENT '用户名称',
  `password` varchar(63) NOT NULL DEFAULT '' COMMENT '用户密码',
  `gender` tinyint(3) NOT NULL DEFAULT '0' COMMENT '性别：0 未知， 1男， 1 女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近一次登录时间',
  `last_login_ip` varchar(63) NOT NULL DEFAULT '' COMMENT '最近一次登录IP地址',
  `user_level` tinyint(3) DEFAULT '0' COMMENT '0 普通用户，1 VIP用户，2 高级VIP用户',
  `nickname` varchar(63) NOT NULL DEFAULT '' COMMENT '用户昵称或网络名称',
  `mobile` varchar(20) NOT NULL DEFAULT '' COMMENT '用户手机号码',
  `avatar` varchar(255) NOT NULL DEFAULT '' COMMENT '用户头像图片',
  `weixin_openid` varchar(63) NOT NULL DEFAULT '' COMMENT '微信登录openid',
  `session_key` varchar(100) NOT NULL DEFAULT '' COMMENT '微信登录会话KEY',
  `status` tinyint(3) NOT NULL DEFAULT '0' COMMENT '0 可用, 1 禁用, 2 注销',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_name` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crawled_listing` (listings crawled from dba.dk / tradera.com; not announces)
--

DROP TABLE IF EXISTS `crawled_listing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `crawled_listing` (
  `id` varchar(64) NOT NULL COMMENT '{source}-{external_id}: deterministic, same row and same index document for a listing',
  `source` varchar(16) NOT NULL COMMENT 'DBA, TRADERA',
  `external_id` varchar(64) NOT NULL COMMENT 'the id of the listing on the source site',
  `url` varchar(1023) NOT NULL COMMENT 'canonical url of the source page (the click-out target)',
  `content_hash` char(64) NOT NULL COMMENT 'sha-256 over title, brief, price, sale_type, ends_at, attributes, image, address',
  `kind` varchar(10) NOT NULL DEFAULT 'GOODS' COMMENT 'GOODS now; JOB, EVENT later',
  `title` varchar(255) NOT NULL DEFAULT '',
  `brief` varchar(255) NOT NULL DEFAULT '' COMMENT 'snippet only, never the full text',
  `image_url` varchar(1023) DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `currency` char(3) DEFAULT NULL,
  `sale_type` varchar(20) NOT NULL DEFAULT 'FIXED_PRICE' COMMENT 'FIXED_PRICE, AUCTION',
  `ends_at` datetime DEFAULT NULL COMMENT 'auction end (tradera)',
  `source_category` varchar(255) DEFAULT NULL COMMENT 'the site''s own category path, e.g. Cars/Private Car',
  `category_id` int(11) DEFAULT NULL COMMENT 'mapped announce_category.id of kind GOODS (listing_category_map)',
  `attributes` json DEFAULT NULL COMMENT 'name -> value, e.g. km, model_year, dealer',
  `address_raw` varchar(255) DEFAULT NULL,
  `region_id` int(11) DEFAULT NULL COMMENT 'announce_region.id by city name, null when unknown',
  `city` varchar(63) DEFAULT NULL,
  `postal_code` varchar(16) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL COMMENT 'publication time on the source',
  `first_seen_at` datetime NOT NULL,
  `last_seen_at` datetime NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, GONE',
  `click_count` int(11) NOT NULL DEFAULT '0',
  `quality_grade` varchar(10) DEFAULT NULL COMMENT 'GOOD, WEAK, REJECT (ListingQuality); only GOOD and WEAK are indexed',
  `quality_issues` json DEFAULT NULL COMMENT 'list of issue codes behind the grade, e.g. ["no_published_at","image_dead"]',
  `url_checked_at` datetime DEFAULT NULL COMMENT 'last liveness check (ListingLivenessJob)',
  `url_ok` tinyint(1) DEFAULT NULL COMMENT '1 when the source url answered 2xx/3xx, 0 when 404/410 (then status is GONE); null when never checked or unreachable',
  `image_ok` tinyint(1) DEFAULT NULL COMMENT '1 when the image url answered 2xx/3xx, 0 when dead (grade downgraded); null when never checked or unreachable',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crawled_listing_source_external` (`source`, `external_id`),
  KEY `idx_crawled_listing_status_seen` (`status`, `last_seen_at`),
  KEY `idx_crawled_listing_source_hash` (`source`, `content_hash`),
  KEY `idx_crawled_listing_status_checked` (`status`, `url_checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Crawled listings: snippet + link out, indexed next to the announces';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `listing_import_run` (one row per import of a crawled feed; the inventory reads the latest per source)
--

DROP TABLE IF EXISTS `listing_import_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `listing_import_run` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `source` varchar(16) NOT NULL COMMENT 'DBA, TRADERA',
  `file` varchar(1023) DEFAULT NULL COMMENT 'the feed file imported; null for a transport without files',
  `started_at` datetime NOT NULL,
  `finished_at` datetime NOT NULL,
  `seen` int(11) NOT NULL DEFAULT '0',
  `inserted` int(11) NOT NULL DEFAULT '0',
  `updated` int(11) NOT NULL DEFAULT '0',
  `unchanged` int(11) NOT NULL DEFAULT '0',
  `rejected` int(11) NOT NULL DEFAULT '0' COMMENT 'items that could not be normalised',
  `good` int(11) NOT NULL DEFAULT '0' COMMENT 'graded GOOD in this run (inserted or updated rows)',
  `weak` int(11) NOT NULL DEFAULT '0',
  `reject` int(11) NOT NULL DEFAULT '0' COMMENT 'graded REJECT: stored with issues, not indexed',
  PRIMARY KEY (`id`),
  KEY `idx_listing_import_run_source_time` (`source`, `finished_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Import runs of crawled feeds (ImportReport per file)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `listing_category_map` (source category path -> announce_category of kind GOODS)
--

DROP TABLE IF EXISTS `listing_category_map`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `listing_category_map` (
  `source` varchar(16) NOT NULL COMMENT 'DBA, TRADERA',
  `source_category` varchar(255) NOT NULL COMMENT 'the site''s category path as the parser emits it',
  `category_id` int(11) NOT NULL COMMENT 'announce_category.id',
  PRIMARY KEY (`source`, `source_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Category mapping of crawled listings: data, not code';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_refresh_token` (edge-issued refresh tokens of members and staff; only the hash is stored)
--

DROP TABLE IF EXISTS `announce_refresh_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_refresh_token` (
  `id` varchar(36) NOT NULL COMMENT 'token id (uuid), chosen by the edge',
  `user_type` varchar(16) NOT NULL COMMENT 'MEMBER (announce_user) or ADMIN (announce_admin)',
  `user_id` int(11) NOT NULL COMMENT 'announce_user.id or announce_admin.id',
  `token_hash` char(64) NOT NULL COMMENT 'SHA-256 hex of the opaque token handed to the client',
  `expires_at` datetime NOT NULL COMMENT 'absolute expiry',
  `revoked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1 once rotated or logged out',
  `add_time` datetime DEFAULT NULL COMMENT 'issued at',
  `update_time` datetime DEFAULT NULL COMMENT 'last rotation / revocation',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_user` (`user_type`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh tokens issued by the gateway edges (gateway-api, gateway-admin)';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2019-12-16 23:12:57

-- ---------------------------------------------------------------------
-- 2. Auction extension (announce_auction_chat.sql)
-- ---------------------------------------------------------------------
-- =====================================================================
--  Announce: auction extension (announce_* tables) of the litemall base schema
--
--  Derived from the ActionBazaar domain model in "EJB 3 in Action, 2nd ed."
--  (Manning 2014): Seller/Bidder/User, Item, Bid, Category, Order, snag-it
--  ordering, featured item, shipping request. The CSR chat and bulletin
--  tables live in announce_messaging.sql.
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
  `announce_id` varchar(36)  NOT NULL COMMENT 'announce.id (any kind)',
  `start_time`  datetime     NOT NULL,
  `end_time`    datetime     NOT NULL,
  `position`    smallint(6)  NOT NULL DEFAULT '0' COMMENT 'ordering on the home page',
  `admin_id`    int(11)      DEFAULT NULL COMMENT 'staff who scheduled it',
  `add_time`    datetime     DEFAULT NULL,
  `update_time` datetime     DEFAULT NULL,
  `deleted`     tinyint(1)   DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_featured_window` (`start_time`, `end_time`),
  KEY `idx_featured_announce` (`announce_id`)
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
-- 2. Messaging bounded context: moved to announce_messaging.sql (module
--    `messaging`; announce_conversation, announce_conversation_participant,
--    announce_chat_message, announce_bulletin).
-- ---------------------------------------------------------------------

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

-- Databases created before 2026-10-01 keyed the featured item by the integer goods id; announce ids are
-- varchar(36) (subject /announce/{id}), so the column is renamed and widened once. Existing numeric ids
-- are kept as strings, like the goods rows migrated into `announce`.
DROP PROCEDURE IF EXISTS announce_change_column;
DELIMITER $$
CREATE PROCEDURE announce_change_column(IN p_table VARCHAR(64), IN p_old_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_old_column) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` CHANGE COLUMN `', p_old_column, '` ', p_ddl);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
CALL announce_change_column('announce_featured_item', 'goods_id', '`announce_id` varchar(36) NOT NULL COMMENT ''announce.id (any kind)''');
DROP PROCEDURE IF EXISTS announce_change_column;

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
-- (the order table's indexes are in announce_order.sql)
CALL announce_add_index('announce_featured_item', 'idx_featured_announce', '(`announce_id`)');
DROP PROCEDURE IF EXISTS announce_add_index;

DROP PROCEDURE IF EXISTS announce_add_column;

-- ---------------------------------------------------------------------
-- 3. Messaging context (announce_messaging.sql)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 4. Order module read models (announce_order.sql)
-- ---------------------------------------------------------------------
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
