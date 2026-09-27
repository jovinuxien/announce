package org.linlinjava.announce.gatewayapi.storage;

import org.linlinjava.announce.db.dao.reactive.LitemallStorageMapper;
import org.linlinjava.announce.db.domain.LitemallStorage;
import org.linlinjava.announce.db.domain.LitemallStorageExample;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** {@link StorageRecords} over the reactive {@code announce_storage} mapper of db-reactive. */
@Component
public class R2dbcStorageRecords implements StorageRecords {

    private final LitemallStorageMapper storage;

    public R2dbcStorageRecords(LitemallStorageMapper storage) {
        this.storage = storage;
    }

    @Override
    public Mono<Void> save(StoredFile f) {
        LocalDateTime now = LocalDateTime.now();
        LitemallStorage row = new LitemallStorage();
        row.setKey(f.key());
        row.setName(f.name());
        row.setType(f.type());
        row.setSize((int) Math.min(Integer.MAX_VALUE, f.size()));
        row.setUrl(f.url());
        row.setAddTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
        return storage.insertSelective(row).then();
    }

    @Override
    public Mono<StoredFile> findByKey(String key) {
        LitemallStorageExample e = new LitemallStorageExample();
        e.or().andKeyEqualTo(key).andDeletedEqualTo(false);
        return storage.selectOneByExample(e).map(r -> new StoredFile(r.getKey(), r.getName(), r.getType(), r.getSize(), r.getUrl()));
    }
}
