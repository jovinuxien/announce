"""Scrapy settings of the announce crawlers.

Politeness is not optional here: robots.txt is obeyed, one request at a time per site, a delay between requests,
autothrottle on top, and an honest user agent that names the bot. The feed is one JSON-lines file per spider run,
written as `<source>_<stamp>.jsonl.part` into the importer's directory and renamed to `.jsonl` by run.sh once the
spider has finished, so the importer never reads a half-written file.
"""
import os

BOT_NAME = "announce_crawler"

SPIDER_MODULES = ["announce_crawler.spiders"]
NEWSPIDER_MODULE = "announce_crawler.spiders"

# An honest, modern user agent. CRAWLER_CONTACT (an url or a mailbox) is appended when set so a site can reach us.
_contact = os.environ.get("CRAWLER_CONTACT", "")
USER_AGENT = "Mozilla/5.0 (compatible; AnnounceBot/0.1; +https://github.com/joviste/announce" + (f"; {_contact}" if _contact else "") + ")"

ROBOTSTXT_OBEY = True

# One request at a time per site, with a delay; autothrottle widens the delay when the site slows down.
CONCURRENT_REQUESTS = 2
CONCURRENT_REQUESTS_PER_DOMAIN = 1
DOWNLOAD_DELAY = 1.0
RANDOMIZE_DOWNLOAD_DELAY = True
AUTOTHROTTLE_ENABLED = True
AUTOTHROTTLE_START_DELAY = 1.0
AUTOTHROTTLE_MAX_DELAY = 30.0
AUTOTHROTTLE_TARGET_CONCURRENCY = 1.0
RETRY_TIMES = 2
DOWNLOAD_TIMEOUT = 30
COOKIES_ENABLED = False
TELNETCONSOLE_ENABLED = False

ITEM_PIPELINES = {
    "announce_crawler.pipelines.CrawledItemPipeline": 300,
}

# The importer's directory (announce.listing.import.dir); one file per run, renamed by run.sh when the spider is done.
IMPORT_DIR = os.environ.get("ANNOUNCE_LISTING_IMPORT_DIR", os.path.expanduser("~/announce_crawled_data"))
FEED_URI_PARAMS = "announce_crawler.feeds.uri_params"
FEEDS = {
    f"{IMPORT_DIR}/%(source)s_%(stamp)s.jsonl.part": {
        "format": "jsonlines",
        "encoding": "utf8",
        "store_empty": False,
        "overwrite": True,
    },
}
FEED_EXPORT_ENCODING = "utf-8"

TWISTED_REACTOR = "twisted.internet.asyncioreactor.AsyncioSelectorReactor"
LOG_LEVEL = "INFO"
