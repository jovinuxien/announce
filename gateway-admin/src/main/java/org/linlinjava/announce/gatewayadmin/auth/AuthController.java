package org.linlinjava.announce.gatewayadmin.auth;

import org.linlinjava.announce.gatewayadmin.config.RefreshProperties;
import org.linlinjava.announce.gatewayadmin.jwt.AdminJwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Staff sign-in. The access token (RS256, 2 h) goes in the JSON body and lives in the SPA's memory; the refresh
 * token travels only in an httpOnly cookie scoped to {@code /auth} so it never reaches {@code /api} or the SPA's
 * scripts. Failures are RFC 7807 problems ({@link AuthExceptionHandler}).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminAccounts accounts;
    private final RefreshTokens refreshTokens;
    private final AdminJwtService jwt;
    private final RefreshProperties refresh;

    public AuthController(AdminAccounts accounts, RefreshTokens refreshTokens, AdminJwtService jwt, RefreshProperties refresh) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.refresh = refresh;
    }

    public record LoginRequest(String username, String password) {}

    public record AdminSummary(Integer id, String username, String avatar, List<Integer> roleIds, List<String> roles) {
        static AdminSummary of(AdminPrincipal p) {
            return new AdminSummary(p.id(), p.username(), p.avatar(), p.roleIds(), List.copyOf(p.authorities()));
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, AdminSummary admin) {}

    @PostMapping("/login")
    public Mono<ResponseEntity<TokenResponse>> login(@RequestBody LoginRequest body, ServerWebExchange exchange) {
        return accounts.authenticate(body.username(), body.password())
                .flatMap(principal -> accounts.recordLogin(principal.id(), clientIp(exchange))
                        .then(refreshTokens.issue(principal.id()))
                        .map(raw -> ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie(raw, refresh.ttl()).toString())
                                .body(tokens(principal))));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(@CookieValue(name = "${announce.refresh.cookie-name:announce_admin_refresh}", required = false) String cookie) {
        return refreshTokens.rotate(cookie)
                .flatMap(rotation -> accounts.load(rotation.adminId())
                        .onErrorMap(AuthenticationFailure.class, e -> new InvalidRefreshTokenException(e.getMessage()))
                        .map(principal -> ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie(rotation.nextToken(), refresh.ttl()).toString())
                                .body(tokens(principal))));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@CookieValue(name = "${announce.refresh.cookie-name:announce_admin_refresh}", required = false) String cookie) {
        return refreshTokens.revoke(cookie)
                .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                        .<Void>build());
    }

    /** Who am I, from the validated token (no database round trip). */
    @GetMapping("/me")
    public Mono<AdminSummary> me(Authentication authentication) {
        return Mono.just(AdminSummary.of((AdminPrincipal) authentication.getPrincipal()));
    }

    private TokenResponse tokens(AdminPrincipal principal) {
        return new TokenResponse(jwt.issue(principal), "Bearer", jwt.accessTtlSeconds(), AdminSummary.of(principal));
    }

    ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(refresh.cookieName(), value)
                .httpOnly(true).secure(refresh.cookieSecure()).path("/auth").sameSite("Strict").maxAge(maxAge).build();
    }

    private static String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() == null ? "" : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }
}
