# Legacy blocking code (not compiled)

Reference material kept outside `src/` while the services are rebuilt in their bounded contexts
(announce-management, order, member, messaging, auction). These classes use the blocking MyBatis
mappers and PageHelper, which no longer exist in this module: every mapper is now reactive
(`org.linlinjava.announce.db.dao.reactive`, Mono/Flux, explicit `selectPageByExample*` paging).

* `service/` – the litemall service layer, one class per table. Port the business rules, not the code.
* `test/`    – the old JDBC-based tests for stock movements, statistics and mapper return types;
               their reactive equivalents live in `src/test/java`.

Delete a file here once its bounded-context replacement exists.
