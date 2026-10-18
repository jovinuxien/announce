package org.linlinjava.announce.listing.infrastructure.liveness;

import io.netty.channel.ChannelOption;
import org.linlinjava.announce.listing.domain.LinkChecker;
import org.linlinjava.announce.listing.domain.LinkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Probes a url with HEAD (GET with {@code Range: bytes=0-0} when HEAD is refused with 405), following redirects, with
 * a bounded timeout and the crawler's honest user agent. Answers a {@link LinkStatus}, never an error.
 */
@Component
public class WebClientLinkChecker implements LinkChecker {

    private static final Logger log = LoggerFactory.getLogger(WebClientLinkChecker.class);

    private final WebClient client;
    private final Duration timeout;

    public WebClientLinkChecker(WebClient.Builder builder,
                                @Value("${announce.listing.liveness.timeout:PT10S}") Duration timeout,
                                @Value("${announce.listing.liveness.user-agent:Mozilla/5.0 (compatible; AnnounceBot/0.1; link check)}") String userAgent) {
        this.timeout = timeout;
        HttpClient http = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE));
        this.client = builder.clone()
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    @Override
    public Mono<LinkStatus> check(String url) {
        return status(HttpMethod.HEAD, url)
                .flatMap(code -> code == 405 ? status(HttpMethod.GET, url) : Mono.just(code))
                .map(LinkStatus::ofHttp)
                .timeout(timeout.multipliedBy(2))
                .onErrorResume(e -> {
                    log.debug("link check of {} failed: {}", url, e.toString());
                    return Mono.just(LinkStatus.UNREACHABLE);
                });
    }

    private Mono<Integer> status(HttpMethod method, String url) {
        WebClient.RequestHeadersSpec<?> request = client.method(method).uri(url);
        if (method == HttpMethod.GET) {
            request = request.header(HttpHeaders.RANGE, "bytes=0-0");
        }
        return request.exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value()));
    }
}
