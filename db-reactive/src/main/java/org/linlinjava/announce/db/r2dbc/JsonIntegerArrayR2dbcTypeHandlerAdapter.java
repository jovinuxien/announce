package org.linlinjava.announce.db.r2dbc;


/** {@code Integer[]} stored as a JSON array (admin role ids, topic goods ids). */
public class JsonIntegerArrayR2dbcTypeHandlerAdapter extends JsonArrayR2dbcTypeHandlerAdapter<Integer[]> {
    public JsonIntegerArrayR2dbcTypeHandlerAdapter() {
        super(Integer[].class);
    }
}
