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



INSERT INTO `marketplace_user`
(`username`, `email`, `password`, `phone`, `avatar`, `bio`, `social_media`, `location`, `latitude`, `longitude`, `is_verified`, `user_type`, `rating_avg`, `status`, `last_login_time`, `last_login_ip`)
VALUES
('alice_smith', 'alice@example.com', 'hashed_pw_1', '+12025550123', '/avatars/alice.jpg', 'Love vintage furniture and local crafts.',
    JSON_OBJECT('instagram', '@alicevintage', 'facebook', 'alicevintage'),
    'New York, USA', 40.712776, -74.005974, 1, 'PRIVATE', 4.8, 0, NOW(), '192.168.1.10'),

('bob_trading', 'bob@tradingco.com', 'hashed_pw_2', '+12025550456', '/avatars/bob.jpg', 'Trading company - second-hand electronics.',
    JSON_OBJECT('website', 'www.bobtrading.com', 'linkedin', 'bob-trading-co'),
    'Los Angeles, USA', 34.052235, -118.243683, 1, 'COMPANY', 4.6, 0, NOW(), '192.168.1.11'),

('charlie', 'charlie@example.com', 'hashed_pw_3', '+12025550789', '/avatars/charlie.png', 'Student selling used books.',
    JSON_OBJECT('twitter', '@charlielovesbooks'),
    'Boston, USA', 42.360081, -71.058884, 1, 'PRIVATE', 4.9, 0, NOW(), '192.168.1.12');

INSERT INTO `marketplace_category`
    (`name`, `description`, `parent_id`, `level`, `icon_url`, `image_url`)VALUES
    ('Electronics', 'Devices, gadgets and accessories', 0, 1, '/icons/electronics.png', '/images/categories/electronics.jpg'),
    ('Furniture', 'Home and office furniture', 0, 1, '/icons/furniture.png', '/images/categories/furniture.jpg'),
    ('Books', 'All kinds of books and magazines', 0, 1, '/icons/books.png', '/images/categories/books.jpg'),

    ('Mobile Phones', 'Smartphones and accessories', 1, 2, '/icons/phones.png', '/images/categories/phones.jpg'),
    ('Laptops', 'Laptops and notebooks', 1, 2, '/icons/laptops.png', '/images/categories/laptops.jpg'),

    ('Chairs', 'Office and dining chairs', 2, 2, '/icons/chairs.png', '/images/categories/chairs.jpg'),
    ('Tables', 'Desks, dining tables, etc.', 2, 2, '/icons/tables.png', '/images/categories/tables.jpg');
INSERT INTO `marketplace_listing`
(`user_id`, `category_id`, `title`, `description`, `price`, `price_type`, `condition`, `status`, `location`, `latitude`, `longitude`, `contact_methods`, `view_count`, `save_count`, `is_promoted`)
VALUES
(1, 6, 'Vintage Wooden Chair', 'Beautiful handcrafted wooden chair in great condition.', 75.00, 'FIXED', 'LIKE_NEW', 'ACTIVE', 'New York, USA', 40.712776, -74.005974, JSON_ARRAY('CHAT', 'PHONE'), 145, 10, 1),

(2, 5, 'Used Dell Laptop i7 16GB RAM', 'Used but works perfectly. Ideal for office work.', 550.00, 'FIXED', 'GOOD', 'ACTIVE', 'Los Angeles, USA', 34.052235, -118.243683, JSON_ARRAY('CHAT'), 230, 22, 0),

(3, 3, 'Collection of Mystery Novels', '10 books - great condition, selling as a bundle.', 40.00, 'FIXED', 'LIKE_NEW', 'ACTIVE', 'Boston, USA', 42.360081, -71.058884, JSON_ARRAY('CHAT', 'EMAIL'), 98, 5, 0);

INSERT INTO `marketplace_listing_image`
(`listing_id`, `image_url`, `thumbnail_url`, `sort_order`, `is_primary`)
VALUES
(1, '/images/listings/chair1.jpg', '/thumbs/chair1_thumb.jpg', 1, 1),
(1, '/images/listings/chair2.jpg', '/thumbs/chair2_thumb.jpg', 2, 0),

(2, '/images/listings/laptop1.jpg', '/thumbs/laptop1_thumb.jpg', 1, 1),

(3, '/images/listings/books1.jpg', '/thumbs/books1_thumb.jpg', 1, 1);

INSERT INTO `marketplace_favorite`
(`user_id`, `listing_id`)
VALUES
(3, 1),  -- Charlie liked Alice’s chair
(1, 2);  -- Alice liked Bob’s laptop

INSERT INTO `marketplace_message`
(`conversation_id`, `sender_id`, `content`, `is_read`)
VALUES
(1, 3, 'Hi, is the chair still available?', 0),
(1, 1, 'Yes, it is! Do you want to see it today?', 0),

(2, 1, 'Is the laptop still working fine?', 1),
(2, 2, 'Yes, perfectly fine. Battery recently replaced.', 1);

INSERT INTO `marketplace_review`
(`reviewer_id`, `reviewee_id`, `listing_id`, `rating`, `comment`)
VALUES
(3, 1, 1, 5, 'Great seller! The chair was exactly as described.'),
(1, 2, 2, 4, 'Laptop works well, delivery was quick.');
INSERT INTO `marketplace_search_history`
(`user_id`, `query`, `filters`, `category_id`, `location`, `results_count`)
VALUES
(1, 'laptop', JSON_OBJECT('price_min', 300, 'price_max', 800), 5, 'Los Angeles, USA', 5),
(3, 'wooden chair', JSON_OBJECT('condition', 'LIKE_NEW'), 6, 'New York, USA', 3);
INSERT INTO `marketplace_saved_search`
(`user_id`, `name`, `query`, `filters`, `category_id`)
VALUES
(1, 'Office Furniture Deals', 'chair', JSON_OBJECT('condition', 'LIKE_NEW'), 6),
(2, 'Used Laptops', 'laptop', JSON_OBJECT('price_max', 700), 5);
INSERT INTO `marketplace_report`
(`reporter_id`, `reported_listing_id`, `reason`, `description`)
VALUES
(3, 2, 'Suspicious Listing', 'Price seems too low for the description.'),
(1, 3, 'Spam', 'Repeated posting of the same books.');



