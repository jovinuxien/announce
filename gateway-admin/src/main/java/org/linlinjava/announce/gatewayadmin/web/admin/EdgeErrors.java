package org.linlinjava.announce.gatewayadmin.web.admin;

/** Exceptions of the edge-hosted controllers, mapped by {@link AdminApiExceptionHandler}. */
public final class EdgeErrors {
    private EdgeErrors() {}

    public static class NotFound extends RuntimeException {
        public NotFound(String what, Object id) {
            super(what + " " + id + " does not exist");
        }
    }

    public static class Conflict extends RuntimeException {
        private final String code;

        public Conflict(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
