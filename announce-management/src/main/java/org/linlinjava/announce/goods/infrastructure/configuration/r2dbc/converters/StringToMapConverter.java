package org.linlinjava.announce.goods.infrastructure.configuration.r2dbc.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.Map;


@ReadingConverter
public class StringToMapConverter implements Converter<String, Map<String, Object>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> convert(String source) {
        try {
            return objectMapper.readValue(source, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON string to Map", e);
        }
    }
}
