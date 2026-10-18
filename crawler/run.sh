#!/usr/bin/env bash
# Runs both spiders and hands their feeds to the importer directory (announce.listing.import.dir).
#
#   ./run.sh                      # 5 list pages per spider (240 dba cars, 400 tradera cars)
#   MAX_PAGES=20 DETAIL=1 ./run.sh
#   ANNOUNCE_LISTING_IMPORT_DIR=/srv/feeds ./run.sh
#
# Each spider writes <source>_<stamp>.jsonl.part; the file is renamed to .jsonl here, once the spider has exited,
# so the importer (regex ^(dba|tradera)_.*\.jsonl?$) never reads a half-written feed.
set -euo pipefail
cd "$(dirname "$0")"
export ANNOUNCE_LISTING_IMPORT_DIR="${ANNOUNCE_LISTING_IMPORT_DIR:-$HOME/announce_crawled_data}"
MAX_PAGES="${MAX_PAGES:-5}"
MAX_ITEMS="${MAX_ITEMS:-0}"
DETAIL="${DETAIL:-0}"
PYTHON="${PYTHON:-.venv/bin/python}"
if [ ! -x "$PYTHON" ]; then
  echo "no venv: python3.12 -m venv .venv && .venv/bin/pip install -r requirements.txt" >&2
  exit 1
fi
mkdir -p "$ANNOUNCE_LISTING_IMPORT_DIR"
for spider in dba_cars tradera_cars; do
  "$PYTHON" -m scrapy crawl "$spider" -a max_pages="$MAX_PAGES" -a max_items="$MAX_ITEMS" -a detail="$DETAIL" "$@"
done
shopt -s nullglob
for part in "$ANNOUNCE_LISTING_IMPORT_DIR"/*.jsonl.part; do
  mv "$part" "${part%.part}"
  echo "feed: ${part%.part} ($(wc -l < "${part%.part}") items)"
done
