"""Shared rules of the v2 feed: schema marker, crawl time, snippet length, required fields."""
from datetime import datetime, timezone

from scrapy.exceptions import DropItem

from announce_crawler.items import BRIEF_LENGTH, SCHEMA, TITLE_LENGTH


class CrawledItemPipeline:
    """Drops items that could never import (no url, external id or title), truncates the snippet (never the full text)
    and stamps `schema` and `crawledAt`. Deduplication is the importer's job (upsert by source + external id)."""

    def process_item(self, item, spider):
        for required in ("url", "externalId", "title"):
            if not item.get(required):
                raise DropItem(f"{required} missing: {item.get('url') or item.get('externalId')}")
        item["schema"] = SCHEMA
        item["source"] = item.get("source") or getattr(spider, "source", spider.name).upper()
        item["title"] = item["title"].strip()[:TITLE_LENGTH]
        item["brief"] = (item.get("brief") or "").strip()[:BRIEF_LENGTH]
        item["attributes"] = {k: v for k, v in (item.get("attributes") or {}).items() if v not in (None, "")}
        item["crawledAt"] = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        return item
