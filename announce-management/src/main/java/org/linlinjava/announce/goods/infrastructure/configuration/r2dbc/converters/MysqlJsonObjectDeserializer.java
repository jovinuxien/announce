package org.linlinjava.announce.goods.infrastructure.configuration.r2dbc.converters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;


@Slf4j
public class MysqlJsonObjectDeserializer extends JsonDeserializer<String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode value = ctxt.readTree(p);
        log.info("read json value: {}", value);
        return value.toString();
    }
}
