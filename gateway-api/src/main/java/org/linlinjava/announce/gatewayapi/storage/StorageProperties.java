package org.linlinjava.announce.gatewayapi.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/**
 * {@code announce.storage.*}: where uploaded pictures live on disk and how they are addressed. {@code public-base}
 * is prefixed to the served path ({@code /api/storage/fetch/{key}}); empty means a relative URL (same origin).
 */
@ConfigurationProperties(prefix = "announce.storage")
public record StorageProperties(Path dir, DataSize maxSize, String publicBase) {

    public StorageProperties {
        if (dir == null) dir = Path.of(System.getProperty("user.home"), "announce_storage");
        if (maxSize == null) maxSize = DataSize.ofMegabytes(10);
        if (publicBase == null) publicBase = "";
    }
}
