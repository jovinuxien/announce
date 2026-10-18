"""dba.dk parsers over the fixtures saved from the live site on 2026-10-17: a markup change fails here first."""
from announce_crawler.parsing import dba
from announce_crawler.spiders.dba_cars import DbaCarsSpider
from tests.conftest import response


def test_list_page_merges_json_ld_and_cards(now):
    items = dba.parse_list(response("dba_list.html", dba.list_url("1", 2)), "1", now)
    assert len(items) == 5
    peugeot = items[0]
    assert peugeot["schema"] == "crawled-item/2"
    assert peugeot["source"] == "DBA"
    assert peugeot["externalId"] == "25925994"
    assert peugeot["url"] == "https://www.dba.dk/mobility/item/25925994"
    assert peugeot["title"] == "Peugeot 308"
    assert peugeot["brief"] == "BlueHDi 120 Allure SW · 2015 ∙ 223.000 km ∙ Diesel ∙ Manuelt"
    assert peugeot["imageUrl"].startswith("https://images.dbastatic.dk/dynamic/default/item/25925994/")
    assert peugeot["price"] == 46000
    assert peugeot["currency"] == "DKK"
    assert peugeot["saleType"] == "FIXED_PRICE"
    assert peugeot["sourceCategory"] == "Cars/Private Car"
    assert peugeot["attributes"] == {
        "brand": "Peugeot", "model": "308", "condition": "used", "model_year": "2015", "km": 223000,
        "fuel": "Diesel", "gearbox": "Manuelt", "seller_type": "Privat",
    }
    assert peugeot["addressRaw"] == "Lystrup"
    assert peugeot["publishedAt"] == "2026-10-17T18:00:00Z"        # "2 t." before NOW
    ids = [i["externalId"] for i in items]
    assert len(set(ids)) == 5
    assert all(i["imageUrl"] and i["title"] and i["price"] for i in items)


def test_empty_page_yields_nothing_instead_of_garbage():
    from scrapy.http import HtmlResponse
    r = HtmlResponse(url=dba.list_url(), body=b"<html><body><p>Ingen biler</p></body></html>", encoding="utf-8")
    assert dba.parse_list(r) == []


def test_detail_page_adds_postal_code_specs_and_images(now):
    item = dba.parse_list(response("dba_list.html", dba.list_url("1", 2)), "1", now)[0]
    item["attributes"].pop("km")
    item = dba.parse_detail(response("dba_detail.html", item["url"]), item, now)
    assert item["addressRaw"] == "8520 Lystrup"
    assert item["attributes"]["km"] == 223000
    assert item["attributes"]["model_year"] == "2015"
    assert item["attributes"]["first_registration"] == "29.06.2015"
    assert item["images"] == ["https://images.dbastatic.dk/dynamic/default/item/25925994/a8d2394c-0e41-4e09-9a09-01209b517172"]
    assert item["publishedAt"] == "2026-10-17T18:00:00Z"            # the list card's age wins over 'Opdateret'


def test_detail_update_time_is_the_fallback_published_date(now):
    item = {"url": "https://www.dba.dk/mobility/item/25925994", "attributes": {}}
    item = dba.parse_detail(response("dba_detail.html", item["url"]), item, now)
    assert item["publishedAt"] == "2026-10-17T19:56:00Z"


def test_spider_follows_pages_up_to_max_pages_and_caps_items(now):
    spider = DbaCarsSpider(max_pages="2", max_items="3")
    out = list(spider.parse_list(response("dba_list.html", dba.list_url("1", 1)), registration_class="1", page=1))
    items = [o for o in out if isinstance(o, dict)]
    assert len(items) == 3 and all(not hasattr(o, "url") for o in items)
    spider = DbaCarsSpider(max_pages="2")
    out = list(spider.parse_list(response("dba_list.html", dba.list_url("1", 1)), registration_class="1", page=1))
    requests = [o for o in out if hasattr(o, "url")]
    assert [r.url for r in requests] == [dba.list_url("1", 2)]
    spider = DbaCarsSpider(max_pages="1")
    out = list(spider.parse_list(response("dba_list.html", dba.list_url("1", 1)), registration_class="1", page=1))
    assert not [o for o in out if hasattr(o, "url")]


def test_detail_mode_requests_each_item_page():
    spider = DbaCarsSpider(max_pages="1", detail="1")
    out = list(spider.parse_list(response("dba_list.html", dba.list_url("1", 1)), registration_class="1", page=1))
    assert len(out) == 5 and all(o.url.startswith("https://www.dba.dk/mobility/item/") for o in out)
    assert out[0].cb_kwargs["item"]["externalId"] == "25925994"
