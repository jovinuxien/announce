package org.linlinjava.announce.goods.infrastructure.configuration.r2dbc.converters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;


@Slf4j
public class MysqlJsonToMapDeserializer extends JsonDeserializer<Map<String, Object>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String jsonString = p.getValueAsString();
        log.debug("Deserializing JSON string: {}", jsonString);

        if (jsonString == null || jsonString.trim().isEmpty()) {
            return Map.of();
        }

        return objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
    }
}
