"""The item every spider yields: the importer's own shape, schema crawled-item/2 (doc/crawled-listings.md section 4).

Plain dicts with these keys; the pipeline stamps the schema and the crawl time and enforces the shared rules.
"""
SCHEMA = "crawled-item/2"

FIELDS = (
    "schema", "source", "externalId", "url", "title", "brief", "imageUrl", "price", "currency", "saleType", "endsAt",
    "sourceCategory", "attributes", "addressRaw", "publishedAt", "crawledAt",
)

BRIEF_LENGTH = 255
TITLE_LENGTH = 255


def new_item(source, **fields):
    """A v2 item with every key present (None when unknown) so the feed lines are uniform."""
    item = {k: None for k in FIELDS}
    item["schema"] = SCHEMA
    item["source"] = source
    item["attributes"] = {}
    item["saleType"] = "FIXED_PRICE"
    item.update(fields)
    return item
