# SQL scripts for announce_reactive_db

| File | Purpose |
|---|---|
| `announce_reactive_db.sql` | **Generated.** Full bootstrap: creates the database and the `announce` user, then every table. Run as a MySQL administrator: `mysql -uroot -p < announce_reactive_db.sql`. Drops the database first, so it is a reset, not an upgrade. |
| `announce_table.sql` | Source: the base tables inherited from litemall, renamed `announce_*`, with the announce read model: `announce` (shared columns of every kind) plus `announce_goods_detail`, `announce_job`, `announce_event` and the goods children keyed by `announce_id`. `announce_category.kind` separates the goods, job and event trees. |
| `announce_auction_chat.sql` | Source: the 10 auction and chat tables plus columns added to user and order. Re-runnable on an existing database. |
| `build_announce_reactive_db.sh` | Regenerates `announce_reactive_db.sql` from the two sources above. Edit the sources, never the generated file. |
| `announce_user.sql` | Recreates only the `announce` user and its grants. |
| `announce_data_en.sql`, `announce_data.sql` | Optional development seed data (English / Chinese): `mysql -uannounce -p announce_reactive_db < announce_data_en.sql`. The former `announce_goods` rows were migrated into `announce` + `announce_goods_detail` as kind GOODS (seller 1, fixed price, no location, no expiry); three JOB and three EVENT root categories were added. |
| `announce_migrate_goods.sql` | The one-off migration that produced the seed above from a database still holding `announce_goods` (kept for reference; the seed files already contain its result). |
| `litemall_schema.sql` | Legacy litemall database bootstrap. Not used by this project. |

Design notes for the auction and chat tables: `doc/auction-chat-schema.md`; for the announce read model: `doc/announce-management-context.md` section 8.
