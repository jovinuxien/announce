#!/usr/bin/env bash
# Regenerates announce_reactive_db.sql from announce_table.sql + announce_auction_chat.sql
# + announce_messaging.sql + announce_order.sql. Edit those source files, never the generated one.
set -euo pipefail
cd "$(dirname "$0")"
python3 - <<'PY'
import re
gen = open('announce_reactive_db.sql').read()
head = gen[:gen.index('-- 1. Base tables')]
head = head[:head.rindex('-- -------')]
base = open('announce_table.sql').read().replace('-- Host: 127.0.0.1    Database: litemall\n', '')
ext  = open('announce_auction_chat.sql').read()
msg  = open('announce_messaging.sql').read()
order = open('announce_order.sql').read()
out = (head
  + "-- ---------------------------------------------------------------------\n"
  + "-- 1. Base tables (generated from announce_table.sql; regenerate this file\n"
  + "--    with: sql/build_announce_reactive_db.sh)\n"
  + "-- ---------------------------------------------------------------------\n"
  + base
  + "\n-- ---------------------------------------------------------------------\n"
  + "-- 2. Auction extension (announce_auction_chat.sql)\n"
  + "-- ---------------------------------------------------------------------\n"
  + ext
  + "\n-- ---------------------------------------------------------------------\n"
  + "-- 3. Messaging context (announce_messaging.sql)\n"
  + "-- ---------------------------------------------------------------------\n"
  + msg
  + "\n-- ---------------------------------------------------------------------\n"
  + "-- 4. Order module read models (announce_order.sql)\n"
  + "-- ---------------------------------------------------------------------\n"
  + order)
open('announce_reactive_db.sql', 'w').write(out)
print("announce_reactive_db.sql regenerated")
PY
