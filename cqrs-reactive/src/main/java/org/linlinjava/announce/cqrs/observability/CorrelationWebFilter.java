package org.linlinjava.announce.cqrs.observability;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reads the correlation header from the request (or creates one), exposes it in the Reactor context so
 * {@link TracingMetaDataEnricher} attaches it to commands, and echoes it in the response.
 */
public class CorrelationWebFilter implements WebFilter {

    private final String headerName;

    public CorrelationWebFilter(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(headerName);
        String correlationId = incoming == null || incoming.isBlank() ? CorrelationContext.newCorrelationId() : incoming;
        exchange.getResponse().getHeaders().set(headerName, correlationId);
        return chain.filter(exchange).contextWrite(context -> CorrelationContext.with(context, correlationId));
    }

    /** Ordered before {@link ServerWebExchangeContextFilter} so every downstream filter sees the id. */
    public static int order() {
        return Integer.MIN_VALUE + 100;
    }
}
