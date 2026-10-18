"""tradera.com parsers over the fixtures saved from the live site on 2026-10-17."""
from announce_crawler.parsing import tradera
from announce_crawler.spiders.tradera_cars import TraderaCarsSpider
from tests.conftest import response


def test_cards_give_every_field(now):
    items = tradera.parse_list(response("tradera_list.html", tradera.list_url(2)), now)
    assert len(items) == 5
    santa_fe = items[0]
    assert santa_fe["source"] == "TRADERA"
    assert santa_fe["externalId"] == "756274596"
    assert santa_fe["url"] == "https://www.tradera.com/item/1001/756274596/hyundai-santa-fe-hev-awd-advc-7-sits-pano-hud-krell-moms"
    assert santa_fe["title"] == "Hyundai Santa Fe HEV AWD Advc 7-Sits Pano HUD KRELL MOMS"
    assert santa_fe["imageUrl"] == "https://img.tradera.net/medium-fit/407/656487407_8a60c54b-abc0-4d49-9541-dae8f8dfa820.jpg"
    assert santa_fe["price"] == 374800
    assert santa_fe["currency"] == "SEK"
    assert santa_fe["saleType"] == "FIXED_PRICE"
    assert santa_fe["attributes"] == {"item_type": "ContactOnly", "model_year": "2023", "km": 93470, "fuel": "Hybrid bensin", "gearbox": "Automat"}
    assert santa_fe["brief"] == "2023 · 9 347 mil · Hybrid bensin · Automat"
    assert santa_fe["publishedAt"] == "2026-10-17T17:00:00Z"        # "Inkom 3 h sedan"
    assert santa_fe["endsAt"] is None
    assert santa_fe["sourceCategory"] == "Vehicles/Cars"


def test_auction_card_has_sale_type_and_end_time(now):
    fiesta = tradera.parse_list(response("tradera_list.html", tradera.list_url(2)), now)[4]
    assert fiesta["saleType"] == "AUCTION"
    assert fiesta["attributes"]["item_type"] == "Auction"
    assert fiesta["price"] == 23000
    assert fiesta["endsAt"] == "2026-10-23T17:59:00Z"               # "Sluttid 23 okt 17:59"
    assert fiesta["publishedAt"] is None
    assert fiesta["attributes"]["km"] == 230


def test_detail_payload_gives_exact_times():
    item = {"url": "https://www.tradera.com/item/1001/756274596/x", "attributes": {}, "saleType": "FIXED_PRICE"}
    item = tradera.parse_detail(response("tradera_detail.html", item["url"]), item)
    assert item["endsAt"] == "2026-12-16T17:36:48.1170000Z"
    assert item["publishedAt"] == "2026-10-17T16:36:48.1170000Z"
    assert item["attributes"]["item_type"] == "ContactOnly"
    assert item["saleType"] == "FIXED_PRICE"


def test_detail_without_payload_leaves_the_item_alone():
    from scrapy.http import HtmlResponse
    r = HtmlResponse(url="https://www.tradera.com/item/1", body=b"<html></html>", encoding="utf-8")
    item = {"endsAt": "x", "publishedAt": "y", "attributes": {}, "saleType": "AUCTION"}
    assert tradera.parse_detail(r, item) == {"endsAt": "x", "publishedAt": "y", "attributes": {}, "saleType": "AUCTION"}


def test_spider_pages_and_caps():
    spider = TraderaCarsSpider(max_pages="3", max_items="2")
    out = list(spider.parse_list(response("tradera_list.html", tradera.list_url(1)), page=1))
    assert len([o for o in out if isinstance(o, dict)]) == 2 and not [o for o in out if hasattr(o, "url")]
    spider = TraderaCarsSpider(max_pages="3")
    out = list(spider.parse_list(response("tradera_list.html", tradera.list_url(1)), page=1))
    assert [o.url for o in out if hasattr(o, "url")] == [tradera.list_url(2)]
