package org.linlinjava.announce.gatewayadmin.security;

/**
 * What the admin edge serves without a token: the sign-in endpoints, the SPA shell and its assets, and the health
 * probe ({@code /ws/**} is a backend prefix: a handshake needs the staff token like {@code /api/**}). Everything else is denied unless a rule in {@link SecurityConfig} grants it. Deep links such as
 * {@code /announces/abc} are rewritten to {@code /index.html} by the SPA fallback filter before security runs.
 */
public final class PublicPaths {

    public static final String[] ALL = {
            "/auth/login", "/auth/refresh", "/auth/logout",
            "/", "/index.html", "/favicon.ico", "/manifest.webmanifest", "/robots.txt",
            "/static/**", "/assets/**", "/app/**", "/content/**", "/*.js", "/*.css", "/*.map", "/*.ico", "/*.png", "/*.svg", "/*.json",
            "/actuator/health", "/actuator/health/**"
    };

    /** Prefixes that belong to the edge or a backend and must never be rewritten to the SPA shell. */
    public static final String[] BACKEND_PREFIXES = {"/api", "/auth", "/actuator", "/management", "/ws"};

    private PublicPaths() {}

    public static boolean isBackend(String path) {
        for (String prefix : BACKEND_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
