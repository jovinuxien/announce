package org.linlinjava.announce.db.r2dbc;


/** {@code String[]} stored as a JSON array (goods gallery, comment / feedback pictures, product specifications). */
public class JsonStringArrayR2dbcTypeHandlerAdapter extends JsonArrayR2dbcTypeHandlerAdapter<String[]> {
    public JsonStringArrayR2dbcTypeHandlerAdapter() {
        super(String[].class);
    }
}
