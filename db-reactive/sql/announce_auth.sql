-- Edge authentication tables (gateway-api / gateway-admin). Re-runnable on an existing database.
-- Design: doc/frontend-edges.md section 2. Refresh tokens are stored hashed (SHA-256, hex); the raw token only
-- ever travels in the httpOnly cookie scoped to /auth. The row id is a UUID chosen by the edge.

CREATE TABLE IF NOT EXISTS `announce_refresh_token` (
  `id` varchar(36) NOT NULL COMMENT 'token id (uuid), chosen by the edge',
  `user_type` varchar(16) NOT NULL COMMENT 'MEMBER (announce_user) or ADMIN (announce_admin)',
  `user_id` int NOT NULL COMMENT 'announce_user.id or announce_admin.id',
  `token_hash` char(64) NOT NULL COMMENT 'SHA-256 hex of the opaque token handed to the client',
  `expires_at` datetime NOT NULL COMMENT 'absolute expiry',
  `revoked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1 once rotated or logged out',
  `add_time` datetime DEFAULT NULL COMMENT 'issued at',
  `update_time` datetime DEFAULT NULL COMMENT 'last rotation / revocation',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_user` (`user_type`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh tokens issued by the gateway edges (gateway-api, gateway-admin)';
