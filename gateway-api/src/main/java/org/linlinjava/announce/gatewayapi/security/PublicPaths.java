package org.linlinjava.announce.gatewayapi.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * The single list of what the customer edge serves without a token ({@code doc/frontend-edges.md} §3). Everything
 * else needs a member. The SPA shell is every GET/HEAD that is not under an API prefix.
 */
public final class PublicPaths {

    private PublicPaths() {
    }

    /** Auth endpoints reachable logged out ({@code /auth/me} is not among them). */
    public static final String[] AUTH_ANY_METHOD = {"/auth/login", "/auth/register", "/auth/refresh", "/auth/logout"};

    /** Catalog reads, click-out, the search tier and the uploaded pictures. */
    public static final String[] PUBLIC_GET = {
            "/api/announces/**", "/api/categories/**", "/api/brands/**", "/out/**", "/api/search", "/api/suggest", "/api/storage/fetch/**"};

    /** Provider-signed webhooks; the order module verifies the signature. */
    public static final String[] WEBHOOK_POST = {"/api/payments/webhook/**", "/api/shipping/webhook/**"};

    public static final String[] ACTUATOR_GET = {"/actuator/health", "/actuator/health/**", "/actuator/info"};

    /** Prefixes that are never the SPA shell; the history fallback uses the same list. */
    public static final String[] API_PREFIXES = {"/api/**", "/auth/**", "/out/**", "/ws/**", "/actuator/**"};

    public static ServerWebExchangeMatcher matcher() {
        List<ServerWebExchangeMatcher> m = new ArrayList<>();
        for (String p : AUTH_ANY_METHOD) m.add(new PathPatternParserServerWebExchangeMatcher(p));
        for (String p : PUBLIC_GET) m.add(read(p));
        for (String p : ACTUATOR_GET) m.add(read(p));
        for (String p : WEBHOOK_POST) m.add(new PathPatternParserServerWebExchangeMatcher(p, HttpMethod.POST));
        m.add(spaShell());
        return new OrServerWebExchangeMatcher(m);
    }

    /** GET or HEAD of anything outside the API prefixes: index.html, assets and deep links. */
    public static ServerWebExchangeMatcher spaShell() {
        List<ServerWebExchangeMatcher> api = new ArrayList<>();
        for (String p : API_PREFIXES) api.add(new PathPatternParserServerWebExchangeMatcher(p));
        return new AndServerWebExchangeMatcher(read("/**"), new NegatedServerWebExchangeMatcher(new OrServerWebExchangeMatcher(api)));
    }

    private static ServerWebExchangeMatcher read(String pattern) {
        return new OrServerWebExchangeMatcher(
                new PathPatternParserServerWebExchangeMatcher(pattern, HttpMethod.GET),
                new PathPatternParserServerWebExchangeMatcher(pattern, HttpMethod.HEAD));
    }
}
