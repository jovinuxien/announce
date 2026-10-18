"""tradera.com: the category page is server-rendered HTML with one `div[data-item-card-id]` per item (title, link,
image, price, attribute spans, 'Inkom … sedan' or, for auctions, 'Sluttid 23 okt 17:59'); the item page embeds the
React server payload where `itemDetails` holds startDate, endDate and itemType as escaped JSON.
"""
import re

from announce_crawler.items import new_item
from announce_crawler.parsing.text import amount, clean, digits, iso, relative_age, short_date

SOURCE = "TRADERA"
BASE = "https://www.tradera.com"
CATEGORY = "1001"            # Fordon > Bilar
ITEM_HREF = re.compile(r"^/item/(\d+)/(\d+)(?:/([^/?#]*))?")
YEAR = re.compile(r"^(19|20)\d{2}$")
MIL = re.compile(r"^([\d\s ]+)\s*mil$", re.IGNORECASE)
AUCTION_TYPES = {"auction", "auctionbin"}
FUELS = {"bensin", "diesel", "el", "hybrid", "hybrid bensin", "hybrid diesel", "laddhybrid", "etanol", "gas", "miljöbränsle/hybrid"}
GEARBOXES = {"automat", "manuell"}

ESCAPED = {
    "start": re.compile(r'startDate\\"\s*:\s*\\"([^"\\]+)'),
    "end": re.compile(r'endDate\\"\s*:\s*\\"([^"\\]+)'),
    "type": re.compile(r'itemType\\"\s*:\s*\\"([A-Za-z]+)'),
}


def list_url(page=1, category=CATEGORY):
    return f"{BASE}/category/{category}?paging={page}"


def sale_type(item_type):
    return "AUCTION" if (item_type or "").lower() in AUCTION_TYPES else "FIXED_PRICE"


def parse_card(card, now=None):
    item_id = clean(card.attrib.get("data-item-card-id"))
    item_type = clean(card.attrib.get("data-item-type"))
    link = card.xpath('.//a[@data-testid="item-card-image"]')
    href = link.attrib.get("href") if link else None
    title = clean(link.attrib.get("title")) if link else None
    if not title:
        title = clean(card.xpath('.//a[contains(@class, "textTruncateTwoLines")]/text()').get())
    image = card.xpath('.//picture/source/@srcset').get()
    if image:
        image = image.split(",")[0].split()[0]       # the 1x medium-fit rendition
    else:
        image = card.xpath('.//img/@src').get()
    attributes = {"item_type": item_type} if item_type else {}
    parts = [clean(t) for t in card.xpath('.//*[contains(@class, "__attribute")]/text()').getall()]
    parts = [p for p in parts if p]
    for p in parts:
        low = p.lower()
        if YEAR.match(p):
            attributes["model_year"] = p
        elif MIL.match(p):
            mil = digits(p)
            if mil is not None:
                attributes["km"] = mil * 10
        elif low in FUELS:
            attributes["fuel"] = p
        elif low in GEARBOXES:
            attributes["gearbox"] = p
    price = amount(card.xpath('.//*[@data-testid="price"]/text()').get())
    time_text = clean(" ".join(card.xpath('.//*[contains(@id, "-time")]//text()').getall()))
    published = None
    ends = None
    if time_text:
        if "inkom" in time_text.lower():
            published = relative_age(time_text, now)
        elif "sluttid" in time_text.lower():
            ends = short_date(time_text.split("Sluttid", 1)[-1], now, future=True)
    m = ITEM_HREF.match(href or "")
    return new_item(
        SOURCE,
        externalId=item_id or (m.group(2) if m else None),
        url=f"{BASE}{href.split('?', 1)[0]}" if href else (f"{BASE}/item/{item_id}" if item_id else None),
        title=title,
        brief=" · ".join(p for p in parts),
        imageUrl=image,
        price=price,
        currency="SEK",
        saleType=sale_type(item_type),
        endsAt=iso(ends),
        sourceCategory="Vehicles/Cars",
        attributes=attributes,
        addressRaw=None,
        publishedAt=iso(published),
    )


def parse_list(response, now=None):
    return [parse_card(card, now) for card in response.xpath('//div[@data-item-card-id]')]


def parse_detail(response, item):
    """Adds the exact start and end time from the server payload; leaves the item as it was when the markup changed."""
    body = response.text
    found = {k: rx.search(body) for k, rx in ESCAPED.items()}
    if found["end"]:
        item["endsAt"] = found["end"].group(1)
    if found["start"]:
        item["publishedAt"] = found["start"].group(1)
    if found["type"]:
        item["attributes"] = {**(item.get("attributes") or {}), "item_type": found["type"].group(1)}
        item["saleType"] = sale_type(found["type"].group(1))
    return item
