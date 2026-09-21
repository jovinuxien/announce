-- Creates only the application user and its grants for announce_reactive_db.
-- Use this when the database already exists but the user is missing.
-- Run as a MySQL administrator:  sudo mysql < db-reactive/sql/announce_user.sql
CREATE DATABASE IF NOT EXISTS `announce_reactive_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'announce'@'localhost' IDENTIFIED BY 'Calliste_1006';
CREATE USER IF NOT EXISTS 'announce'@'%'         IDENTIFIED BY 'Calliste_1006';
GRANT ALL PRIVILEGES ON `announce_reactive_db`.* TO 'announce'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON `announce_reactive_db`.* TO 'announce'@'%'         WITH GRANT OPTION;
FLUSH PRIVILEGES;
SELECT user, host, plugin FROM mysql.user WHERE user = 'announce';
