# SQL scripts for announce_reactive_db

| File | Purpose |
|---|---|
| `announce_reactive_db.sql` | **Generated.** Full bootstrap: creates the database and the `announce` user, then every table. Run as a MySQL administrator: `mysql -uroot -p < announce_reactive_db.sql`. Drops the database first, so it is a reset, not an upgrade. |
| `announce_table.sql` | Source: the 29 base tables inherited from litemall, renamed `announce_*`. |
| `announce_auction_chat.sql` | Source: the 10 auction and chat tables plus columns added to goods, user and order. Re-runnable on an existing database. |
| `build_announce_reactive_db.sh` | Regenerates `announce_reactive_db.sql` from the two sources above. Edit the sources, never the generated file. |
| `announce_user.sql` | Recreates only the `announce` user and its grants. |
| `announce_data_en.sql`, `announce_data.sql` | Optional development seed data (English / Chinese): `mysql -uannounce -p announce_reactive_db < announce_data_en.sql`. |
| `litemall_schema.sql` | Legacy litemall database bootstrap. Not used by this project. |

Design notes for the auction and chat tables: `doc/auction-chat-schema.md`.
