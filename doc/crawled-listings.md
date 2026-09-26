# Crawled listings: dba.dk and tradera.com as a second search source

Status: implemented (2026-09-26, worktree `crawled-listings`). Module `announce-management`, package
`org.linlinjava.announce.listing`; schema in `db-reactive`; index fields in `docker/cqrs/application.indexer-service.yml`.

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
| `add_time`, `update_time` | |

Keys: unique (`source`, `external_id`); (`status`, `last_seen_at`) for the sweep; (`source`, `content_hash`).

`listing_category_map` (`source`, `source_category`, `category_id`) maps a site's category path to a local goods
category. It is data: insert rows, no code changes. Nothing is seeded; an unmapped listing is indexed without
`category_ids` and still shows up through the `source` and `kind` facets.

Mappers: `CrawledListingMapper` (generated, plus hand-written `upsert`, `touchSeen`, `updateStatus`,
`selectStaleIds`, `incrementClickCount`) and `ListingCategoryMapMapper`. Regenerate with the `mbg` profile of
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
`ListingSweepJob` runs it on `announce.listing.sweep.cron` (default half past every hour). A GONE listing that
appears in a later feed goes back to ACTIVE and is indexed again.

## 4. Feed format the importer expects

The scrapy project (`~/Desktop/python_projects/scrapy_projects/tutorial`, outside this repository) writes one
**JSON-lines** file per run into `announce.listing.import.dir` (default `/home/bimeni/announce_crawled_data`),
named `<spider>_<yyyyMMddHHmm>.jsonl`: `dba_202410081230.jsonl`, `tradera_202410081230.jsonl`. `JsonFeedReader`
also accepts the older indented JSON array (`dba_cars.json`), so both layouts import. Every field is what scrapy's
`ItemLoader` emits: a list, usually of one element, with the page's whitespace (`"\r\n    6580 Vamdrup\r\n"`).

`DbaFeedParser` (spider `dba_cars`):

| feed field | becomes |
|---|---|
| `itemUrl` | `url` (canonical) and `external_id` = the `id-<digits>` segment |
| `itemName`, `itemDescription`, `itemImage` | `title`, `brief`, `image_url` |
| `itemPris` | `price` (DKK) |
| `itemCategory` / `itemSubCategory` | `source_category` = `Cars/Private Car` |
| `itemKm`, `itemModelYear` | attributes `km`, `model_year` (digits only: "273.000" -> 273000) |
| `itemAddress` | first element -> `address_raw`, postal code and city; a second element (trade sellers) -> attribute `dealer` |
| `itemPublishedDate` | `published_at` (`yyyy-MM-dd HH:mm:ss.SSSSSS`) |

`TraderaFeedParser` (spider `tradera_cars`, items from the page's `__NEXT_DATA__`): `itemId` -> `external_id` and
`url` = `https://www.tradera.com/item/{itemId}`; `description` -> `title` and `brief`; `itemType` containing
"auction" -> `sale_type` AUCTION with `ends_at` = `endDate`, else FIXED_PRICE; `startDate` -> `published_at`;
currency SEK. The current spider emits no title, price, image, category or address; the parser reads the optional
fields `title`, `itemUrl`, `price`, `imageUrl`, `category`, `address` when a later spider version adds them.

Scrapy fixes made on 2026-09-26 (backups `*.bak-2026-09-26` next to the files): `FEEDS` switched to `jsonlines`
with `%(name)s_%(stamp)s.jsonl` (`FEED_URI_PARAMS = tutorial.feeds.uri_params` supplies the stamp), the tradera
spider renamed from `cars` to `tradera_cars` so its file carries the source prefix, and the two dedup pipelines
removed (`DuplicatesPipeline` read JSON lines from a file the json feed wrote as an array and keyed on the
misspelt `itmeUrl`). The importer dedups.

## 5. Import path (`listing.application`, `listing.infrastructure.integration`)

`ListingImportService` is transport-agnostic: `importItems(Flux<CrawledItem>)` is the core,
`importFile(source, path)` parses a file through the source's `ListingFeedParser` first. Per item, in feed order:

| state of the row | action | counted as |
|---|---|---|
| unknown id | insert, index | `inserted` |
| known, ACTIVE, same content hash | `touchSeen` (last_seen_at only) | `unchanged` |
| known, hash differs, or GONE | overwrite, ACTIVE, index | `updated` |
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
POST /api/admin/listings/import   {"path": "/home/bimeni/announce_crawled_data/dba_cars.json", "source": "DBA"}
POST /api/admin/listings/reindex  -> {"indexed": n}   # every ACTIVE row again, batches of announce.listing.reindex.batch (500)
```

`source` is optional when the file name carries the prefix. The admin import does not move the file.

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
```

## 9. Tests

`mvn -pl announce-management -P '!webapp' test`: `ListingImportEndToEndTest` (Testcontainers MySQL with the real
schema, EventSourcingDB for the co-hosted aggregate, `announce.search.mode=noop`) imports the fixtures (three dba
items, two tradera items) twice (five rows and five index calls, then none), changes a price (one reindex), sweeps a
row with an old `last_seen_at` (GONE, removed), checks `/out/{id}` (302 / 410 / 404), re-imports the GONE row
(ACTIVE again), drives the admin endpoints, drops files into a temporary directory for the polling flow (moved to
`done/` and `failed/`), and loads the real `dba_cars.json` when it is present. Unit tests cover the parsers, the
address normaliser, the content hash and the document. `mvn -pl db-reactive test` covers the mappers.

## 10. Out of scope, for later

Geocoding, image mirroring, jobs and events from crawled sources, nutch as a discovery step feeding start urls to
scrapy, the Kafka transport, frontend changes.
