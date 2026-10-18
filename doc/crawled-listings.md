# Crawled listings: dba.dk and tradera.com as a second search source

Status: implemented (2026-09-26, worktree `crawled-listings`); crawler, quality grade, liveness check and inventory
added 2026-10-17 (worktree `crawler-inventory`, plan in `doc/crawler-inventory-plan.md`). Module `announce-management`,
package `org.linlinjava.announce.listing`; schema in `db-reactive`; spiders in `crawler/`; index fields in
`docker/cqrs/application.indexer-service.yml`.

## 1. What a crawled listing is, and is not

Listings scraped from other classifieds sites are **not announces**. Nobody here owns them, they never enter the
event store or the `announce*` read models, and no command of the `Announce` aggregate knows about them. They are an
aggregator feed (the Trovit / Indeed model): the site keeps a **snippet and a link**, indexes it next to the local
announces so a search shows both, and a click on a hit leaves for the source page.

Legal stance, agreed 2026-09-25 and enforced in code:

- Only a snippet is stored and shown: `title`, `brief` truncated to 255 characters (`CrawledItem` truncates it,
  whatever the parser hands in), one image url, price, a few attributes. Never the full text.
- Images are hot-linked by url, never mirrored.
- The result links out through `GET /out/{id}` (302 to the source url); the frontend shows `external_url` only as a
  domain ("dba.dk"). Clicks are counted so the value of each source can be measured.
- A listing the source has taken down disappears from the index after a grace period and `/out/{id}` answers 410.

The two sub-contexts of the service share only `org.linlinjava.announce.search` (`SearchIndexer`); nothing under
`goods` imports `listing` or the other way round (`SubContextBoundariesTest` checks the sources).

## 2. Table `crawled_listing` (`db-reactive/sql/announce_table.sql`)

| column | meaning |
|---|---|
| `id` varchar(64) PK | `{source}-{external_id}` in lower case, e.g. `dba-1117479380`: the same listing always maps to the same row and the same index document |
| `source` | `DBA`, `TRADERA` (`ListingSource`; the facet value is the lower-case name) |
| `external_id` | the site's own id (dba: the `id-<digits>` url segment; tradera: `itemId`) |
| `url` varchar(1023) | canonical page url (query string and fragment stripped); the click-out target |
| `content_hash` char(64) | SHA-256 over title, brief, price, sale type, ends_at, attributes, image, address (`ContentHash`) |
| `kind` | `GOODS` today; `JOB` / `EVENT` when such sources are crawled |
| `title`, `brief` varchar(255) | the snippet |
| `image_url`, `price` decimal(10,2), `currency` char(3) | DKK for dba, SEK for tradera |
| `sale_type` | `FIXED_PRICE`, `AUCTION` (same names as goods, so the facet spans both) |
| `ends_at` | auction end (tradera) |
| `source_category` | the site's own path, e.g. `Cars/Private Car` |
| `category_id` | mapped `announce_category` of kind GOODS, through `listing_category_map`; null when unmapped |
| `attributes` JSON | name -> value strings: `km`, `model_year`, `dealer` for dba cars |
| `address_raw`, `postal_code`, `city`, `region_id` | `Address.parse` splits "6580 Vamdrup"; `region_id` is `announce_region` by city name, null when unknown (no geocoding) |
| `published_at` | publication time on the source, when the feed has it |
| `first_seen_at`, `last_seen_at` | crawler bookkeeping; `last_seen_at` drives the sweep |
| `status` | `ACTIVE`, `GONE` |
| `click_count` | incremented by `/out/{id}` |
| `quality_grade`, `quality_issues` JSON | `GOOD`, `WEAK`, `REJECT` and the issue codes behind it (section 4a); null on rows imported before grading existed, filled by the next reindex |
| `url_checked_at`, `url_ok`, `image_ok` | the liveness check (section 4b): when the row was last probed, whether the source url answered (0 = 404/410, the row is GONE), whether the image answered (0 = dead, the grade is downgraded); null = never probed or unreachable |
| `add_time`, `update_time` | |

Keys: unique (`source`, `external_id`); (`status`, `last_seen_at`) for the sweep; (`source`, `content_hash`);
(`status`, `url_checked_at`) for the liveness batch.

`listing_import_run` records one row per imported feed file (source, file, started/finished, the `ImportReport`
counts including the grades); the inventory shows the latest row per source.

`listing_category_map` (`source`, `source_category`, `category_id`) maps a site's category path to a local goods
category. It is data: insert rows, no code changes. Nothing is seeded; an unmapped listing is indexed without
`category_ids` and still shows up through the `source` and `kind` facets.

Mappers: `CrawledListingMapper` (generated, plus hand-written `upsert`, `touchSeen`, `updateStatus`,
`selectStaleIds`, `incrementClickCount`, `selectForLivenessCheck`, `updateLiveness`, `updateQuality`,
`selectInventoryCounts`), `ListingImportRunMapper` and `ListingCategoryMapMapper`. Regenerate with the `mbg` profile of
`db-reactive/pom.xml` and `mybatis-generator/reactify_mapper.py`; `CrawledListingMapperTest` covers the
hand-written statements against the live database.

## 3. Domain and lifecycle (`listing.domain`)

- `CrawledItem`: the normalised shape a parser produces (source, external id, url, kind, title, brief, image,
  price, currency, sale type, ends at, source category, attributes, raw address, published at). Its constructor
  enforces the rules every source shares: required fields, brief and title truncated to 255, attributes sorted.
- `CrawledListing`: the row as the service keeps it. `firstSeen` for a new listing, `seenAgain` when a later feed
  shows it (keeps `first_seen_at` and `click_count`, sets ACTIVE), `contentChanged` (hash compare), `isStale`,
  `gone`.
- Ports: `CrawledListingRepository`, `ListingCategoryMapping`, `RegionLookup`, `ListingFeedParser`.

Lifecycle: a listing is ACTIVE while the feeds keep showing it. `ListingSweepService` sets rows whose
`last_seen_at` is older than `announce.listing.gone-after` (default `P7D`) GONE and removes them from the index;
`ListingSweepJob` runs it on `announce.listing.sweep.cron` (default half past every hour). The liveness check
(section 4b) sets a row GONE at once when its source url answers 404 or 410. A GONE listing that appears in a later
feed goes back to ACTIVE and is indexed again, provided its grade (section 4a) passes.

## 4. Feed format the importer expects

Since 2026-10-17 the spiders live in the repository (`crawler/`, section 11) and emit the importer's own shape,
**schema `crawled-item/2`**: one flat JSON object per line, one JSON-lines file per spider run named
`<source>_<yyyyMMddHHmm>.jsonl` (`dba_202610172301.jsonl`, `tradera_202610172301.jsonl`) in
`announce.listing.import.dir`. `CrawledItemV2Parser` reads a line of any source; the per-source parsers hand a line
over to it when it carries the `schema` marker, so legacy and v2 files import through the same path.

| field | type | meaning |
|---|---|---|
| `schema` | `"crawled-item/2"` | marks the line as v2 |
| `source` | `DBA`, `TRADERA` | optional; must match the file's source when present |
| `externalId` | string | the site's own id (digits of `/mobility/item/{id}` on dba, `data-item-card-id` on tradera) |
| `url` | string | the item page; query string and fragment are stripped on import |
| `title`, `brief` | string | the snippet; the pipeline truncates `brief` to 255 characters, `CrawledItem` does it again |
| `imageUrl` | string or null | one image (dba: `images.dbastatic.dk`, tradera: the medium-fit rendition on `img.tradera.net`) |
| `price`, `currency` | number / `DKK`, `SEK` | null price for an auction without an opening bid |
| `saleType` | `FIXED_PRICE`, `AUCTION` | tradera `Auction` / `AuctionBin` are auctions, `ContactOnly` and `PureBin` fixed price |
| `endsAt` | ISO-8601 or null | auction end (tradera: `Sluttid` on the card, exact from the detail pass) |
| `sourceCategory` | string | `Cars/Private Car` (dba `registration_class=1`), `Vehicles/Cars` (tradera category 1001) |
| `attributes` | object | `brand`, `model`, `condition`, `model_year`, `km`, `fuel`, `gearbox`, `seller_type` (dba); `item_type`, `model_year`, `km` (mil x 10), `fuel`, `gearbox` (tradera) |
| `addressRaw` | string or null | dba: the city from the card, `8520 Lystrup` with the detail pass; tradera: none |
| `publishedAt` | ISO-8601 or null | from the relative age on the card (`2 t.`, `Inkom 3 h sedan`), so approximate; exact `startDate` from the tradera detail pass |
| `crawledAt` | ISO-8601 | when the spider produced the line (not stored) |
| `images` | list | dba detail pass only: the full image list (not stored; `imageUrl` is the first) |

The legacy shapes are still read, for the fixtures and the 2024 `dba_cars.json`: `JsonFeedReader` accepts an
indented JSON array as well as JSON lines, and every legacy field is what scrapy's `ItemLoader` emitted, a list
with the page's whitespace.

`DbaFeedParser` (legacy spider `dba_cars` of 2024):

| feed field | becomes |
|---|---|
| `itemUrl` | `url` (canonical) and `external_id` = the `id-<digits>` segment |
| `itemName`, `itemDescription`, `itemImage` | `title`, `brief`, `image_url` |
| `itemPris` | `price` (DKK) |
| `itemCategory` / `itemSubCategory` | `source_category` = `Cars/Private Car` |
| `itemKm`, `itemModelYear` | attributes `km`, `model_year` (digits only: "273.000" -> 273000) |
| `itemAddress` | first element -> `address_raw`, postal code and city; a second element (trade sellers) -> attribute `dealer` |
| `itemPublishedDate` | `published_at` (`yyyy-MM-dd HH:mm:ss.SSSSSS`) |

`TraderaFeedParser` (legacy spider `tradera_cars` of 2024, items from the page's `__NEXT_DATA__`): `itemId` ->
`external_id` and `url` = `https://www.tradera.com/item/{itemId}`; `description` -> `title` and `brief`; `itemType`
containing "auction" -> `sale_type` AUCTION with `ends_at` = `endDate`, else FIXED_PRICE; `startDate` ->
`published_at`; currency SEK; optional `title`, `itemUrl`, `price`, `imageUrl`, `category`, `address`.

### 4a. Quality grade (`listing.domain.ListingQuality`)

"Good candidate" is a grade computed at import from the item's own fields, and recomputed when the liveness check
changes the verdict on the image. The grade and the issue codes behind it are stored (`quality_grade`,
`quality_issues`) and shown by the inventory.

| grade | rule | issue codes |
|---|---|---|
| `REJECT` | a hard field is missing: url, title or image. Stored with its issues, never indexed | `no_url`, `no_title`, `no_image` (plus any soft issue) |
| `WEAK` | every hard field is there but at least one soft issue | `url_pattern` (not the source's canonical item url), `image_not_https`, `image_host_unknown` (not `images.dbastatic.dk`, `billeder.dba.dk`, `img.tradera.net`), `no_price` (fixed price without a price above zero; auctions are exempt), `no_published_at`, `no_brief`, `no_attributes`, `image_dead` (liveness) |
| `GOOD` | no issue at all | |

Only the grades in `announce.listing.index-grades` (default `GOOD,WEAK`) reach the index (`IndexGating`): the
importer indexes a new or changed row when its grade passes and removes the document of a row that was indexed and
no longer passes; the reindex pushes the passing rows only, grades rows that have no grade yet on the way (so one
reindex migrates rows imported before grading existed) and removes the rest. `ImportReport` counts `good`, `weak`
and `reject` next to `inserted` / `updated` / `unchanged` / `rejected` (`rejected` = could not be normalised at all)
and `indexed` = the documents pushed; `indexFailed` counts rows stored whose index call failed after two retries
(logged at ERROR; the next reindex pushes them). The first import after a cold start of the search tier hit the
client's default 1 s time limit once on 2026-10-17, hence the retries and `resilience4j.timelimiter.instances.ocs-indexer`
in the service's configuration.

### 4b. Liveness check (`ListingLivenessService`, `ListingLivenessJob`)

A bounded probe of the ACTIVE rows, least recently checked first (never-checked rows before all others):
`announce.listing.liveness.batch` rows per run (default 100), sorted by host so one site is hit at a time, with
`announce.listing.liveness.delay` between probes (default 1 s). The probe (`WebClientLinkChecker` behind the
`LinkChecker` port, HEAD with a GET `Range: bytes=0-0` fallback on 405, redirects followed,
`announce.listing.liveness.timeout` 10 s, the bot's user agent) answers OK (2xx/3xx), GONE (404/410), ERROR (any
other status) or UNREACHABLE.

| finding | effect |
|---|---|
| url GONE | row set GONE at once, `url_ok = 0`, document removed from the index, `/out/{id}` answers 410 |
| url OK | `url_ok = 1` |
| url ERROR / UNREACHABLE | nothing but `url_checked_at` (a blocked or flaky site never empties the index) |
| image OK | `image_ok = 1`; if it was 0 the grade is recomputed (`image_dead` gone) and the row reindexed |
| image GONE / ERROR | `image_ok = 0`, grade recomputed with `image_dead` (GOOD -> WEAK), row reindexed, or its document removed when the grade no longer passes |
| image UNREACHABLE | the old verdict is kept |

`ListingLivenessJob` runs it on `announce.listing.liveness.cron` (default every 15 minutes); disable with
`announce.listing.liveness.enabled=false`. `POST /api/admin/listings/import` of a changed row forgets the image
verdict when the image url changed (the upsert resets `image_ok`), so a new picture is probed afresh.

## 5. Import path (`listing.application`, `listing.infrastructure.integration`)

`ListingImportService` is transport-agnostic: `importItems(Flux<CrawledItem>)` is the core,
`importFile(source, path)` parses a file through the source's `ListingFeedParser` first. Per item, in feed order:

| state of the row | action | counted as |
|---|---|---|
| unknown id | insert, grade, index when the grade passes | `inserted` (+ `good` / `weak` / `reject`) |
| known, ACTIVE, same content hash | `touchSeen` (last_seen_at only) | `unchanged` |
| known, hash differs, or GONE | overwrite, ACTIVE, regrade, index when the grade passes (document removed when it no longer does) | `updated` (+ grade) |
| cannot be normalised | logged, skipped | `rejected` |

The `ImportReport` answers with these counts; `indexed = inserted + updated`. A run repeated on the same file
touches every row and indexes nothing.

Transport today is the directory, through a Spring Integration flow (`ListingImportFlow`): a
`FileReadingMessageSource` on `announce.listing.import.dir`, polled every `announce.listing.import.poll`
(default one minute), accept-once, regex `^(dba|tradera)_.*\.jsonl?$`, one file per poll. The handler picks the
source from the file prefix, runs the import, and a file outbound adapter moves the file to `done/` (or `failed/`
when the import threw, so a broken file is not retried every poll). Disable with
`announce.listing.import.enabled=false`. Kafka is the planned next transport (scrapy producer -> topic
`crawled-listings` -> `PublicEventSubscribers.forTopic` -> `importItems`); the service does not change for it.

Admin triggers (`ListingAdminController`):

```
POST /api/admin/listings/import    {"path": "/home/bimeni/announce_crawled_data/dba_202610172301.jsonl", "source": "DBA"}
POST /api/admin/listings/reindex   -> {"indexed": n}   # every indexable ACTIVE row again, batches of announce.listing.reindex.batch (500)
POST /api/admin/listings/liveness  -> {"checked", "gone", "imageDead", "imageBack", "reindexed"}   # one batch now
GET  /api/admin/listings/inventory -> {"sources": [{"source": "DBA", "domain": "dba.dk", "active", "gone",
                                        "grades": {"GOOD", "WEAK", "REJECT", "UNGRADED"}, "missingImage", "deadLink",
                                        "checked", "lastImport": {"file", "startedAt", "finishedAt", "seen", ... "good", "weak", "reject"}}, ...]}
```

`source` is optional when the file name carries the prefix. The admin import does not move the file. The inventory
is a read-model query only: `active` / `gone` per source, the ACTIVE rows per grade (`UNGRADED` = imported before
grading existed), `missingImage` = ACTIVE rows without an image url or with a dead image, `deadLink` = rows the
liveness check set GONE, `checked` = ACTIVE rows probed at least once, and the latest `listing_import_run`.

## 6. Index (`docker/cqrs/application.indexer-service.yml`)

Both sub-contexts push to the same `announce_index` through `SearchIndexer` (`OcsSearchIndexer` in mode `ocs`,
`RecordingSearchIndexer` in mode `noop`). A listing document (`ListingDocument`) uses the announce field names so
facets and sorts work across both origins:

| field | listing value |
|---|---|
| `announce_id` | the listing id (`dba-1117479380`) |
| `source` | `dba`, `tradera` (announces: `local`). Result + Facet + Filter, never a default scope |
| `external_url` | the source url, Result only (display the domain; click through `/out/{id}`) |
| `source_category` | the site's path, Result only |
| `kind`, `title`, `description` (= brief), `image_url`, `price`, `currency`, `sale_type`, `ends_epoch` | as for goods; `currency` is now a facet |
| `status` | `PUBLISHED` (only ACTIVE listings are indexed) so one status filter covers all hits |
| `category_ids`, `region_id`, `city`, `postal_code` | when mapped / parsed |
| `created_epoch` | `published_at`, else `first_seen_at` |
| `km`, `model_year`, `dealer`, ... | attributes, dynamic facets like goods attributes |

The dynamic-field negative lookahead lists `source`, `external_url` and `source_category` so they stay fixed fields.

## 7. Click-out

`GET /out/{id}`: 302 with `Location` = the source url and `click_count + 1` for an ACTIVE listing; 410 for a GONE
one; 404 for an unknown id. Problems are RFC 7807 (`ListingExceptionHandler`).

## 8. Configuration

```yaml
announce:
  search:
    mode: ocs            # noop records in memory (tests, local runs without the search tier)
    index: announce_index
  listing:
    gone-after: P7D
    import:
      enabled: true
      dir: ${ANNOUNCE_LISTING_IMPORT_DIR:/home/bimeni/announce_crawled_data}
      poll: PT1M
    sweep:
      enabled: true
      cron: "0 30 * * * *"
    reindex:
      batch: 500
    index-grades: GOOD,WEAK   # grades that reach the index; REJECT never does
    liveness:
      enabled: true
      cron: "0 */15 * * * *"
      batch: 100
      delay: PT1S
      timeout: PT10S
      user-agent: "Mozilla/5.0 (compatible; AnnounceBot/0.1; link check)"
```

## 9. Tests

`mvn -pl announce-management -P '!webapp' test`: `ListingImportEndToEndTest` (Testcontainers MySQL with the real
schema, EventSourcingDB for the co-hosted aggregate, `announce.search.mode=noop`, a fake `LinkChecker`) imports the
fixtures (three dba items, two tradera items) twice (five rows, three GOOD and two WEAK, five index calls, then
none), reads the inventory for them, changes a price (one reindex), sweeps a row with an old `last_seen_at` (GONE,
removed), checks `/out/{id}` (302 / 410 / 404), re-imports the GONE row (ACTIVE again), drives the admin endpoints,
drops files into a temporary directory for the polling flow (moved to `done/` and `failed/`), imports a v2 fixture
whose second item has no image (stored REJECT, one index call, never reindexed), runs the liveness check with a 410
url (GONE, removed, `/out` 410) and a dead then live image (GOOD -> WEAK -> GOOD, reindexed each time), and loads
the real `dba_cars.json` when it is present (four of its 54 rows grade REJECT: no image). Unit tests cover the
parsers (legacy and v2), the quality rules, the address normaliser, the content hash and the document.
`mvn -pl db-reactive test` covers the mappers, including the liveness, quality, inventory and import-run statements.
`pytest` in `crawler/` covers the spiders offline (section 11).

## 10. Out of scope, for later

Geocoding, image mirroring, jobs and events from crawled sources, nutch as a discovery step feeding start urls to
scrapy, the Kafka transport, frontend changes (the admin SPA still shows the 2026-09 listing stats; the inventory
endpoint is not wired to a screen yet).

## 11. Crawler runbook (`crawler/`)

The scrapy project (`announce_crawler`, scrapy 2.13 on Python 3.9) replaced the 2024 spiders on 2026-10-17: both
sites changed their markup (dba.dk moved to the Schibsted platform, tradera.com dropped `__NEXT_DATA__`), the old
`tutorial` project on the Desktop is dead and becomes a symlink to `crawler/` after merge.

Setup and run:

```
cd crawler
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt     # a Python with the bz2 module: the pyenv builds on this host lack it, anaconda's 3.9 works
.venv/bin/python -m pytest                                              # offline, over the fixtures in tests/fixtures
./run.sh                                                                # both spiders, 5 list pages each (240 dba + 400 tradera items)
MAX_PAGES=20 DETAIL=1 ./run.sh                                          # deeper, with one item page per listing
ANNOUNCE_LISTING_IMPORT_DIR=/srv/feeds ./run.sh                         # another importer directory
.venv/bin/python -m scrapy crawl dba_cars -a max_pages=2 -a registration_class=1,2 -a detail=1
.venv/bin/python -m scrapy crawl tradera_cars -a max_pages=3 -a max_items=100
```

What the spiders do:

- `dba_cars`: `/mobility/search/car?registration_class=N&page=M` (48 products a page, about 18,800 cars in class 1).
  The JSON-LD `CollectionPage` gives name, url, image, brand, model, price and condition; the HTML card next to it
  gives the attribute line (year, km, fuel, gearbox), the city, the seller type and the relative age. `-a detail=1`
  fetches each item page (1.5 s apart) for the postal code (`Placering`), missing specs, the first registration,
  the image list, and `Opdateret` as a fallback date. Only `registration_class=1` (`Cars/Private Car`) was verified;
  2 and 3 are mapped to the 2024 names "Yellow plate cars" and "Truck and Buses" without checking the site.
- `tradera_cars`: `/category/1001?paging=N` (80 cards a page). Cards: id and type in `data-item-card-id` /
  `data-item-type`, title and link on `a[data-testid=item-card-image]`, image from the `picture` source set, price in
  `[data-testid=price]`, attribute spans (year, mil, fuel, gearbox), `Inkom … sedan` (relative age) or `Sluttid 23 okt
  17:59` (auction end, current or next year). `-a detail=1` reads `startDate`, `endDate` and `itemType` from the escaped
  `itemDetails` of the React server payload; when the payload changes shape the card values stay.
- Politeness (`settings.py`): robots.txt obeyed (both sites allow the list and item pages, checked 2026-10-17), one
  request at a time per site, 1 s between requests plus autothrottle, two retries, user agent
  `Mozilla/5.0 (compatible; AnnounceBot/0.1; +https://github.com/joviste/announce)` with `CRAWLER_CONTACT` appended
  when that variable is set. Both sites served the bot user agent on 2026-10-17.
- Output: `<source>_<stamp>.jsonl.part` in the importer directory while the spider runs, renamed to `.jsonl` by
  `run.sh` once it exits, so the polling flow (regex `^(dba|tradera)_.*\.jsonl?$`) never imports a half-written file.
  A bare `scrapy crawl` leaves the `.part` file; rename it yourself.
- Caps: `-a max_pages` (default 5), `-a max_items` (default 0 = unlimited), `-a start_page`; no hard-coded item count
  any more.
- Tests (`tests/`): `scrapy.http.HtmlResponse` over trimmed copies of the live pages of 2026-10-17
  (`tests/fixtures/*.html`, 5 cards each plus the detail blocks) check every field, the paging and the caps, and the
  pipeline rules. When a site changes its markup, these fail before the feed silently empties; refresh the fixture from
  the live page and fix the parser in `announce_crawler/parsing/`.

Measured on 2026-10-17 (5 pages each): 239 dba items, 400 tradera items, no item dropped; dba items complete,
tradera auctions carry `Sluttid` instead of `Inkom` so they have no `publishedAt` (WEAK), tradera has no address.
