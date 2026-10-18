"""The shared rules of the v2 feed, and the text helpers."""
from datetime import datetime, timezone

import pytest
from scrapy.exceptions import DropItem

from announce_crawler.items import new_item
from announce_crawler.parsing.text import amount, relative_age, short_date
from announce_crawler.pipelines import CrawledItemPipeline


class Spider:
    name = "dba_cars"
    source = "dba"


def test_pipeline_stamps_schema_and_truncates_the_snippet():
    item = new_item("DBA", externalId="1", url="https://www.dba.dk/mobility/item/1", title=" T ", brief="x" * 300,
                    attributes={"km": 1, "empty": "", "none": None})
    out = CrawledItemPipeline().process_item(item, Spider())
    assert out["schema"] == "crawled-item/2"
    assert out["title"] == "T"
    assert len(out["brief"]) == 255
    assert out["attributes"] == {"km": 1}
    assert out["crawledAt"].endswith("Z")
    assert set(out) >= {"externalId", "url", "title", "brief", "imageUrl", "price", "currency", "saleType", "endsAt",
                        "sourceCategory", "attributes", "addressRaw", "publishedAt", "crawledAt"}


def test_pipeline_drops_items_without_url_or_title():
    with pytest.raises(DropItem):
        CrawledItemPipeline().process_item(new_item("DBA", externalId="1", url=None, title="T"), Spider())
    with pytest.raises(DropItem):
        CrawledItemPipeline().process_item(new_item("DBA", externalId="1", url="https://x", title=""), Spider())


NOW = datetime(2026, 10, 17, 20, 0, tzinfo=timezone.utc)


def test_text_helpers():
    assert amount("46.000 kr.") == 46000 and amount("374 800 kr") == 374800 and amount("") is None
    assert relative_age("2 t.", NOW) == datetime(2026, 10, 17, 18, 0, tzinfo=timezone.utc)
    assert relative_age("3 dage", NOW) == datetime(2026, 10, 14, 20, 0, tzinfo=timezone.utc)
    assert relative_age("35 min.", NOW) == datetime(2026, 10, 17, 19, 25, tzinfo=timezone.utc)
    assert relative_age("Inkom 2 dagar sedan", NOW) == datetime(2026, 10, 15, 20, 0, tzinfo=timezone.utc)
    assert relative_age("I dag", NOW) == NOW
    assert relative_age("Nyhed", NOW) is None
    assert short_date("23 okt 17:59", NOW, future=True) == datetime(2026, 10, 23, 17, 59, tzinfo=timezone.utc)
    assert short_date("3 jan 10:00", NOW, future=True) == datetime(2027, 1, 3, 10, 0, tzinfo=timezone.utc)
    assert short_date("17. okt. 2026, 19.56", NOW) == datetime(2026, 10, 17, 19, 56, tzinfo=timezone.utc)
    assert short_date("nonsense", NOW) is None
