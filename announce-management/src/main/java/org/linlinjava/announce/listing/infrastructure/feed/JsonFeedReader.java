package org.linlinjava.announce.listing.infrastructure.feed;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Streams the objects of a scrapy feed file one by one, whatever its layout: a JSON array (the {@code json} feed,
 * indented or not) or JSON lines (the {@code jsonlines} feed, one object per line). Reading happens on the
 * bounded-elastic scheduler; the whole file is never held in memory.
 */
public final class JsonFeedReader {

    private final ObjectMapper mapper;

    public JsonFeedReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Flux<JsonNode> read(Path file) {
        return Flux.using(() -> mapper.getFactory().createParser(Files.newBufferedReader(file)), this::objects, JsonFeedReader::close)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<JsonNode> objects(JsonParser parser) {
        return Flux.generate(sink -> {
            try {
                JsonToken t = parser.nextToken();
                while (t == JsonToken.START_ARRAY || t == JsonToken.END_ARRAY) {   // the array feed: skip the brackets
                    t = parser.nextToken();
                }
                if (t == null) {
                    sink.complete();
                } else if (t == JsonToken.START_OBJECT) {
                    sink.next(mapper.readTree(parser));
                } else {
                    sink.error(new IllegalArgumentException("unexpected token " + t + " at " + parser.currentLocation()));
                }
            } catch (IOException e) {
                sink.error(new UncheckedIOException(e));
            }
        });
    }

    private static void close(JsonParser parser) {
        try {
            parser.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
