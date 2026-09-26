package org.linlinjava.announce.gatewayapi.auth;

import jakarta.validation.Valid;
import org.linlinjava.announce.gatewayapi.auth.AuthDtos.LoginRequest;
import org.linlinjava.announce.gatewayapi.auth.AuthDtos.RegisterRequest;
import org.linlinjava.announce.gatewayapi.auth.AuthDtos.TokenResponse;
import org.linlinjava.announce.gatewayapi.auth.AuthDtos.UserResponse;
import org.linlinjava.announce.gatewayapi.security.IdentityForwardingFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Member identity at the edge: {@code /auth/login|register|refresh|logout|me}. The access token (RS256, 2 h) is
 * returned in the body and kept in memory by the SPA; the refresh token (30 days, single use, hashed in
 * {@code announce_refresh_token}) travels only as an httpOnly cookie on {@code /auth}.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserAccounts accounts;
    private final PasswordEncoder passwords;
    private final AccessTokens accessTokens;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;

    public AuthController(UserAccounts accounts, PasswordEncoder passwords, AccessTokens accessTokens,
                          RefreshTokenService refreshTokens, RefreshCookies cookies) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<TokenResponse>> login(@Valid @RequestBody LoginRequest body, ServerWebExchange exchange) {
        return accounts.findByLogin(body.username().trim())
                .switchIfEmpty(Mono.error(AuthException.unauthorized("bad credentials")))
                .flatMap(a -> matches(body.password(), a.passwordHash())
                        .flatMap(ok -> ok ? Mono.just(a) : Mono.error(AuthException.unauthorized("bad credentials"))))
                .flatMap(a -> a.enabled() ? Mono.just(a) : Mono.error(AuthException.forbidden("account disabled")))
                .flatMap(a -> accounts.recordLogin(a.id(), clientIp(exchange)).thenReturn(a))
                .flatMap(a -> session(a, HttpStatus.OK));
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<TokenResponse>> register(@Valid @RequestBody RegisterRequest body) {
        return Mono.fromCallable(() -> passwords.encode(body.password()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hash -> accounts.register(body.username().trim(), hash, body.nickname(), blankToNull(body.mobile())))
                .flatMap(a -> session(a, HttpStatus.CREATED));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(@CookieValue(name = "${announce.auth.cookie-name:announce_refresh}", required = false) String cookie) {
        return refreshTokens.rotate(cookie)
                .flatMap(issued -> accounts.findById(issued.userId())
                        .switchIfEmpty(Mono.error(AuthException.unauthorized("member no longer exists")))
                        .map(a -> ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookies.issue(issued.token()).toString())
                                .body(tokenResponse(a))));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@CookieValue(name = "${announce.auth.cookie-name:announce_refresh}", required = false) String cookie) {
        return refreshTokens.revoke(cookie)
                .then(Mono.fromSupplier(() -> ResponseEntity.noContent()
                        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                        .build()));
    }

    @GetMapping("/me")
    public Mono<UserResponse> me(@RequestHeader(IdentityForwardingFilter.HDR_USER_ID) int userId) {
        return accounts.findById(userId)
                .switchIfEmpty(Mono.error(AuthException.unauthorized("member no longer exists")))
                .map(UserResponse::of);
    }

    private Mono<ResponseEntity<TokenResponse>> session(Account a, HttpStatus status) {
        return refreshTokens.issue(a.id())
                .map(issued -> ResponseEntity.status(status)
                        .header(HttpHeaders.SET_COOKIE, cookies.issue(issued.token()).toString())
                        .body(tokenResponse(a)));
    }

    private TokenResponse tokenResponse(Account a) {
        AccessTokens.Issued access = accessTokens.issue(a);
        return new TokenResponse(access.token(), "Bearer", access.expiresInSeconds(), UserResponse.of(a));
    }

    /** BCrypt is deliberately slow; keep it off the event loop. */
    private Mono<Boolean> matches(String raw, String hash) {
        return Mono.fromCallable(() -> hash != null && !hash.isBlank() && passwords.matches(raw, hash))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static String clientIp(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(java.net.InetAddress::getHostAddress)
                .orElse("");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
