package org.linlinjava.announce.goods.infrastructure.configuration.r2dbc.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;

import java.io.IOException;
import java.util.Arrays;

public class StringArrayReadConverter implements Converter<String, String[]> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String[] convert(String source) {
        if(source == null || source.trim().isEmpty() || "[]".equals(source)){
            return new String[0];
        }
        try {
            return mapper.readValue(source, String[].class);
        }catch (IOException e) {
            return Arrays.stream(source.replace("[", "").replace("]", "").split(","))
                    .map(String::trim).toArray(String[]::new);
        }
    }
}
