# Plan: worktree `crawler-inventory` — fresh crawled data and a listing inventory

Status: **approved 2026-10-17** as written (decisions 1-3, steps 1-10, open points resolved: new worktree
`crawler-inventory` on branch `feat/crawler-inventory` off `announce-jo-branch`; the Desktop scrapy folder
becomes a symlink to `crawler/` after merge). Proposed 2026-09-29 in the `crawled-listings` worktree after
merging `announce-jo-branch` (fbfc72f4) and rerunning the acceptance tests. The matching
`### Worktree: crawler-inventory` block is in `CLAUDE.md`; launch with
`FRESH=1 MODULES=crawler-inventory ./open-fix-worktrees.sh`. Nothing below is implemented yet.

## 0. State of the `crawled-listings` worktree on 2026-09-29

- `announce-jo-branch` merged in (fast-forward to fbfc72f4). Working tree clean.
- `mvn -pl announce-management -P '!webapp' test`: 56 tests pass. `ListingImportEndToEndTest` passes all 8
  checks when `~/announce_crawled_data/dba_cars.json` is present (it lives in `done/` now; a temporary copy was
  used for the run and removed).
- `mvn -pl db-reactive test`: 69 of 70 pass. `CustomReactiveMappersTest.stockMovesUpAndDownAndRefusesToGoNegative`
  fails because the English seed has no `announce_goods_product` rows. Pre-existing on the integration branch,
  unrelated to listings; belongs to the reseed work (add product rows to the seed, or skip when the table is empty).
- Gotchas seen: after the merge, db-reactive needs `mvn clean` once (stale compiled `LitemallCartMapper.xml` in
  `target/`); `DbUtilTest` rewrites `db-reactive/test.sql` as a side effect (restore with `git checkout`).

## 1. Survey of the scrapy project and the source sites

Scrapy project: `~/Desktop/python_projects/scrapy_projects/tutorial`, **not a git repository**; venv
`tutorial_env` = Python 3.8.10 + scrapy 2.11.1; spiders `dba_cars`, `dba_detail`, `tradera_cars`; feed already
JSON lines `%(name)s_%(stamp)s.jsonl` into `~/announce_crawled_data`; `CLOSESPIDER_ITEMCOUNT = 20`; the user
agent list is ancient IE strings. Only real feed: `done/dba_cars.json`, 54 rows from 2024-10-08, 4 without image.

Both spiders are dead against today's markup:

| site | then (2024, what the spiders expect) | now (2026-09-29) |
|---|---|---|
| dba.dk list | `tr.dbaListing` rows, urls `.../id-<digits>/`, images `billeder.dba.dk` | JSON-LD `CollectionPage.mainEntity` ItemList of 48 `Product`s: name, url `https://www.dba.dk/mobility/item/{id}`, image `images.dbastatic.dk/dynamic/default/item/{id}/{uuid}`, brand, model, `offers.price` DKK, itemCondition. `/biler/biler?page=N` does NOT paginate; `/mobility/search/car?registration_class=1&page=N` does (48/page, ~18k cars). |
| dba.dk detail | `id-<digits>` page, label/value rows | JSON-LD `Product` with the full image list; text "Kilometertal 187.500 km"; year/address selectors still to find. |
| tradera.com list | `__NEXT_DATA__` with `discover.items[]` (itemId, shortDescription, itemType, startDate, endDate) | HTML cards `div[data-item-card-id][data-item-type=Auction|...]`; title `a[data-testid=item-card-image]/@title`; href `/item/{cat}/{id}/{slug}`; `img/@src` on `img.tradera.net/small-fit/...` (medium-fit, large-fit exist); `[data-testid=price]` "259 900 kr"; attribute spans (year, "14 237 mil", fuel); "Inkom 4 h sedan"; pagination `/category/1001?paging=N`. Auction end not on the card. |

robots.txt of both sites allows these pages. The Java importer accepts `^(dba|tradera)_.*\.jsonl?$` in the
import dir; `CrawledItem` (`listing.domain`) is the normalised shape a parser must produce.

## 2. Decisions to approve

1. **Scrapy project moves into the repo as `crawler/`** (the `tutorial` package, `scrapy.cfg`, a pinned
   `requirements.txt`; venv, eggs, build, dbs, logs, .idea excluded). Reason: a worktree cannot branch, diff or
   review a folder outside git. After merge, the Desktop path can become a symlink to the repo folder.
   Alternative (not recommended): keep it on the Desktop and symlink from the repo; other worktrees would not
   have it and the link would be brittle.
2. **Spiders emit the importer's own shape.** Each JSON line is a flat `CrawledItem` with
   `schema: "crawled-item/2"`: `externalId, url, title, brief, imageUrl, price, currency, saleType, endsAt,
   sourceCategory, attributes{}, addressRaw, publishedAt, crawledAt`. One generic `CrawledItemV2Parser` on the
   Java side; `DbaFeedParser` / `TraderaFeedParser` stay for the legacy fixtures.
3. **"Available and good candidate" = quality grade + liveness.** Quality is computed at import from the item;
   availability is `status` ACTIVE/GONE plus a bounded HEAD-check job. Only graded rows are indexed.

## 3. Steps

1. `crawler/` with the scrapy project, `run.sh` (both spiders into `announce.listing.import.dir`), a modern
   honest user agent, robots obeyed, item/page caps as spider arguments (`-a max_pages=`) instead of the
   hard-coded 20-item limit; polite delays and autothrottle.
2. `dba_cars`: iterate `/mobility/search/car?registration_class=1&page=N`, parse the JSON-LD product list;
   `external_id` = digits of `/mobility/item/{id}`; `source_category` "Cars/Private Car" (and the yellow-plate /
   truck classes as further `registration_class` values); optional `-a detail=1` pass (1.5 s delay, capped)
   adding km, model year, address and the full image list from the detail page.
3. `tradera_cars`: iterate `/category/1001?paging=N`, parse the cards; `sale_type` from `data-item-type`;
   `currency` SEK; attributes year / km (mil x 10) / fuel; `published_at` from the relative "Inkom … sedan";
   optional detail pass for `ends_at`; null fallback.
4. Offline spider tests (pytest, `scrapy.http.HtmlResponse` over saved HTML fixtures from today's pages) so a
   markup change fails a test instead of silently emptying the feed.
5. `listing.domain.ListingQuality`: grade GOOD / WEAK / REJECT + `issues` list. GOOD = title, https image on a
   known host, canonical url matching the source pattern, price > 0 or AUCTION, published date. WEAK = one soft
   field missing (published date, brief, attributes). REJECT = url, title or image missing.
6. Schema (`db-reactive/sql/announce_table.sql`, `build_announce_reactive_db.sh`, mappers regenerated):
   `quality_grade varchar(10)`, `quality_issues json`, `url_checked_at datetime`, `url_ok tinyint`,
   `image_ok tinyint` on `crawled_listing`; key on (`status`, `url_checked_at`).
7. `ListingLivenessJob` (`@Scheduled`, next to `ListingSweepJob`): HEAD the source url and image of the N
   oldest-checked ACTIVE rows per run (`announce.listing.liveness.batch`, default 100), one host at a time with a
   delay; 404/410 on the url -> GONE at once + index removal; dead image -> grade downgraded and reindexed.
   `WebClient` behind a `LinkChecker` port; fake in tests.
8. Index gating: `announce.listing.index-grades` (default GOOD, WEAK); REJECT rows stay in the table with their
   issues, never indexed. `ImportReport` gains counts per grade.
9. `GET /api/admin/listings/inventory`: per source ACTIVE, GONE, per grade, missing image, dead link, last import
   time and file. Read-model query only.
10. Docs: `doc/crawled-listings.md` gains the v2 feed shape, the quality rules, the liveness job and the crawler
    runbook; this file's status line updated; `CLAUDE.md` block.

## 4. Acceptance

- `mvn -pl db-reactive test` and `mvn -pl announce-management -P '!webapp' test` pass with
  `announce.search.mode=noop`; `pytest` in `crawler/` passes offline.
- A real run of both spiders writes `dba_<stamp>.jsonl` and `tradera_<stamp>.jsonl` that import with zero
  rejected rows and >= 90 % GOOD.
- A fixture item without image imports as REJECT with no index call; a stubbed 410 on a url marks the row GONE
  and removes it from the index; a stubbed dead image downgrades to WEAK and reindexes.
- The inventory endpoint returns the expected counts for a five-row fixture.

## 5. Out of scope

Image mirroring, Kafka transport, jobs/events from crawled sources, Nutch, frontend changes.

## 6. Open points

- tradera auction end time and dba address format need the detail pages (not fetched during the survey);
  planned best-effort with null fallback.
- Whether to run this in the existing `crawled-listings` worktree or a new `crawler-inventory` one (plan
  assumes new, branch `feat/crawler-inventory` off `announce-jo-branch`).
- Whether the Desktop scrapy folder becomes a symlink after merge.
