package org.linlinjava.announce.cqrs.test;

import org.linlinjava.announce.cqrs.security.ServiceTokens;
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import java.time.Instant;

/**
 * Lets a service's suite run with {@code announce.service-token.mode=enforce}, as in production, by signing the
 * test's requests the way an edge does:
 * <pre>{@code
 * @SpringBootTest(webEnvironment = RANDOM_PORT, properties = {EdgeRequests.ENFORCE, EdgeRequests.SECRET_PROPERTY})
 * class MyServiceEndToEndTest {
 *     @TestConfiguration
 *     static class Config {
 *         @Bean WebTestClientBuilderCustomizer edge() { return EdgeRequests.signedWebTestClient(); }
 *     }
 *     @Autowired WebTestClient client;   // every request carries a fresh X-Service-Token
 * }
 * }</pre>
 * A request that must look like it bypassed the edges comes from a client built without the customizer
 * ({@code WebTestClient.bindToServer()}).
 */
public final class EdgeRequests {

    /** The secret of the suites; never a real one. */
    public static final String SECRET = "test-service-secret";
    /** {@code @SpringBootTest(properties = ...)} entries turning the check on with {@link #SECRET}. */
    public static final String ENFORCE = "announce.service-token.mode=enforce";
    public static final String SECRET_PROPERTY = "announce.service-token.secret=" + SECRET;
    /** The name the test's tokens are minted under. */
    public static final String SERVICE = "test-edge";

    private EdgeRequests() {
    }

    /** A fresh token, for a request built by hand (a WebSocket handshake). */
    public static String token() {
        return ServiceTokens.mint(SERVICE, SECRET, Instant.now().getEpochSecond());
    }

    /** A client filter that signs every request. */
    public static ExchangeFilterFunction signing() {
        return (request, next) -> next.exchange(ClientRequest.from(request)
                .headers(headers -> headers.set(ServiceTokens.HEADER, token())).build());
    }

    /** Makes the auto-configured {@code WebTestClient} of a {@code @SpringBootTest} sign every request. */
    public static WebTestClientBuilderCustomizer signedWebTestClient() {
        return builder -> builder.filter(signing());
    }
}
