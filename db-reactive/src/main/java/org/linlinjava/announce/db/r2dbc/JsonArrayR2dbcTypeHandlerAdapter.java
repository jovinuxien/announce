package org.linlinjava.announce.db.r2dbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.ReadableMetadata;
import io.r2dbc.spi.Statement;
import pro.chenggang.project.reactive.mybatis.support.r2dbc.executor.parameter.ParameterHandlerContext;
import pro.chenggang.project.reactive.mybatis.support.r2dbc.executor.type.R2dbcTypeHandlerAdapter;

/**
 * R2DBC counterpart of the JDBC {@code Json*ArrayTypeHandler}s: a Java array is stored as a JSON
 * array in a VARCHAR column ({@code gallery}, {@code pic_urls}, {@code specifications}, {@code role_ids}...).
 *
 * <p>reactive-mybatis-support cannot run JDBC type handlers. It resolves an adapter by the Java type
 * the mapper XML's {@code typeHandler} handles, so registering one adapter per array type is enough;
 * the {@code typeHandler=...} attributes in the XML stay as they are.
 */
abstract class JsonArrayR2dbcTypeHandlerAdapter<T> implements R2dbcTypeHandlerAdapter<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Class<T> arrayClass;

    JsonArrayR2dbcTypeHandlerAdapter(Class<T> arrayClass) {
        this.arrayClass = arrayClass;
    }

    @Override
    public Class<T> adaptClazz() {
        return arrayClass;
    }

    @Override
    public void setParameter(Statement statement, ParameterHandlerContext context, T parameter) {
        try {
            statement.bind(context.getIndex(), MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise " + arrayClass.getSimpleName() + " to JSON", e);
        }
    }

    @Override
    public T getResult(Readable readable, ReadableMetadata metadata, String columnName) {
        return parse(readable.get(columnName, String.class));
    }

    @Override
    public T getResult(Readable readable, ReadableMetadata metadata, int columnIndex) {
        return parse(readable.get(columnIndex, String.class));
    }

    private T parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, arrayClass);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Column does not hold a JSON " + arrayClass.getSimpleName() + ": " + json, e);
        }
    }
}
