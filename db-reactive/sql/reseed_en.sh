#!/usr/bin/env bash
# Replaces the litemall demo content of a live announce_reactive_db by the English seed
# (announce_reseed_en.sql) without touching members, administrators, tokens, crawled
# listings, orders or announces posted through the application.
#
# Steps: 1. mysqldump of every table the migration rewrites (into $BACKUP_DIR),
#        2. collect the ids of the litemall goods that will disappear,
#        3. run announce_reseed_en.sql,
#        4. delete those ids from the OCS index when the indexer is reachable,
#        5. remind to run the admin reindex so the sample announces reach the index.
#
#   DB_PASSWORD=... ./reseed_en.sh            # defaults: user announce, 127.0.0.1:3306
#   OCS_INDEXER=http://localhost:8535 ./reseed_en.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-announce}"
DB_NAME="${DB_NAME:-announce_reactive_db}"
DB_PASSWORD="${DB_PASSWORD:-${ANNOUNCE_DB_PASSWORD:-}}"
OCS_INDEXER="${OCS_INDEXER:-http://localhost:8535}"
OCS_INDEX="${OCS_INDEX:-announce_index}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/announce_backups}"

if [[ -z "$DB_PASSWORD" ]]; then
  read -r -s -p "MySQL password for $DB_USER: " DB_PASSWORD; echo
fi
export MYSQL_PWD="$DB_PASSWORD"
MYSQL=(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --default-character-set=utf8mb4 "$DB_NAME")

TABLES=(announce announce_goods_detail announce_goods_attribute announce_goods_product announce_goods_specification
        announce_job announce_event announce_category announce_brand announce_keyword announce_region announce_role
        announce_ad announce_topic announce_comment announce_issue announce_log announce_footprint announce_system
        listing_category_map crawled_listing)

mkdir -p "$BACKUP_DIR"
backup="$BACKUP_DIR/announce_reseed_$(date +%Y%m%d%H%M%S).sql"
echo "1. backing up ${#TABLES[@]} tables to $backup"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --default-character-set=utf8mb4 --single-transaction --no-tablespaces \
  "$DB_NAME" "${TABLES[@]}" > "$backup"

echo "2. collecting the litemall goods ids"
old_ids="$("${MYSQL[@]}" -N -e "SELECT id FROM announce WHERE id REGEXP '^[0-9]{7}\$' AND id NOT BETWEEN '2000001' AND '2000021'")"
echo "   $(echo "$old_ids" | grep -c . || true) rows will be removed"

echo "3. applying announce_reseed_en.sql"
"${MYSQL[@]}" < "$HERE/announce_reseed_en.sql"
"${MYSQL[@]}" -e "SELECT 'categories' AS what, COUNT(*) AS rows_ FROM announce_category UNION ALL SELECT 'regions', COUNT(*) FROM announce_region
  UNION ALL SELECT 'announces', COUNT(*) FROM announce UNION ALL SELECT 'brands', COUNT(*) FROM announce_brand"

echo "4. removing the old documents from the OCS index at $OCS_INDEXER"
if [[ -n "$old_ids" ]] && [[ "$(curl -s -m 5 -o /dev/null -w "%{http_code}" "$OCS_INDEXER/")" != "000" ]]; then
  echo "$old_ids" | xargs -n 100 | while read -r batch; do
    args=(); for id in $batch; do args+=(--data-urlencode "id=$id"); done
    curl -sf -m 30 -o /dev/null -X DELETE -G "$OCS_INDEXER/indexer-api/v1/update/$OCS_INDEX" "${args[@]}"
  done
  echo "   done"
else
  echo "   indexer not reachable (or nothing to remove): start docker/cqrs and delete the ids listed in $backup.ids, or rebuild the index"
  echo "$old_ids" > "$backup.ids"
fi

cat <<EOF
5. push the sample announces to the index with the admin trigger, once announce-management runs:
   POST /api/admin/announces/reindex through gateway-admin (port 8081, admin token) or directly on announce-management
   Backup kept at $backup
EOF
