"""tradera.com cars: /category/1001?paging=N, 80 cards per page.

Arguments: -a max_pages=5, -a max_items=0 (unlimited), -a detail=0|1 (visit each item page for the exact start and
end time; slower), -a start_page=1, -a category=1001.
"""
import scrapy

from announce_crawler.parsing import tradera


class TraderaCarsSpider(scrapy.Spider):
    name = "tradera_cars"
    source = "tradera"
    allowed_domains = ["www.tradera.com"]

    def __init__(self, max_pages="5", max_items="0", detail="0", start_page="1", category=tradera.CATEGORY, **kwargs):
        super().__init__(**kwargs)
        self.max_pages = int(max_pages)
        self.max_items = int(max_items)
        self.detail = str(detail) in ("1", "true", "yes")
        self.start_page = int(start_page)
        self.category = str(category)
        self.yielded = 0
        if self.detail:
            self.custom_settings = {**(self.custom_settings or {}), "DOWNLOAD_DELAY": 1.5}

    async def start(self):
        yield scrapy.Request(tradera.list_url(self.start_page, self.category), callback=self.parse_list,
                             cb_kwargs={"page": self.start_page})

    def parse_list(self, response, page):
        items = tradera.parse_list(response)
        self.logger.info("tradera page %s: %s cards", page, len(items))
        if not items:
            self.logger.warning("no item cards on %s: markup change or end of results", response.url)
            return
        for item in items:
            if self.max_items and self.yielded >= self.max_items:
                return
            self.yielded += 1
            if self.detail and item.get("url"):
                yield response.follow(item["url"], callback=self.parse_detail, cb_kwargs={"item": item})
            else:
                yield item
        if page - self.start_page + 1 < self.max_pages:
            yield scrapy.Request(tradera.list_url(page + 1, self.category), callback=self.parse_list,
                                 cb_kwargs={"page": page + 1})

    def parse_detail(self, response, item):
        yield tradera.parse_detail(response, item)
