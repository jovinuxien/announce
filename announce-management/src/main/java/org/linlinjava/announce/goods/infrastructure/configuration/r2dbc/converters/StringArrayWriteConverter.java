package org.linlinjava.announce.goods.infrastructure.configuration.r2dbc.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;

import java.util.Arrays;

public class StringArrayWriteConverter implements Converter<String[], String> {


    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convert(String[] source) {
        if (source == null || source.length == 0) {
            return "[]";
        }
        try {
            return mapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            return Arrays.toString(source);
        }
    }

}
