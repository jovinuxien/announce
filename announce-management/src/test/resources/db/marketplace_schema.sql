drop database if exists marketplace;
drop user if exists 'marketplace'@'%';
-- 支持emoji：需要mysql数据库参数： character_set_server=utf8mb4
create database marketplace default character set utf8mb4 collate utf8mb4_unicode_ci;
use litemall;
-- create user 'litemall'@'%' identified by 'litemall123456';
create user 'marketplace'@'localhost' identified by 'Calliste_1006';
grant all privileges on marketplace.* to 'marketplace'@'localhost' with grant option;
flush privileges;