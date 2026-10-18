"""dba.dk cars: /mobility/search/car?registration_class=N&page=M, 48 products per page.

Arguments: -a max_pages=5 (per registration class), -a max_items=0 (unlimited), -a detail=0|1 (visit each item page
for postal code, image list and missing attributes; slower, 1.5 s between requests), -a registration_class=1
(comma-separated list allowed), -a start_page=1.
"""
import scrapy

from announce_crawler.parsing import dba


class DbaCarsSpider(scrapy.Spider):
    name = "dba_cars"
    source = "dba"
    allowed_domains = ["www.dba.dk"]
    PAGE_SIZE = 48

    def __init__(self, max_pages="5", max_items="0", detail="0", registration_class="1", start_page="1", **kwargs):
        super().__init__(**kwargs)
        self.max_pages = int(max_pages)
        self.max_items = int(max_items)
        self.detail = str(detail) in ("1", "true", "yes")
        self.classes = [c.strip() for c in str(registration_class).split(",") if c.strip()]
        self.start_page = int(start_page)
        self.yielded = 0
        if self.detail:
            self.custom_settings = {**(self.custom_settings or {}), "DOWNLOAD_DELAY": 1.5}

    async def start(self):
        for rc in self.classes:
            page = self.start_page
            yield scrapy.Request(dba.list_url(rc, page), callback=self.parse_list,
                                 cb_kwargs={"registration_class": rc, "page": page})

    def parse_list(self, response, registration_class, page):
        items = dba.parse_list(response, registration_class)
        self.logger.info("dba class %s page %s: %s products", registration_class, page, len(items))
        if not items:
            self.logger.warning("no JSON-LD products on %s: markup change or end of results", response.url)
            return
        for item in items:
            if self.max_items and self.yielded >= self.max_items:
                return
            self.yielded += 1
            if self.detail:
                yield response.follow(item["url"], callback=self.parse_detail, cb_kwargs={"item": item})
            else:
                yield item
        # a page short of PAGE_SIZE is still followed: the JSON-LD block sometimes lists a few products less than the cards
        if page - self.start_page + 1 < self.max_pages:
            yield scrapy.Request(dba.list_url(registration_class, page + 1), callback=self.parse_list,
                                 cb_kwargs={"registration_class": registration_class, "page": page + 1})

    def parse_detail(self, response, item):
        yield dba.parse_detail(response, item)
