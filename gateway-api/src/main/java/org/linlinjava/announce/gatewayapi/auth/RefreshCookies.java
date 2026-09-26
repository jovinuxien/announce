package org.linlinjava.announce.gatewayapi.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Builds the httpOnly refresh cookie, scoped to {@code /auth} so no other route ever sees it. */
@Component
public class RefreshCookies {

    public static final String PATH = "/auth";

    private final AuthProperties props;

    public RefreshCookies(AuthProperties props) {
        this.props = props;
    }

    public String name() {
        return props.cookieName();
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(props.refreshTtl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(props.cookieName(), value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .path(PATH)
                .sameSite("Strict");
    }
}
