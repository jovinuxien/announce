-- MySQL dump 10.13  Distrib 5.7.17, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: litemall
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
-- Table structure for table `announce_cart`
--

DROP TABLE IF EXISTS `announce_cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_cart` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT NULL COMMENT '用户表的用户ID',
  `goods_id` int(11) DEFAULT NULL COMMENT '商品表的商品ID',
  `goods_sn` varchar(63) DEFAULT NULL COMMENT '商品编号',
  `goods_name` varchar(127) DEFAULT NULL COMMENT '商品名称',
  `product_id` int(11) DEFAULT NULL COMMENT '商品货品表的货品ID',
  `price` decimal(10,2) DEFAULT '0.00' COMMENT '商品货品的价格',
  `number` smallint(5) DEFAULT '0' COMMENT '商品货品的数量',
  `specifications` varchar(1023) DEFAULT NULL COMMENT '商品规格值列表，采用JSON数组格式',
  `checked` tinyint(1) DEFAULT '1' COMMENT '购物车中商品是否选择状态',
  `pic_url` varchar(255) DEFAULT NULL COMMENT '商品图片或者商品货品图片',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COMMENT='购物车商品表';
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
-- Table structure for table `announce_order`
--

DROP TABLE IF EXISTS `announce_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_order` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT '用户表的用户ID',
  `order_sn` varchar(63) NOT NULL COMMENT '订单编号',
  `order_status` smallint(6) NOT NULL COMMENT '订单状态',
  `aftersale_status` smallint(6) DEFAULT '0' COMMENT '售后状态，0是可申请，1是用户已申请，2是管理员审核通过，3是管理员退款成功，4是管理员审核拒绝，5是用户已取消',
  `consignee` varchar(63) NOT NULL COMMENT '收货人名称',
  `mobile` varchar(63) NOT NULL COMMENT '收货人手机号',
  `address` varchar(127) NOT NULL COMMENT '收货具体地址',
  `message` varchar(512) NOT NULL DEFAULT '' COMMENT '用户订单留言',
  `goods_price` decimal(10,2) NOT NULL COMMENT '商品总费用',
  `freight_price` decimal(10,2) NOT NULL COMMENT '配送费用',
  `coupon_price` decimal(10,2) NOT NULL COMMENT '优惠券减免',
  `integral_price` decimal(10,2) NOT NULL COMMENT '用户积分减免',
  `groupon_price` decimal(10,2) NOT NULL COMMENT '团购优惠价减免',
  `order_price` decimal(10,2) NOT NULL COMMENT '订单费用， = goods_price + freight_price - coupon_price',
  `actual_price` decimal(10,2) NOT NULL COMMENT '实付费用， = order_price - integral_price',
  `pay_id` varchar(63) DEFAULT NULL COMMENT '微信付款编号',
  `pay_time` datetime DEFAULT NULL COMMENT '微信付款时间',
  `ship_sn` varchar(63) DEFAULT NULL COMMENT '发货编号',
  `ship_channel` varchar(63) DEFAULT NULL COMMENT '发货快递公司',
  `ship_time` datetime DEFAULT NULL COMMENT '发货开始时间',
  `refund_amount` decimal(10,2) DEFAULT NULL COMMENT '实际退款金额，（有可能退款金额小于实际支付金额）',
  `refund_type` varchar(63) DEFAULT NULL COMMENT '退款方式',
  `refund_content` varchar(127) DEFAULT NULL COMMENT '退款备注',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '用户确认收货时间',
  `comments` smallint(6) DEFAULT '0' COMMENT '待评价订单商品数量',
  `end_time` datetime DEFAULT NULL COMMENT '订单关闭时间',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `announce_order_goods`
--

DROP TABLE IF EXISTS `announce_order_goods`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `announce_order_goods` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `order_id` int(11) NOT NULL DEFAULT '0' COMMENT '订单表的订单ID',
  `goods_id` int(11) NOT NULL DEFAULT '0' COMMENT '商品表的商品ID',
  `goods_name` varchar(127) NOT NULL DEFAULT '' COMMENT '商品名称',
  `goods_sn` varchar(63) NOT NULL DEFAULT '' COMMENT '商品编号',
  `product_id` int(11) NOT NULL DEFAULT '0' COMMENT '商品货品表的货品ID',
  `number` smallint(5) NOT NULL DEFAULT '0' COMMENT '商品货品的购买数量',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品货品的售价',
  `specifications` varchar(1023) NOT NULL COMMENT '商品货品的规格列表',
  `pic_url` varchar(255) NOT NULL DEFAULT '' COMMENT '商品货品图片或者商品图片',
  `comment` int(11) DEFAULT '0' COMMENT '订单商品评论，如果是-1，则超期不能评价；如果是0，则可以评价；如果其他值，则是comment表里面的评论ID。',
  `add_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `order_id` (`order_id`),
  KEY `goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品表';
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
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crawled_listing_source_external` (`source`, `external_id`),
  KEY `idx_crawled_listing_status_seen` (`status`, `last_seen_at`),
  KEY `idx_crawled_listing_source_hash` (`source`, `content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Crawled listings: snippet + link out, indexed next to the announces';
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
