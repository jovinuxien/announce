package org.linlinjava.announce.gatewayapi.auth;

import org.springframework.http.HttpStatus;

/** An auth failure that maps to a problem detail of the given status (401 credentials, 409 conflict, 403 disabled). */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static AuthException unauthorized(String message) {
        return new AuthException(HttpStatus.UNAUTHORIZED, message);
    }

    public static AuthException conflict(String message) {
        return new AuthException(HttpStatus.CONFLICT, message);
    }

    public static AuthException forbidden(String message) {
        return new AuthException(HttpStatus.FORBIDDEN, message);
    }
}
