package org.linlinjava.announce.listing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two sub-contexts of this service share only {@code org.linlinjava.announce.search}: nothing under {@code goods}
 * imports {@code listing} and nothing under {@code listing} imports {@code goods}. Checked on the sources so the rule
 * holds without an ArchUnit dependency.
 */
class SubContextBoundariesTest {

    static final Path SOURCES = Path.of("src/main/java/org/linlinjava/announce");

    @Test
    void goodsAndListingNeverImportEachOther() throws IOException {
        assertThat(offenders("goods", "org.linlinjava.announce.listing")).isEmpty();
        assertThat(offenders("listing", "org.linlinjava.announce.goods")).isEmpty();
        assertThat(offenders("search", "org.linlinjava.announce.goods")).isEmpty();
        assertThat(offenders("search", "org.linlinjava.announce.listing")).isEmpty();
    }

    private static List<String> offenders(String root, String forbiddenPackage) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES.resolve(root))) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> read(p).lines().anyMatch(l -> l.startsWith("import " + forbiddenPackage + ".")))
                    .map(Path::toString).toList();
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
