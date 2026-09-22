-- Tables the cqrs-reactive framework keeps in each service's own MySQL database.
-- They are created automatically on start-up (cqrs.command.idempotency.create-table,
-- cqrs.readside.progress.create-table); this file documents the DDL for operators.

CREATE TABLE IF NOT EXISTS cqrs_command_log (
  command_id   VARCHAR(128) NOT NULL,
  subject      VARCHAR(512) NOT NULL,
  completed    TINYINT(1)   NOT NULL DEFAULT 0,
  result_json  MEDIUMTEXT   NULL,
  created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at TIMESTAMP(3) NULL,
  PRIMARY KEY (command_id)
);

CREATE TABLE IF NOT EXISTS cqrs_readside_progress (
  group_id     VARCHAR(190) NOT NULL,
  partition_no BIGINT       NOT NULL,
  event_id     VARCHAR(64)  NOT NULL,
  updated_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (group_id, partition_no)
);
