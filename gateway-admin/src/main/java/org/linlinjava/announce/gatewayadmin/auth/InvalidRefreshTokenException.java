package org.linlinjava.announce.gatewayadmin.auth;

/** Missing, unknown, revoked or expired refresh token: 401 and the cookie is cleared. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
