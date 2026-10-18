"""dba.dk (Schibsted platform since 2025): list pages carry a JSON-LD CollectionPage with 48 Products and an HTML
card per product with the attribute line, the city, the seller type and the age; the item page carries a JSON-LD
Product with the image list and label/value rows (Modelår, Kilometertal, Placering).

Everything here is a pure function of the response so tests can run it over saved fixtures.
"""
import json
import re

from announce_crawler.items import new_item
from announce_crawler.parsing.text import amount, clean, digits, iso, relative_age, short_date

SOURCE = "DBA"
BASE = "https://www.dba.dk"
ITEM_ID = re.compile(r"/mobility/item/(\d+)")
IMAGE_HOST = "https://images.dbastatic.dk/"

# registration_class of /mobility/search/car -> the site's category path we store. Only 1 was verified in 2026-10.
REGISTRATION_CLASSES = {
    "1": "Cars/Private Car",
    "2": "Cars/Yellow plate cars",
    "3": "Cars/Truck and Buses",
}


def list_url(registration_class="1", page=1):
    return f"{BASE}/mobility/search/car?registration_class={registration_class}&page={page}"


def category_of(registration_class):
    return REGISTRATION_CLASSES.get(str(registration_class), f"Cars/Class {registration_class}")


def json_ld_products(response):
    """The Products of the CollectionPage JSON-LD, in page order; [] when the block is missing (markup change)."""
    for text in response.xpath('//script[@type="application/ld+json"]/text()').getall():
        try:
            data = json.loads(text)
        except ValueError:
            continue
        if data.get("@type") != "CollectionPage":
            continue
        elements = (data.get("mainEntity") or {}).get("itemListElement") or []
        return [e.get("item") for e in elements if (e.get("item") or {}).get("url")]
    return []


def cards_by_id(response):
    """The HTML cards keyed by item id (the card's link carries the id)."""
    cards = {}
    for article in response.xpath('//article[contains(@class, "sf-search-ad")]'):
        link_id = clean(article.xpath('.//a[contains(@class, "sf-search-ad-link")]/@id').get())
        href = article.xpath('.//a[contains(@class, "sf-search-ad-link")]/@href').get()
        item_id = link_id or (ITEM_ID.search(href or "") and ITEM_ID.search(href).group(1))
        if item_id:
            cards[item_id] = article
    return cards


ATTRIBUTE_SPLIT = re.compile(r"\s*[∙·•|]\s*")
YEAR = re.compile(r"^(19|20)\d{2}$")


def card_fields(article, now=None):
    """What the HTML card adds to the JSON-LD product: attribute line, city, seller type, relative age."""
    out = {"attributes": {}, "brief_parts": []}
    variant = clean(article.xpath('.//div[contains(@class, "text-caption")]/text()').get())
    line = clean(article.xpath('.//span[contains(@class, "text-caption") and contains(@class, "font-bold")]/text()').get())
    if variant:
        out["brief_parts"].append(variant)
    if line:
        out["brief_parts"].append(line)
        for part in ATTRIBUTE_SPLIT.split(line):
            part = part.strip()
            if YEAR.match(part):
                out["attributes"]["model_year"] = part
            elif part.lower().endswith("km"):
                out["attributes"]["km"] = digits(part)
            elif part.lower() in ("diesel", "benzin", "el", "hybrid", "plug-in hybrid", "brint", "gas"):
                out["attributes"]["fuel"] = part
            elif part.lower() in ("manuelt", "automatisk", "automatgear", "manuel"):
                out["attributes"]["gearbox"] = part
    footer = [clean(t) for t in article.xpath('.//span[contains(@class, "truncate")]/text()').getall()]
    footer = [t for t in footer if t]
    if footer:
        out["city"] = footer[0]
    if len(footer) > 1:
        seller = footer[1].split("∙")[0].strip()
        out["attributes"]["seller_type"] = seller
    age = clean(article.xpath('.//span[contains(@class, "self-end")]/text()').get())
    out["published_at"] = relative_age(age, now) if age else None
    return out


def parse_list(response, registration_class="1", now=None):
    """The v2 items of one list page (JSON-LD product merged with its HTML card)."""
    products = json_ld_products(response)
    cards = cards_by_id(response)
    items = []
    for p in products:
        m = ITEM_ID.search(p.get("url", ""))
        if not m:
            continue
        item_id = m.group(1)
        offers = p.get("offers") or {}
        brand = (p.get("brand") or {}).get("name")
        model = p.get("model") if isinstance(p.get("model"), str) else (p.get("model") or {}).get("name")
        image = p.get("image")
        if isinstance(image, list):
            image = image[0] if image else None
        if isinstance(image, dict):
            image = image.get("contentUrl") or image.get("url")
        attributes = {}
        if brand:
            attributes["brand"] = brand
        if model:
            attributes["model"] = model
        condition = p.get("itemCondition") or offers.get("itemCondition")
        if condition:
            attributes["condition"] = condition.rsplit("/", 1)[-1].replace("Condition", "").lower()
        brief_parts = [p["description"]] if p.get("description") else []
        card = cards.get(item_id)
        extra = card_fields(card, now) if card is not None else {"attributes": {}, "brief_parts": []}
        attributes.update(extra["attributes"])
        for part in extra["brief_parts"]:
            if part not in brief_parts:
                brief_parts.append(part)
        items.append(new_item(
            SOURCE,
            externalId=item_id,
            url=f"{BASE}/mobility/item/{item_id}",
            title=clean(p.get("name")),
            brief=" · ".join(brief_parts),
            imageUrl=image,
            price=amount(str(offers.get("price"))) if offers.get("price") not in (None, "") else None,
            currency=offers.get("priceCurrency") or "DKK",
            saleType="FIXED_PRICE",
            sourceCategory=category_of(registration_class),
            attributes=attributes,
            addressRaw=extra.get("city"),
            publishedAt=iso(extra.get("published_at")),
        ))
    return items


def spec_rows(response):
    """The label/value rows of the item page ('Modelår' -> '2015', 'Kilometertal' -> '223.000 km'): a label element
    with the s-text-subtle class (span, dt or p) followed by the value element (p or dd)."""
    rows = {}
    for label in response.xpath('//*[(self::span or self::dt or self::p) and contains(@class, "s-text-subtle")]'):
        key = clean(label.xpath("text()").get())
        value = clean(label.xpath("following-sibling::*[self::p or self::dd][1]/text()").get())
        if key and value and key not in rows:
            rows[key] = value
    return rows


def parse_detail(response, item, now=None):
    """Enriches a list item with the detail page: postal code + city, km / model year when the card lacked them,
    first registration, the full image list, and the site's update time as a fallback published date."""
    rows = spec_rows(response)
    attributes = dict(item.get("attributes") or {})
    if "model_year" not in attributes and rows.get("Modelår"):
        attributes["model_year"] = rows["Modelår"]
    if "km" not in attributes and rows.get("Kilometertal"):
        attributes["km"] = digits(rows["Kilometertal"])
    if rows.get("Geartype") and "gearbox" not in attributes:
        attributes["gearbox"] = rows["Geartype"]
    if rows.get("Drivmiddel") and "fuel" not in attributes:
        attributes["fuel"] = rows["Drivmiddel"]
    if rows.get("1. registrering"):
        attributes["first_registration"] = rows["1. registrering"]
    place = clean(response.xpath('//h2[normalize-space(text())="Placering"]/following::span[1]//text()').get())
    if place:
        item["addressRaw"] = place
    images = []
    for text in response.xpath('//script[@type="application/ld+json"]/text()').getall():
        try:
            data = json.loads(text)
        except ValueError:
            continue
        if data.get("@type") == "Product":
            for img in data.get("image") or []:
                url = img.get("contentUrl") if isinstance(img, dict) else img
                if url:
                    images.append(url)
    if images:
        item["images"] = images
        if not item.get("imageUrl"):
            item["imageUrl"] = images[0]
    if not item.get("publishedAt") and rows.get("Opdateret"):
        item["publishedAt"] = iso(short_date(rows["Opdateret"], now))
    item["attributes"] = attributes
    return item
