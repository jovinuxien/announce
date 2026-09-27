package org.linlinjava.announce.gatewayapi.storage;

import reactor.core.publisher.Mono;

/** Port over {@code announce_storage}; the R2DBC implementation is {@link R2dbcStorageRecords}. */
public interface StorageRecords {

    Mono<Void> save(StoredFile file);

    Mono<StoredFile> findByKey(String key);
}
