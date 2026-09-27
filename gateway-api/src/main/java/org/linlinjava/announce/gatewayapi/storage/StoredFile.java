package org.linlinjava.announce.gatewayapi.storage;

/** One row of {@code announce_storage}: the key is the file name on disk and the public path segment. */
public record StoredFile(String key, String name, String type, long size, String url) {
}
