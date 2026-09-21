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
-- Table structure for table `marketplace_user`
--
DROP TABLE IF EXISTS `marketplace_user`;
CREATE TABLE `marketplace_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(63) NOT NULL COMMENT 'User name',
  `email` varchar(255) NOT NULL COMMENT 'Email address',
  `password` varchar(255) NOT NULL DEFAULT '' COMMENT 'Hashed password',
  `phone` varchar(20) DEFAULT '' COMMENT 'Phone number',
  `avatar` varchar(255) DEFAULT '' COMMENT 'Profile picture',
  `bio` varchar(511) DEFAULT '' COMMENT 'Short bio/description',
  `social_media` json DEFAULT NULL COMMENT 'Social media links as JSON',
  `location` varchar(255) DEFAULT '' COMMENT 'User location',
  `latitude` decimal(10,8) DEFAULT NULL COMMENT 'Location latitude',
  `longitude` decimal(10,8) DEFAULT NULL COMMENT 'Location longitude',
  `is_verified` tinyint(1) DEFAULT '0' COMMENT 'Email verification status',
  `user_type` enum('PRIVATE','COMPANY') DEFAULT 'PRIVATE' COMMENT 'Seller type',
  `rating_avg` decimal(3,2) DEFAULT '0.00' COMMENT 'Average rating',
  `status` tinyint(3) DEFAULT '0' COMMENT '0 active, 1 suspended, 2 banned',
  `last_login_time` datetime DEFAULT NULL,
  `last_login_ip` varchar(63) DEFAULT '',
  `email_verification_token` varchar(255) DEFAULT NULL,
  `password_reset_token` varchar(255) DEFAULT NULL,
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `location` (`location`),
  KEY `user_type` (`user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_category`
--
DROP TABLE IF EXISTS `marketplace_category`;
CREATE TABLE `marketplace_category` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(63) NOT NULL DEFAULT '',
  `description` varchar(255) DEFAULT '',
  `parent_id` int(11) DEFAULT '0' COMMENT 'Parent category ID',
  `icon_url` varchar(255) DEFAULT '',
  `image_url` varchar(255) DEFAULT '',
  `level` int(11) DEFAULT '1' COMMENT 'Category depth level',
  `sort_order` int(11) DEFAULT '50',
  `custom_attributes_schema` json DEFAULT NULL COMMENT 'JSON schema for category-specific attributes',
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `parent_id` (`parent_id`),
  KEY `level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_listing`  Listings/Announcements
--
DROP TABLE IF EXISTS `marketplace_listing`;
CREATE TABLE `marketplace_listing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `category_id` int(11) NOT NULL,
  `title` varchar(255) NOT NULL DEFAULT '',
  `description` text,
  `price` decimal(15,2) DEFAULT NULL COMMENT 'NULL for free/swap items',
  `price_type` enum('FIXED','FREE','SWAP','AUCTION') DEFAULT 'FIXED',
  `condition` enum('NEW','LIKE_NEW','GOOD','FAIR','POOR') DEFAULT 'GOOD',
  `status` enum('ACTIVE','SOLD','EXPIRED','DELETED','PENDING') DEFAULT 'ACTIVE',
  `location` varchar(255) DEFAULT '',
  `latitude` decimal(10,8) DEFAULT NULL,
  `longitude` decimal(10,8) DEFAULT NULL,
  `contact_methods` json DEFAULT NULL COMMENT 'Preferred contact methods',
  `view_count` int(11) DEFAULT '0',
  `save_count` int(11) DEFAULT '0',
  `is_promoted` tinyint(1) DEFAULT '0',
  `promotion_expires` datetime DEFAULT NULL,
  `bump_count` int(11) DEFAULT '0',
  `last_bumped_at` datetime DEFAULT NULL,
  `custom_attributes` json DEFAULT NULL COMMENT 'Category-specific attributes',
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `category_id` (`category_id`),
  KEY `status` (`status`),
  KEY `price` (`price`),
  KEY `location` (`location`),
  KEY `is_promoted` (`is_promoted`),
  FULLTEXT KEY `search_index` (`title`,`description`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_listing_image`
--
DROP TABLE IF EXISTS `marketplace_listing_image`;
CREATE TABLE `marketplace_listing_image` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `listing_id` int(11) NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `thumbnail_url` varchar(255) DEFAULT NULL,
  `sort_order` int(11) DEFAULT '0',
  `is_primary` tinyint(1) DEFAULT '0',
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `listing_id` (`listing_id`),
  KEY `sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_favorite`
--
DROP TABLE IF EXISTS `marketplace_favorite`;
CREATE TABLE `marketplace_favorite` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `listing_id` int(11) NOT NULL,
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_listing` (`user_id`,`listing_id`),
  KEY `user_id` (`user_id`),
  KEY `listing_id` (`listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_conversation`
--
DROP TABLE IF EXISTS `marketplace_conversation`;
CREATE TABLE `marketplace_conversation` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `listing_id` int(11) NOT NULL,
  `buyer_id` int(11) NOT NULL,
  `seller_id` int(11) NOT NULL,
  `last_message_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `listing_id` (`listing_id`),
  KEY `buyer_id` (`buyer_id`),
  KEY `seller_id` (`seller_id`),
  KEY `last_message_at` (`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_message`
--
DROP TABLE IF EXISTS `marketplace_message`;
CREATE TABLE `marketplace_message` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `conversation_id` int(11) NOT NULL,
  `sender_id` int(11) NOT NULL,
  `content` text NOT NULL,
  `message_type` enum('TEXT','IMAGE','FILE') DEFAULT 'TEXT',
  `file_url` varchar(255) DEFAULT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `conversation_id` (`conversation_id`),
  KEY `sender_id` (`sender_id`),
  KEY `is_read` (`is_read`),
  KEY `add_time` (`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_review`
--
DROP TABLE IF EXISTS `marketplace_review`;
CREATE TABLE `marketplace_review` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `reviewer_id` int(11) NOT NULL COMMENT 'User writing review',
  `reviewee_id` int(11) NOT NULL COMMENT 'User being reviewed',
  `listing_id` int(11) NOT NULL,
  `rating` tinyint(1) NOT NULL COMMENT '1-5 stars',
  `comment` text,
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_review` (`reviewer_id`,`listing_id`),
  KEY `reviewee_id` (`reviewee_id`),
  KEY `listing_id` (`listing_id`),
  KEY `rating` (`rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketplace_saved_search`
--

DROP TABLE IF EXISTS `marketplace_saved_search`;
CREATE TABLE `marketplace_saved_search` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `query` varchar(255) NOT NULL,
  `filters` json DEFAULT NULL,
  `category_id` int(11) DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;


-- Admin & Moderation
DROP TABLE IF EXISTS `marketplace_report`;
CREATE TABLE `marketplace_report` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `reporter_id` int(11) NOT NULL,
  `reported_user_id` int(11) DEFAULT NULL,
  `reported_listing_id` int(11) DEFAULT NULL,
  `reason` varchar(255) NOT NULL,
  `description` text,
  `status` enum('PENDING','RESOLVED','REJECTED') DEFAULT 'PENDING',
  `admin_notes` text,
  `add_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `reporter_id` (`reporter_id`),
  KEY `reported_user_id` (`reported_user_id`),
  KEY `reported_listing_id` (`reported_listing_id`),
  KEY `status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
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
